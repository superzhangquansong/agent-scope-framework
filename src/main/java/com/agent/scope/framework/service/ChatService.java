package com.agent.scope.framework.service;

import com.agent.scope.framework.bo.event.AgentOtherEventBO;
import com.agent.scope.framework.bo.event.PermissionAskEventBO.ToolCallInfo;
import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.constant.FileConst;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.dto.ChatStreamDTO;
import com.agent.scope.framework.dto.PermissionConfirmDTO;
import com.agent.scope.framework.enums.AgentEventEnum;
import com.agent.scope.framework.enums.ImageTypeEnum;
import com.agent.scope.framework.enums.MediaTypeEnum;
import com.agent.scope.framework.exception.ErrorCode;
import com.agent.scope.framework.exception.PermissionException;
import com.agent.scope.framework.handler.AgentEventHandlerRegistry;
import com.agent.scope.framework.handler.EventContext;
import com.agent.scope.framework.hdl.ImageInfo;
import com.agent.scope.framework.vo.ToolResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.*;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.agent.scope.framework.constant.BusinessConst.*;


/**
 * 核心聊天服务 —— 完全基于 AgentScope HarnessAgent 的智能路由。
 * <p>
 * 本服务是整个系统的核心入口，<b>不包含任何硬编码的意图判断或命令解析逻辑</b>。
 * 所有用户消息统一交给 HarnessAgent 处理，由 LLM 通过 ReAct 循环智能决策：
 * <ol>
 *   <li>LLM 理解用户自然语言意图</li>
 *   <li>LLM 自主选择并调用注册在 Toolkit 中的业务工具（DeviceTool、ProductTool 等）</li>
 *   <li>工具执行后 LLM 观察结果，决定是否继续推理或返回最终回复</li>
 *   <li>Agent 事件流通过 SSE 实时推送到前端</li>
 * </ol>
 * </p>
 * <p>
 * <b>事件处理采用策略模式 + 注册表模式</b>：
 * 各 {@link AgentEvent} 子类由对应的 {@code AgentEventHandler} 策略实现处理，
 * 通过 {@link AgentEventHandlerRegistry} 按 Class O(1) 查找分发，
 * 彻底消除原 instanceof if-else 长链。
 * </p>
 * <p>
 * <b>对话全链路记录</b>：通过 {@link ChatRecordService} 异步落库以下信息，
 * 不阻塞 SSE 主流程：
 * <ul>
 *   <li>用户输入消息（streamEvents 开始时）</li>
 *   <li>LLM 思考过程摘要（ThinkingBlockEndEvent 时）</li>
 *   <li>工具调用全量信息（ToolResultEndEvent 时，含入参/出参/状态/耗时）</li>
 *   <li>LLM 最终回复（doOnComplete 时）</li>
 *   <li>单次模型调用记录（ModelCallEndEvent 时，含输出内容/Token/耗时/replyId）</li>
 *   <li>Token 消耗汇总（ModelCallEndEvent 累积，doOnComplete 时统一保存）</li>
 * </ul>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /**
     * Jackson ObjectMapper（线程安全，静态复用，避免每次创建）。
     * <p>用于 SSE 事件 JSON 序列化与多模态消息构建。</p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HarnessAgent harnessAgent;
    private final ChatRecordService chatRecordService;
    private final AgentScopeProperties agentScopeProperties;
    private final AgentEventHandlerRegistry agentEventHandlerRegistry;
    private final PendingConfirmationService pendingConfirmationService;
    private final RedisRateLimiterService redisRateLimiterService;

    /**
     * 本地快速通道执行器（1.5b 分类 → Java 调工具 → 1.5b 汇总）。
     * <p>单意图设备控制走本地 qwen2.5:1.5b，0 云端 token，~500ms 响应。
     * 未命中设备控制时返回 null，自动回退云端 HarnessAgent ReAct。</p>
     */
    private final LocalFastPathExecutor localFastPathExecutor;

    /**
     * AgentStateStore（可选注入，用于清除残留的 ASKING 状态）。
     * <p>当 PendingConfirmationService TTL 过期但框架 RedisAgentStateStore 仍保留
     * ASKING 状态时，通过此引用调用 delete(userId, sessionId) 清除残留状态。</p>
     */
    private final Optional<io.agentscope.core.state.AgentStateStore> agentStateStore;

    /**
     * StringRedisTemplate（可选注入，用于跨实例中断消息发布/订阅）。
     * <p>Redis 不可用时为空，此时中断仅在本实例生效。</p>
     */
    private final Optional<StringRedisTemplate> stringRedisTemplateOpt;

    /**
     * 技能自动沉淀服务（特性33，条件装配）。
     * <p>启用 scope.agentscope.advanced.skill-promotion-enabled=true 时装配，
     * Agent 完成复杂任务后在 doOnComplete 中异步沉淀可复用技能。</p>
     */
    private final Optional<SkillPromotionService> skillPromotionService;

    /**
     * 活跃会话注册表：sessionId → SseEmitter。
     * <p>用于支持运行时中断与活跃会话追踪。</p>
     */
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /**
     * 活跃订阅注册表：sessionId → Disposable。
     * <p>存储 Reactor 订阅引用，支持 /api/chat/interrupt 立即终止。</p>
     */
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();

    /**
     * 已中断会话标记集合。
     * <p>
     * 当 {@link #interruptSession} 被调用时将 sessionId 加入此集合，
     * {@code doOnComplete} 据此判断是否跳过 SSE 发送（emitter 已在中断时关闭）。
     * 框架优雅中断完成后仍会触发 {@code doOnComplete}，此时只需保存记录，不需要再操作已关闭的 emitter。
     * </p>
     */
    private final Set<String> interruptedSessions = ConcurrentHashMap.newKeySet();

    /** 框架优雅中断超时时间（秒），超时后强制 dispose 订阅 */
    private static final long INTERRUPT_GRACE_TIMEOUT_SECONDS = 30;

    /** Redis 发布/订阅频道：跨实例中断消息 */
    private static final String INTERRUPT_CHANNEL = "agent:interrupt";

    /** Redis 消息监听容器（@PostConstruct 中初始化，@PreDestroy 中销毁） */
    private RedisMessageListenerContainer redisListenerContainer;

    /**
     * 中断指定会话的 Agent 执行。
     * <p>
     * <b>多实例中断机制（P1-7）</b>：本方法先在本地执行中断（关闭 SSE + 标记 + 超时兜底），
     * 然后通过 Redis 发布/订阅将中断消息广播到 {@link #INTERRUPT_CHANNEL}。
     * 其他实例收到消息后调用 {@link #interruptSessionLocal} 执行本地中断，
     * 从而实现跨节点中断。activeSubscriptions / interruptedSessions 保持内存级
     * （Reactor Subscription 不可序列化，无法跨节点共享），仅中断信号通过 Redis 传播。
     * Redis 不可用时仅本实例中断生效。
     * </p>
     * <p>
     * <b>关键设计</b>：立即关闭 SSE 连接（让用户看到即时响应），但<b>不立即 dispose 订阅</b>，
     * 给 AgentScope 框架时间在 ReAct 循环检查点检测中断信号并优雅保存 AgentState。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return true=成功中断，false=会话不存在或已结束
     */
    public boolean interruptSession(String sessionId) {
        // 1. 本地中断（优雅中断 + 超时兜底，核心逻辑保持不变）
        boolean interrupted = interruptSessionLocal(sessionId);

        // 2. 通过 Redis 发布/订阅广播中断消息到其他实例（多实例部署时跨节点中断）
        stringRedisTemplateOpt.ifPresent(redis -> {
            try {
                redis.convertAndSend(INTERRUPT_CHANNEL, sessionId);
                log.info("[Chat] 已发布跨实例中断消息: sessionId={}, channel={}", sessionId, INTERRUPT_CHANNEL);
            } catch (Exception e) {
                log.warn("[Chat] 发布跨实例中断消息失败: sessionId={}, error={}", sessionId, e.getMessage());
            }
        });

        return interrupted;
    }

    /**
     * 本地中断逻辑（不发布 Redis 消息，供 {@link #interruptSession} 和 Redis 监听器调用）。
     * <p>
     * 中断流程：
     * <ol>
     *   <li>标记 sessionId 为已中断（供 doOnComplete 跳过 SSE 发送）</li>
     *   <li>立即向 SSE 发送 agent_end（interrupted=true）+ [DONE]，关闭 emitter</li>
     *   <li>从 activeEmitters / activeSubscriptions 中移除引用</li>
     *   <li><b>不 dispose 订阅</b>，让框架在 ReAct 检查点优雅中断 → handleInterrupt → 保存 AgentState</li>
     *   <li>启动延迟兜底任务：{@link #INTERRUPT_GRACE_TIMEOUT_SECONDS} 秒后若订阅仍未完成，强制 dispose</li>
     * </ol>
     * </p>
     * <p>
     * 这样设计的核心原因：AgentScope 2.0 的 {@code interrupt(userId, sessionId)} 设置 InterruptControl 标志后，
     * ReAct 循环在<b>下一次迭代前</b>检查标志并进入 handleInterrupt 路径保存状态。
     * 如果立即 dispose 订阅，HTTP 流被取消（CancellationException），框架没有机会保存 AgentState，
     * 导致用户后续用相同 sessionId 说"继续"时上下文丢失。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return true=成功中断，false=会话不存在或已结束
     */
    private boolean interruptSessionLocal(String sessionId) {
        Disposable subscription = activeSubscriptions.remove(sessionId);
        SseEmitter emitter = activeEmitters.remove(sessionId);

        if (subscription == null) {
            log.warn("[Chat] 中断失败：未找到活跃订阅: sessionId={}", sessionId);
            return false;
        }

        // 1. 标记为已中断，供 doOnComplete 检测并跳过 SSE 发送
        interruptedSessions.add(sessionId);

        // 2. 立即向 SSE 发送中断事件并关闭连接（让用户看到即时响应）
        if (emitter != null) {
            try {
                sendEvent(emitter, SSE_EVENT_AGENT_END, sessionId, Map.of(
                        "interrupted", true,
                        BusinessConst.RESPONSE_KEY_MESSAGE, "Agent 已被用户中断"));
            } catch (IOException e) {
                log.warn("[Chat] 中断时发送 agent_end 失败: {}", e.getMessage());
            }
            sendDone(emitter);
            safeComplete(emitter);
        }

        // 3. 不立即 dispose 订阅，给框架时间在 ReAct 检查点优雅中断并保存 AgentState
        //    框架的 interrupt(userId, sessionId) 已在 InterruptController 中调用，
        //    ReAct 循环将在当前迭代结束后检测到中断信号 → handleInterrupt → saveState
        log.info("[Chat] SSE 已关闭，等待框架优雅中断并保存 AgentState: sessionId={}", sessionId);

        // 4. 延迟兜底：超时后若订阅仍未完成，强制 dispose（防止框架卡死导致资源泄漏）
        Mono.delay(Duration.ofSeconds(INTERRUPT_GRACE_TIMEOUT_SECONDS))
                .doOnNext(i -> {
                    if (!subscription.isDisposed()) {
                        log.warn("[Chat] 框架中断超时({}s)，强制 dispose 订阅: sessionId={}",
                                INTERRUPT_GRACE_TIMEOUT_SECONDS, sessionId);
                        subscription.dispose();
                    }
                })
                .doOnError(e -> log.warn("[Chat] 延迟 dispose 兜底任务异常: sessionId={}, error={}",
                        sessionId, e.getMessage()))
                .subscribe();

        log.info("[Chat] 会话已中断: sessionId={}", sessionId);
        return true;
    }

    /**
     * 初始化 Redis 跨实例中断监听器（P1-7）。
     * <p>订阅 {@link #INTERRUPT_CHANNEL}，收到中断消息时调用 {@link #interruptSessionLocal}
     * 执行本地中断（不再次发布消息，避免循环广播）。</p>
     */
    @PostConstruct
    public void initInterruptListener() {
        stringRedisTemplateOpt.ifPresent(redis -> {
            try {
                redisListenerContainer = new RedisMessageListenerContainer();
                redisListenerContainer.setConnectionFactory(redis.getConnectionFactory());
                // 监听器：收到中断消息后检查本地是否有该会话的活跃订阅，有则本地中断
                MessageListener listener = (message, pattern) -> {
                    String sessionId = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                    log.info("[Chat] 收到跨实例中断消息: sessionId={}", sessionId);
                    if (activeSubscriptions.containsKey(sessionId)) {
                        interruptSessionLocal(sessionId);
                    }
                };
                redisListenerContainer.addMessageListener(listener, new ChannelTopic(INTERRUPT_CHANNEL));
                redisListenerContainer.afterPropertiesSet();
                redisListenerContainer.start();
                log.info("[Chat] 已订阅跨实例中断频道: {}", INTERRUPT_CHANNEL);
            } catch (Exception e) {
                log.warn("[Chat] 订阅跨实例中断频道失败，多实例中断不可用: {}", e.getMessage());
            }
        });
    }

    /**
     * 销毁 Redis 监听容器，释放资源（P1-7）。
     */
    @PreDestroy
    public void destroyInterruptListener() {
        if (redisListenerContainer != null) {
            try {
                redisListenerContainer.destroy();
                log.info("[Chat] 已销毁跨实例中断监听器");
            } catch (Exception e) {
                log.warn("[Chat] 销毁跨实例中断监听器失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 权限确认并恢复 Agent 执行（HITL 人机交互）。
     *
     * <p>当敏感工具调用被权限系统拦截为 ASK 决策时，Agent 暂停执行。
     * 用户在前端确认后，调用此方法发送携带 {@link ConfirmResult} 的消息恢复执行。</p>
     *
     * <p>恢复原理：AgentScope 2.0 的 Agent 状态通过 RedisAgentStateStore 持久化，
     * 工具调用状态为 ASKING。当收到携带 {@code Msg.METADATA_CONFIRM_RESULTS} 的消息时，
     * Agent 会将 ASKING 状态的工具调用替换为 ALLOWED（确认）或写入 DENIED 结果（拒绝），
     * 然后从中断点继续执行 ReAct 循环。</p>
     *
     * @param dto     权限确认请求
     * @param emitter SSE 发射器
     */
    public void confirmAndResume(PermissionConfirmDTO dto, SseEmitter emitter) {
        String sessionId = dto.getSessionId();
        String userId = dto.getUserId();
        String houseId = dto.getHouseId();
        String accessToken = dto.getAccessToken();

        final long tStart = System.currentTimeMillis();

        // 特性43：Redis 分布式限流检查（用户会话维度），防止刷接口
        if (!redisRateLimiterService.tryAcquireUserSession(userId, sessionId)) {
            log.warn("[Chat] 权限确认请求被限流: userId={}, sessionId={}", userId, sessionId);
            sendRateLimitedError(emitter, sessionId);
            return;
        }

        final ChatSessionRecorder recorder = new ChatSessionRecorder();

        try {
            SessionContext ctx = SessionContext.builder()
                    .userId(userId)
                    .houseId(houseId)
                    .sessionId(sessionId)
                    .accessToken(accessToken)
                    .build();

            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .put(CTX_KEY_SESSION_CONTEXT, ctx)
                    .build();

            // 始终从 Redis 加载待确认的完整数据（含 input 字段），确保 ToolUseBlock 与
            // 原始 RequireUserConfirmEvent 中的完全一致，Agent 才能匹配 ASKING 状态并恢复
            List<ToolCallInfo> pendingInfos = pendingConfirmationService.loadPendingConfirmations(sessionId);
            if (pendingInfos.isEmpty()) {
                throw new PermissionException(ErrorCode.PERMISSION_PENDING_NOT_FOUND,
                        "未找到待确认的权限请求: sessionId=" + sessionId);
            }

            // 按官方文档实现：
            //   confirmed 布尔值直接来自前端 confirms[].allowed，不做自然语言猜测
            //   resumeMsg 只设 metadata（有备注时附加 textContent，兼容 AgentState 丢失走 addToContext 的场景）
            //   官方文档：已拒绝的工具调用会产生 LLM 可见的错误结果，LLM 可能重试，这是模型行为
            Map<String, Boolean> userDecisions = new HashMap<>();
            List<PermissionConfirmDTO.ConfirmItem> items = dto.getConfirms();
            if (items != null) {
                for (PermissionConfirmDTO.ConfirmItem item : items) {
                    userDecisions.put(item.getToolCallId(), item.isAllowed());
                }
            }

            String userMessage = dto.getUserMessage();

            // 构建 ConfirmResult 列表（官方示例：new ConfirmResult(confirmed, toolCall, rules)）
            // content 字段必须设置，否则 ToolValidator 校验报 "argument 'content' is null"
            List<ConfirmResult> confirmResults = pendingInfos.stream()
                    .map(info -> {
                        ToolUseBlock toolUse = ToolUseBlock.builder()
                                .id(info.getToolCallId())
                                .name(info.getToolName())
                                .input(info.getInput())
                                .content(resolveToolContent(info))
                                .build();
                        boolean allowed = userDecisions.getOrDefault(info.getToolCallId(), true);
                        log.info("[Chat] 权限确认决策: sessionId={}, toolCallId={}, toolName={}, allowed={}",
                                sessionId, info.getToolCallId(), info.getToolName(), allowed);
                        return new ConfirmResult(allowed, toolUse, null);
                    })
                    .toList();

            pendingConfirmationService.clearPendingConfirmations(sessionId);

            // 检测用户是否拒绝了全部工具调用
            // 框架 applyConfirmResults 在 confirmed=false 时只写 "Permission denied by user" 到上下文
            // 然后继续 resumeAgent() 推理循环，LLM 可能调用其他工具绕路
            // 此时标记 userDenied，doOnNext 拦截事件不转发，doOnComplete 直接发 agent_end
            boolean allDenied = confirmResults.stream().noneMatch(ConfirmResult::isConfirmed);
            if (allDenied) {
                recorder.userDenied = true;
                log.info("[Chat] 用户拒绝全部工具调用，将拦截 LLM 绕路事件: sessionId={}", sessionId);
            }

            // 官方 resumeMsg 只设 metadata；有备注时附加 textContent
            UserMessage.Builder resumeBuilder = UserMessage.builder()
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults));
            if (userMessage != null && !userMessage.isBlank()) {
                resumeBuilder.textContent(userMessage);
            }
            Msg resumeMsg = resumeBuilder.build();

            final long tBeforeSubscribe = System.currentTimeMillis();
            log.info("[Chat] [计时] confirmAndResume 准备阶段: sessionId={}, 构建耗时={}ms, confirmCount={}",
                    sessionId, tBeforeSubscribe - tStart, confirmResults.size());

            sendEvent(emitter, SSE_EVENT_AGENT_START, sessionId, Map.of());

            activeEmitters.put(sessionId, emitter);

            // 同步权限上下文（Nacos 热更新）：恢复执行前确保 AgentState 的权限上下文
            // 与当前配置一致，避免恢复后仍因旧 ASK 规则再次触发 RequireUserConfirmEvent
            syncPermissionContext(userId, sessionId);

            Disposable resumeSubscription = harnessAgent.streamEvents(resumeMsg, runtimeContext)
                    .doOnNext(event -> {
                        // 用户拒绝全部工具时，框架仍会继续推理（调用其他工具绕路），
                        // 拦截这些事件不转发给前端
                        if (recorder.userDenied) {
                            return;
                        }
                        try {
                            forwardAgentEvent(emitter, sessionId, userId, event, recorder);
                        } catch (Exception e) {
                            log.warn("[Chat] SSE事件转发失败: eventType={}, error={}",
                                    event.getClass().getSimpleName(), e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);

                        long totalDurationMs = System.currentTimeMillis() - recorder.sessionStartTime;

                        // 保存对话记录与 Token 用量
                        if (recorder.finalReplyContent.length() > 0) {
                            chatRecordService.saveAssistantMessage(sessionId, userId, recorder.finalReplyContent.toString());
                        }
                        chatRecordService.saveTokenUsage(sessionId,
                                recorder.totalInputTokens.get(),
                                recorder.totalOutputTokens.get(),
                                agentScopeProperties.getModelName());

                        // 中断后框架优雅完成场景：emitter 已在 interruptSession 中关闭，跳过 SSE 发送
                        boolean wasInterrupted = interruptedSessions.remove(sessionId);
                        if (wasInterrupted) {
                            log.info("[Chat] 权限恢复后框架优雅中断完成: sessionId={}, 耗时={}ms",
                                    sessionId, totalDurationMs);
                            return;
                        }

                        // 用户拒绝场景：框架后台已清理 ASKING 状态，前端直接发 agent_end
                        // 不转发 LLM 的绕路输出（searchProduct 等无关工具调用）
                        if (recorder.userDenied) {
                            log.info("[Chat] 用户拒绝操作，直接结束: sessionId={}, 耗时={}ms",
                                    sessionId, totalDurationMs);
                            try {
                                sendEvent(emitter, SSE_EVENT_AGENT_END, sessionId, Map.of());
                            } catch (IOException e) {
                                log.warn("[Chat] 发送 agent_end 失败: {}", e.getMessage());
                            }
                            sendDone(emitter);
                            safeComplete(emitter);
                            return;
                        }

                        // 自动批准模式：emitter 生命周期移交后续事件，不在此处关闭
                        // recorder.autoConfirmed 来自第一层订阅，confirmAndResume 内部的新 recorder 默认为 false，
                        // 因此额外检查权限配置确保自动批准时 emitter 保持打开
                        boolean isAutoConfirm = recorder.autoConfirmed
                                || (agentScopeProperties.getPermission() != null
                                    && !agentScopeProperties.getPermission().isEnabled());
                        if (isAutoConfirm) {
                            log.info("[Chat] 自动批准模式，跳过 emitter 关闭: sessionId={}", sessionId);
                            return;
                        }

                        // 权限暂停场景：RequireUserConfirmHandler 已在事件链中发送 permission_paused，
                        // 此处仅关闭 emitter，不再重复发送
                        if (recorder.permissionPaused) {
                            log.info("[Chat] 权限恢复后再次触发 HITL 暂停: sessionId={}, 耗时={}ms",
                                    sessionId, totalDurationMs);
                            safeComplete(emitter);
                            return;
                        }

                        log.info("[Chat] 权限确认后恢复执行完成: sessionId={}, 总耗时={}ms", sessionId, totalDurationMs);

                        // 特性33：技能自动沉淀（权限确认恢复后完成，异步触发）
                        skillPromotionService.ifPresent(svc -> svc.promoteSkill(sessionId, userId, recorder));

                        try {
                            sendEvent(emitter, SSE_EVENT_AGENT_END, sessionId, Map.of());
                        } catch (IOException e) {
                            log.warn("[Chat] 发送 agent_end 失败: {}", e.getMessage());
                        }
                        try {
                            sendDone(emitter);
                            safeComplete(emitter);
                        } catch (Exception e) {
                            log.warn("[Chat] doOnComplete 关闭 SSE 失败: {}", e.getMessage());
                            try { emitter.completeWithError(e); } catch (Exception ignored) {}
                        }
                    })
                    .doOnError(err -> {
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);

                        // 中断导致的错误：清理标记并保存部分结果
                        if (interruptedSessions.remove(sessionId)) {
                            log.info("[Chat] 权限恢复后中断: sessionId={}, error={}",
                                    sessionId, err.getMessage());
                            return;
                        }

                        log.error("[Chat] 权限确认后恢复执行异常: sessionId={}", sessionId, err);
                        try {
                            sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                                    "error", Map.of("code", ERROR_CODE_AGENT_ERROR,
                                            "message", err.getMessage() != null ? err.getMessage() : MSG_AGENT_RESUME_ERROR)));
                        } catch (IOException ignored) {
                        }
                        try {
                            sendDone(emitter);
                            safeComplete(emitter);
                        } catch (Exception e) {
                            log.warn("[Chat] doOnError 关闭 SSE 失败: {}", e.getMessage());
                            try { emitter.completeWithError(e); } catch (Exception ignored) {}
                        }
                    })
                    .doOnCancel(() -> {
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);
                        interruptedSessions.remove(sessionId);
                        log.info("[Chat] 权限恢复订阅被取消: sessionId={}", sessionId);
                        if (recorder.finalReplyContent.length() > 0) {
                            chatRecordService.saveAssistantMessage(sessionId, userId, recorder.finalReplyContent.toString());
                        }
                        chatRecordService.saveTokenUsage(sessionId,
                                recorder.totalInputTokens.get(),
                                recorder.totalOutputTokens.get(),
                                agentScopeProperties.getModelName());
                    })
                    .subscribe();

            activeSubscriptions.put(sessionId, resumeSubscription);

        } catch (Exception e) {
            log.error("[Chat] 权限确认处理异常: sessionId={}", sessionId, e);
            try {
                sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                        "error", Map.of("code", ERROR_CODE_INTERNAL_ERROR, "message", e.getMessage())));
            } catch (IOException ignored) {
            }
            sendDone(emitter);
            safeComplete(emitter);
        }
    }

    /**
     * 同步权限上下文到当前会话的 AgentState（支持 Nacos 热更新）。
     * <p>
     * 在每次 streamEvents 调用前执行，确保 AgentState 中的 PermissionContextState
     * 与 Nacos 最新配置一致。解决 permission.enabled 从 true 改为 false 后
     * ASK 规则仍残留在 AgentState/permissionEngineCache 中导致工具仍走人机确认的问题。
     * </p>
     * <p>
     * 实现原理：
     * <ol>
     *   <li>通过 harnessAgent.getDelegate() 获取内部 ReActAgent</li>
     *   <li>调用 getAgentState(userId, sessionId) 获取当前会话状态（触发 stateCache 缓存）</li>
     *   <li>根据 AgentScopeProperties（@RefreshScope）当前值构建新的 PermissionContextState</li>
     *   <li>若与 AgentState 中的上下文不一致，更新 state 并清除 permissionEngineCache</li>
     *   <li>后续 activateSlotForContext 会从更新的 state 创建新 PermissionEngine</li>
     * </ol>
     * </p>
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     */
    private void syncPermissionContext(String userId, String sessionId) {
        try {
            ReActAgent reActAgent = harnessAgent.getDelegate();
            AgentState state = reActAgent.getAgentState(userId, sessionId);
            PermissionContextState currentContext = buildPermissionContext();
            PermissionContextState stateContext = state.getPermissionContext();

            if (stateContext == null || !stateContext.equals(currentContext)) {
                state.setPermissionContext(currentContext);
                clearPermissionEngineCache(reActAgent);

                // 关键：将更新后的 AgentState 持久化回 Redis stateStore。
                // activateSlotForContext 在 stateStore != null 时会重新从 Redis 加载 AgentState
                // （loadOrCreateAgentStateForSlot → stateStore.get），覆盖 stateCache 中的更新。
                // 若不持久化，syncPermissionContext 的更新会被 activateSlotForContext 覆盖，
                // 导致 permission.enabled 从 false 改为 true 后仍使用旧的 BYPASS 无 ASK 上下文。
                agentStateStore.ifPresent(store -> {
                    try {
                        store.save(userId, sessionId, "agent_state", state);
                        log.debug("[Chat] AgentState 已持久化到 stateStore: userId={}, sessionId={}",
                                userId, sessionId);
                    } catch (Exception saveEx) {
                        log.warn("[Chat] AgentState 持久化失败: userId={}, sessionId={}, error={}",
                                userId, sessionId, saveEx.getMessage());
                    }
                });

                log.info("[Chat] 权限上下文已同步: userId={}, sessionId={}, enabled={}, askTools={}",
                        userId, sessionId,
                        agentScopeProperties.getPermission().isEnabled(),
                        agentScopeProperties.getPermission().getAskTools());
            }
        } catch (Exception e) {
            log.warn("[Chat] 权限上下文同步失败（降级为当前状态）: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage());
        }
    }

    /**
     * 根据当前 Nacos 配置构建 PermissionContextState。
     * <p>
     * 与 PermissionConfig.permissionContextState() 逻辑一致，但读取的是
     * @RefreshScope 刷新后的 AgentScopeProperties，确保热更新生效。
     * </p>
     * <ul>
     *   <li>enabled=false：纯 BYPASS 模式，无 ASK 规则，所有工具直接放行</li>
     *   <li>enabled=true：BYPASS 模式 + ask-tools 的 ASK 规则，仅指定工具触发确认</li>
     * </ul>
     *
     * @return 当前配置对应的 PermissionContextState
     */
    private PermissionContextState buildPermissionContext() {
        AgentScopeProperties.Permission permission = agentScopeProperties.getPermission();

        // enabled=false：纯 BYPASS，所有工具直接放行（无 ASK 规则）
        if (!permission.isEnabled()) {
            return PermissionContextState.builder()
                    .mode(PermissionMode.BYPASS)
                    .build();
        }

        // enabled=true：BYPASS + ASK 规则（仅 ask-tools 中的工具触发 HITL 确认）
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.BYPASS);

        List<String> askTools = permission.getAskTools();
        if (askTools != null) {
            for (String toolName : askTools) {
                builder.addAskRule(toolName,
                        new PermissionRule(toolName, null, PermissionBehavior.ASK, "nacos"));
            }
        }

        return builder.build();
    }

    /**
     * 通过反射清除 ReActAgent 的 permissionEngineCache。
     * <p>
     * PermissionEngine 缓存在 ReActAgent 的 private final 字段中，
     * 更新 AgentState 的权限上下文后需清除缓存，否则下次仍使用旧引擎
     * （旧引擎持有旧 ASK 规则，导致 enabled=false 时仍触发 RequireUserConfirmEvent）。
     * </p>
     *
     * @param reActAgent ReActAgent 实例
     * @throws ReflectiveOperationException 反射访问失败
     */
    private void clearPermissionEngineCache(ReActAgent reActAgent) throws ReflectiveOperationException {
        Field cacheField = ReActAgent.class.getDeclaredField("permissionEngineCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> cache =
                (ConcurrentHashMap<String, Object>) cacheField.get(reActAgent);
        cache.clear();
    }

    public void streamEvents(ChatStreamDTO dto, SseEmitter emitter) {
        String sessionId = dto.getSessionId();
        String userId = dto.getUserId();
        String houseId = dto.getHouseId();
        String accessToken = dto.getAccessToken();

        // 特性43：Redis 分布式限流检查（用户会话维度），防止刷接口
        if (!redisRateLimiterService.tryAcquireUserSession(userId, sessionId)) {
            log.warn("[Chat] 请求被限流: userId={}, sessionId={}", userId, sessionId);
            sendRateLimitedError(emitter, sessionId);
            return;
        }

        final ChatSessionRecorder recorder = new ChatSessionRecorder();

        try {
            SessionContext ctx = SessionContext.builder()
                    .userId(userId)
                    .houseId(houseId)
                    .sessionId(sessionId)
                    .accessToken(accessToken)
                    .images(convertToImageInfos(dto))
                    .build();

            // 构建 RuntimeContext，预注入 SessionContext
            // SessionContextMiddleware 会检测到已存在的 SessionContext，不再覆盖
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .put(CTX_KEY_SESSION_CONTEXT, ctx)
                    .build();

            // 异步保存用户输入消息（不阻塞主流程）
            chatRecordService.saveUserMessage(sessionId, userId, houseId, dto.getUserMessage());

            sendEvent(emitter, SSE_EVENT_AGENT_START, sessionId, Map.of());

            activeEmitters.put(sessionId, emitter);

            // 同步权限上下文（Nacos 热更新）：确保 AgentState 的 PermissionContextState
            // 与当前配置一致，enabled=false 时清除 ASK 规则使所有工具直接放行
            syncPermissionContext(userId, sessionId);

            // ===== 本地快速通道：1.5b 分类 → Java 调工具 → 1.5b 汇总（0 云端 token） =====
            // 命中设备控制 → 直接返回结果，不走云端 ReAct
            // 未命中（闲聊/多意图/分类失败）→ 返回 null，回退云端 HarnessAgent
            /*LocalFastPathExecutor.FastPathResult fastResult =
                    localFastPathExecutor.tryExecute(dto.getUserMessage(), ctx);
            if (fastResult != null) {
                handleFastPathResult(emitter, sessionId, userId, houseId, fastResult);
                return;
            }*/

            // ===== 云端 HarnessAgent ReAct（现有逻辑，处理复杂对话/多意图/记忆等） =====
            // 构建 Agent 消息：检测是否有待确认的权限请求（HITL ASKING 状态）
            Msg agentMessage = buildAgentMessageWithPermissionCheck(dto, sessionId);

            String selectedModelName = agentScopeProperties.getModelName();
            // 委托 HarnessAgent 执行 ReAct 推理循环
            Disposable subscription = harnessAgent.streamEvents(agentMessage, runtimeContext)
                    .doOnNext(event -> {
                        try {
                            forwardAgentEvent(emitter, sessionId, userId, event, recorder);
                        } catch (Exception e) {
                            log.warn("[Chat] SSE事件转发失败: eventType={}, error={}",
                                    event.getClass().getSimpleName(), e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);

                        long totalDurationMs = System.currentTimeMillis() - recorder.sessionStartTime;

                        // 保存对话记录与 Token 用量（无论是否被中断，都需落库部分结果）
                        // 模型名使用意图路由实际选中的模型（Ollama / 云端），而非硬编码全局模型名
                        if (recorder.finalReplyContent.length() > 0) {
                            chatRecordService.saveAssistantMessage(sessionId, userId, recorder.finalReplyContent.toString());
                        }
                        chatRecordService.saveTokenUsage(sessionId,
                                recorder.totalInputTokens.get(),
                                recorder.totalOutputTokens.get(),
                                selectedModelName);

                        // 中断后框架优雅完成场景：emitter 已在 interruptSession 中关闭，跳过 SSE 发送
                        boolean wasInterrupted = interruptedSessions.remove(sessionId);
                        if (wasInterrupted) {
                            log.info("[Chat] 框架优雅中断完成，AgentState 已保存: sessionId={}, 耗时={}ms",
                                    sessionId, totalDurationMs);
                            return;
                        }

                        // 自动批准模式：emitter 生命周期移交 confirmAndResume，不在此处关闭
                        if (recorder.autoConfirmed) {
                            log.info("[Chat] 自动批准模式，跳过 emitter 关闭: sessionId={}", sessionId);
                            return;
                        }

                        // 权限暂停场景：RequireUserConfirmHandler 已在事件链中发送 permission_paused，
                        // 此处仅关闭 emitter，不再重复发送（避免冗余 event + [DONE] 导致前端误判流结束）
                        if (recorder.permissionPaused) {
                            log.info("[Chat] Agent 因权限确认暂停: sessionId={}, 耗时={}ms", sessionId, totalDurationMs);
                            safeComplete(emitter);
                            return;
                        }

                        // 正常完成：Agent 执行完毕（含记忆整合等后台收尾）
                        log.info("[Chat] HarnessAgent 执行完成: sessionId={}, 总耗时={}ms", sessionId, totalDurationMs);

                        // 特性33：技能自动沉淀（异步，不阻塞 SSE 主流程；仅在 Agent 正常完成时触发）
                        skillPromotionService.ifPresent(svc -> svc.promoteSkill(sessionId, userId, recorder));

                        try {
                            sendEvent(emitter, SSE_EVENT_AGENT_END, sessionId, Map.of());
                        } catch (IOException e) {
                            log.warn("[Chat] 发送 agent_end 失败: {}", e.getMessage());
                        }
                        try {
                            sendDone(emitter);
                            safeComplete(emitter);
                        } catch (Exception e) {
                            log.warn("[Chat] doOnComplete 关闭 SSE 失败: {}", e.getMessage());
                        }
                    })
                    .doOnError(err -> {
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);

                        // 中断导致的错误（框架超时 dispose 后可能触发 cancel/error）：清理中断标记
                        if (interruptedSessions.remove(sessionId)) {
                            log.info("[Chat] 中断后框架报错（AgentState 可能已部分保存）: sessionId={}, error={}",
                                    sessionId, err.getMessage());
                            // 保存部分回复
                            if (recorder.finalReplyContent.length() > 0) {
                                chatRecordService.saveAssistantMessage(sessionId, userId, recorder.finalReplyContent.toString());
                            }
                            chatRecordService.saveTokenUsage(sessionId,
                                    recorder.totalInputTokens.get(),
                                    recorder.totalOutputTokens.get(),
                                    agentScopeProperties.getModelName());
                            return;
                        }

                        log.error("[Chat] HarnessAgent 执行异常: sessionId={}", sessionId, err);
                        String errMsg = err.getMessage() != null ? err.getMessage() : "";

                        // 检测残留 ASKING 状态错误（Redis 中有持久化状态但本次消息未携带 ConfirmResult）
                        if (errMsg.contains(ASKING_ERROR_KEYWORD)) {
                            log.warn("[Chat] 检测到残留 ASKING 状态: sessionId={}, userId={}, 尝试清除并恢复", sessionId, userId);
                            handleAskingResidualError(emitter, sessionId, userId);
                        } else {
                            try {
                                sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                                        "error", Map.of("code", ERROR_CODE_AGENT_ERROR,
                                                "message", errMsg.isEmpty() ? MSG_AGENT_EXECUTION_ERROR : errMsg)));
                            } catch (IOException ignored) {
                            }
                        }
                        try {
                            sendDone(emitter);
                            safeComplete(emitter);
                        } catch (Exception e) {
                            log.warn("[Chat] doOnError 关闭 SSE 失败: {}", e.getMessage());
                        }
                    })
                    .doOnCancel(() -> {
                        // cancel 信号：interruptSession 超时兜底 dispose 或客户端断开连接时触发
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);
                        interruptedSessions.remove(sessionId);

                        log.info("[Chat] Agent 订阅被取消: sessionId={}", sessionId);
                        // 保存部分回复，确保中断时已生成的内容不丢失
                        if (recorder.finalReplyContent.length() > 0) {
                            chatRecordService.saveAssistantMessage(sessionId, userId, recorder.finalReplyContent.toString());
                        }
                        chatRecordService.saveTokenUsage(sessionId,
                                recorder.totalInputTokens.get(),
                                recorder.totalOutputTokens.get(),
                                agentScopeProperties.getModelName());
                    })
                    .subscribe();

            activeSubscriptions.put(sessionId, subscription);
            log.info("[Chat] 已注册活跃订阅: sessionId={}, 可被中断", sessionId);

        } catch (Exception e) {
            log.error("[Chat] 消息处理异常: sessionId={}", sessionId, e);
            try {
                sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                        "error", Map.of("code", ERROR_CODE_INTERNAL_ERROR, "message", e.getMessage())));
            } catch (IOException ignored) {
            }
            sendDone(emitter);
            safeComplete(emitter);
        }
    }

    /**
     * 处理残留 ASKING 状态错误。
     * <p>当 AgentScope 报 "paused for human-in-the-loop confirmation" 错误时，
     * 说明框架的 RedisAgentStateStore 中仍保留了上一轮的 ASKING 状态（工具调用待确认），
     * 但 PendingConfirmationService 的 TTL（30分钟）可能已过期导致无法恢复 ConfirmResult。</p>
     *
     * <p>处理策略：</p>
     * <ol>
     *   <li>先从 Redis 加载待确认数据，若有则重新发送 permission_ask 事件</li>
     *   <li>若 PendingConfirmationService 数据已过期（空），则调用 AgentStateStore.delete()
     *       清除框架残留 ASKING 状态，通知前端刷新会话后重试</li>
     * </ol>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     * @param userId    用户 ID（用于清除框架状态）
     */
    private void handleAskingResidualError(SseEmitter emitter, String sessionId, String userId) {
        List<ToolCallInfo> pending = pendingConfirmationService.loadPendingConfirmations(sessionId);
        if (!pending.isEmpty()) {
            log.info("[Chat] 从 Redis 恢复待确认数据成功，重新发送权限确认: sessionId={}", sessionId);
            try {
                com.agent.scope.framework.bo.event.PermissionAskEventBO eventBO =
                        com.agent.scope.framework.bo.event.PermissionAskEventBO.builder()
                                .type(AgentEventEnum.PERMISSION_ASK.getDesc())
                                .sessionId(sessionId)
                                .toolCalls(pending)
                                .build();
                emitter.send(SseEmitter.event().name(AgentEventEnum.PERMISSION_ASK.getDesc()).data(toJson(eventBO)));

                sendEvent(emitter, SSE_EVENT_PERMISSION_PAUSED, sessionId, Map.of(
                        "message", MSG_ASKING_RESIDUAL));
            } catch (IllegalStateException e) {
                log.debug("[Chat] 重新发送 permission_ask 失败(emitter已关闭): sessionId={}", sessionId);
            } catch (IOException e) {
                log.warn("[Chat] 重新发送 permission_ask 失败: {}", e.getMessage());
            }
        } else {
            // PendingConfirmationService 的 TTL 已过期，但框架的 RedisAgentStateStore 仍保留了 ASKING 状态
            // 需要清除框架状态，让用户下次消息能正常执行
            log.warn("[Chat] PendingConfirm 已过期，清除框架残留 ASKING 状态: sessionId={}, userId={}", sessionId, userId);
            pendingConfirmationService.clearPendingConfirmations(sessionId);
            // 调用 AgentStateStore.delete() 删除框架在 Redis 中的持久化状态
            agentStateStore.ifPresent(store -> {
                try {
                    store.delete(userId, sessionId);
                    log.info("[Chat] 已清除框架残留状态: userId={}, sessionId={}", userId, sessionId);
                } catch (Exception ex) {
                    log.warn("[Chat] 清除框架状态失败: userId={}, sessionId={}, error={}", userId, sessionId, ex.getMessage());
                }
            });
            try {
                sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                        "error", Map.of("code", ERROR_CODE_AGENT_ERROR,
                                "message", "上一轮会话的权限确认状态已过期，请在前端刷新会话后重新发送消息")));
            } catch (IOException ignored) {
            }
        }
    }

    // ==================== Agent 消息构建（含 HITL 权限确认处理）====================

    /**
     * 解析 ToolCallInfo 的 content 字段（工具入参的原始 JSON 字符串）。
     * <p>AgentScope 2.0 的 {@code ToolExecutor.executeCore()} 使用
     * {@code toolCall.getContent()} 而非 {@code toolCall.getInput()} 进行参数校验。
     * 若 content 为 null，校验报 "Schema validation error: argument 'content' is null"。</p>
     *
     * <p>解析优先级：</p>
     * <ol>
     *   <li>{@code info.getContent()} —— 从 Redis 加载的原始 JSON（PendingConfirmationService 已缓存）</li>
     *   <li>将 {@code info.getInput()} 序列化为 JSON 作为回退</li>
     *   <li>兜底返回 "{}"</li>
     * </ol>
     *
     * @param info Redis 中缓存的工具调用信息
     * @return 工具入参的 JSON 字符串，永不为 null
     */
    private String resolveToolContent(ToolCallInfo info) {
        // 优先使用 Redis 中缓存的 content（由 PendingConfirmationService 从 ToolUseBlock.getContent() 保存）
        if (info.getContent() != null && !info.getContent().isBlank()) {
            return info.getContent();
        }

        // 回退：将 input Map 序列化为 JSON
        Map<String, Object> input = info.getInput();
        if (input != null && !input.isEmpty()) {
            try {
                return OBJECT_MAPPER.writeValueAsString(input);
            } catch (Exception e) {
                log.warn("[Chat] 序列化 input 为 content 失败: toolCallId={}, error={}",
                        info.getToolCallId(), e.getMessage());
            }
        }

        // 兜底：空 JSON 对象（与 ToolCallsAccumulator.build() 行为一致）
        log.warn("[Chat] content 和 input 均不可用，使用空 JSON: toolCallId={}, toolName={}",
                info.getToolCallId(), info.getToolName());
        return "{}";
    }

    /**
     * 构建 Agent 消息，自动处理待确认的权限请求（HITL）。
     *
     * <p>当上一轮 Agent 执行因敏感工具调用被权限系统拦截（ASKING 状态）时，
     * AgentScope 要求恢复消息必须携带 {@link ConfirmResult} 元数据。
     * 本方法从 Redis 检测是否存在当前会话的待确认工具调用：
     * <ul>
     *   <li>存在且用户消息匹配拒绝关键词（取消/拒绝/不要等）→ 构建 allowed=false 的 ConfirmResult</li>
     *   <li>存在且用户消息是其他内容 → 默认视为确认（用户可能直接说设备指令而非"确认"二字）</li>
     *   <li>不存在 → 普通用户消息，走正常 ReAct 流程</li>
     * </ul>
     * </p>
     *
     * @param dto       聊天请求 DTO
     * @param sessionId 会话 ID
     * @return Agent 消息（可能携带 ConfirmResult 元数据）
     */
    private Msg buildAgentMessageWithPermissionCheck(ChatStreamDTO dto, String sessionId) {
        List<ToolCallInfo> pendingTools = pendingConfirmationService.loadPendingConfirmations(sessionId);
        if (pendingTools.isEmpty()) {
            return buildUserMessage(dto);
        }

        // 按官方文档实现：用户通过 /api/chat/stream 恢复时默认确认（allowed=true）
        // resumeMsg 只设 metadata，有 userMessage 时附加 textContent
        log.info("[Chat] 检测到待确认权限请求，按确认恢复: sessionId={}", sessionId);

        List<ConfirmResult> confirmResults = pendingTools.stream()
                .map(info -> {
                    ToolUseBlock toolUse = ToolUseBlock.builder()
                            .id(info.getToolCallId())
                            .name(info.getToolName())
                            .input(info.getInput())
                            .content(resolveToolContent(info))
                            .build();
                    return new ConfirmResult(true, toolUse, null);
                })
                .toList();

        pendingConfirmationService.clearPendingConfirmations(sessionId);

        UserMessage.Builder builder = UserMessage.builder()
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults));
        String userMessage = dto.getUserMessage();
        if (userMessage != null && !userMessage.isBlank()) {
            builder.textContent(userMessage);
        }
        return builder.build();
    }

    /**
     * 将 ChatStreamDTO 中的图片列表转换为 ImageInfo 列表。
     *
     * <p>供 FloorPlanTool 等需要图片的工具使用。图片数据从会话上下文获取，
     * 工具方法无需通过 @ToolParam 传递大量 Base64 数据。</p>
     *
     * @param dto 聊天请求 DTO
     * @return ImageInfo 列表；无图片时返回 null
     */
    private List<ImageInfo> convertToImageInfos(ChatStreamDTO dto) {
        List<String> images = dto.getImages();
        if (images == null || images.isEmpty()) {
            return null;
        }
        boolean isBase64 = ImageTypeEnum.BASE64.equals(dto.getImageType());
        List<ImageInfo> result = new ArrayList<>(images.size());
        for (String img : images) {
            if (img == null || img.isBlank()) {
                continue;
            }
            ImageInfo info = ImageInfo.builder()
                    .base64(isBase64 ? img : null)
                    .mimeType("image/png")
                    .fileName(isBase64 ? null : img)
                    .build();
            result.add(info);
        }
        return result.isEmpty() ? null : result;
    }

    private Msg buildUserMessage(ChatStreamDTO dto) {
        String userMessage = dto.getUserMessage();
        List<String> images = dto.getImages();
        List<String> audios = dto.getAudios();
        List<String> videos = dto.getVideos();

        boolean noMedia = (images == null || images.isEmpty())
                && (audios == null || audios.isEmpty())
                && (videos == null || videos.isEmpty());
        if (noMedia) {
            return new UserMessage(userMessage);
        }

        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(TextBlock.builder().text(userMessage).build());

        appendMediaBlocks(blocks, images, ImageTypeEnum.BASE64.equals(dto.getImageType()), MediaKind.IMAGE);
        appendMediaBlocks(blocks, audios, MediaTypeEnum.BASE64.equals(dto.getAudioType()), MediaKind.AUDIO);
        appendMediaBlocks(blocks, videos, MediaTypeEnum.BASE64.equals(dto.getVideoType()), MediaKind.VIDEO);

        return new UserMessage(blocks);
    }

    /**
     * 媒体种类枚举（内部使用，区分图片/音频/视频的构造逻辑与默认 MIME）。
     */
    private enum MediaKind {
        IMAGE, AUDIO, VIDEO
    }

    /**
     * 批量追加媒体 ContentBlock 到 blocks 列表。
     *
     * @param blocks      目标块列表
     * @param mediaList   媒体数据列表（URL 或 Base64）
     * @param isBase64    是否为 Base64 编码
     * @param mediaKind   媒体种类
     */
    private void appendMediaBlocks(List<ContentBlock> blocks, List<String> mediaList,
                                   boolean isBase64, MediaKind mediaKind) {
        if (mediaList == null || mediaList.isEmpty()) {
            return;
        }
        for (String media : mediaList) {
            if (media == null || media.isBlank()) {
                continue;
            }
            switch (mediaKind) {
                case IMAGE -> blocks.add(isBase64
                        ? ImageBlock.builder().source(new Base64Source(FileConst.MEDIA_TYPE, media)).build()
                        : ImageBlock.builder().source(new URLSource(media)).build());
                case AUDIO -> blocks.add(isBase64
                        ? AudioBlock.builder().source(new Base64Source(FileConst.AUDIO_MEDIA_TYPE, media)).build()
                        : AudioBlock.builder().source(new URLSource(media)).build());
                case VIDEO -> blocks.add(isBase64
                        ? VideoBlock.builder().source(new Base64Source(FileConst.VIDEO_MEDIA_TYPE, media)).build()
                        : VideoBlock.builder().source(new URLSource(media)).build());
            }
        }
    }

    // ==================== Agent 事件转发（策略模式 + 注册表分发）====================

    /**
     * 将 AgentScope AgentEvent 转换为 SSE 事件并发送。
     * <p>
     * 通过 {@link AgentEventHandlerRegistry} 按事件 Class O(1) 查找对应处理器并执行，
     * 消除原有 16 个 instanceof if-else 分支。未注册处理器的事件走兜底逻辑。
     * </p>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param event     AgentScope 事件
     * @param recorder  会话级别记录容器
     * @throws IOException SSE 发送异常
     */
    private void forwardAgentEvent(SseEmitter emitter, String sessionId, String userId,
                                   AgentEvent event, ChatSessionRecorder recorder) throws IOException {
        EventContext ctx = new EventContext(emitter, sessionId, userId, recorder, OBJECT_MAPPER);

        boolean handled = agentEventHandlerRegistry.dispatch(ctx, event);

        // 兜底：未注册处理器的事件（AgentStartEvent/ExceedMaxItersEvent/AgentEndEvent 等）
        if (!handled && !(event instanceof AgentEndEvent)) {
            AgentOtherEventBO eventBO = AgentOtherEventBO.builder()
                    .type(event.getClass().getSimpleName())
                    .sessionId(sessionId)
                    .eventType(event.getType() != null ? event.getType().name() : "UNKNOWN")
                    .build();
            emitter.send(SseEmitter.event().name(eventBO.getType()).data(toJson(eventBO)));
        }

        if (event instanceof AgentEndEvent) {
            log.info("[Chat] 收到 AgentEndEvent，等待 doOnComplete 关闭 SSE: sessionId={}", sessionId);
        }

        log.debug("[SSE] 转发Agent事件: sessionId={}, eventType={}",
                sessionId, event.getClass().getSimpleName());
    }

    // ==================== 快速通道结果处理 ====================

    /**
     * 处理本地快速通道结果，发送 SSE 事件并保存记录。
     * <p>
     * 快速通道命中设备控制后，不走 AgentScope ReAct 链路，
     * 直接将 1.5b 生成的回复和工具结果通过 SSE 推送给前端。
     * </p>
     *
     * @param emitter    SSE 发射器
     * @param sessionId  会话 ID
     * @param userId     用户 ID
     * @param houseId    房屋 ID
     * @param fastResult 快速通道执行结果
     */
    private void handleFastPathResult(SseEmitter emitter, String sessionId, String userId,
                                      String houseId, LocalFastPathExecutor.FastPathResult fastResult) {
        ToolResultVO toolResult = fastResult.toolResult();
        String reply = fastResult.reply();

        try {
            // 1. 发送 result 事件（与云端 ToolResultEndHandler.forwardStructuredResult 格式一致）
            //    前端按 routePath 路由渲染结构化数据（设备列表/控制结果等）
            if (toolResult.getRoutePath() != null && !toolResult.getRoutePath().isBlank()) {
                Map<String, Object> resultEvent = new LinkedHashMap<>();
                resultEvent.put("type", "result");
                resultEvent.put("sessionId", sessionId);
                resultEvent.put("routePath", toolResult.getRoutePath());
                resultEvent.put("data", toolResult.getData() != null ? toolResult.getData() : new LinkedHashMap<>());
                resultEvent.put("message", toolResult.getMessage() != null ? toolResult.getMessage() : "");
                emitter.send(SseEmitter.event().name("result").data(OBJECT_MAPPER.writeValueAsString(resultEvent)));
                log.info("[Chat] 快速通道发送 result 事件: routePath={}", toolResult.getRoutePath());
            }

            // 2. 发送 agent_end 事件（携带回复文本，前端朗读/显示）
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("reply", reply);
            fields.put("broadcastText", toolResult.getBroadcastText() != null
                    ? toolResult.getBroadcastText() : reply);
            fields.put("fastPath", true);
            sendEvent(emitter, SSE_EVENT_AGENT_END, sessionId, fields);
        } catch (IOException e) {
            log.warn("[Chat] 快速通道发送 SSE 事件失败: {}", e.getMessage());
        }

        // 异步保存对话记录
        chatRecordService.saveAssistantMessage(sessionId, userId, reply);
        chatRecordService.saveTokenUsage(sessionId, 0, 0, "qwen2.5:1.5b");

        sendDone(emitter);
        safeComplete(emitter);

        log.info("[Chat] 快速通道完成: sessionId={}, routePath={}", sessionId, toolResult.getRoutePath());
    }

    // ==================== SSE 事件发送工具方法 ====================

    /**
     * 发送 SSE 事件（JSON 格式）。
     *
     * @param emitter   SSE 发射器
     * @param type      事件类型
     * @param sessionId 会话 ID
     * @param fields    额外字段
     * @throws IOException SSE 发送异常
     */
    private void sendEvent(SseEmitter emitter, String type, String sessionId, Map<String, Object> fields) throws IOException {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("sessionId", sessionId);
        event.put("timestamp", System.currentTimeMillis());
        if (fields != null) {
            event.putAll(fields);
        }

        String json = toJson(event);
        try {
            emitter.send(SseEmitter.event().name(type).data(json));
        } catch (IllegalStateException e) {
            // Emitter已关闭（客户端断开、会话中断或已完成），包装为IOException供调用方统一处理
            // 不包装则IllegalStateException会绕过调用方的catch(IOException)导致错误级联
            throw new IOException("SSE Emitter已关闭", e);
        }
        log.debug("[SSE] 发送事件: type={}, sessionId={}", type, sessionId);
    }

    /**
     * 发送 SSE 结束标记 [DONE]。
     *
     * @param emitter SSE 发射器
     */
    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IllegalStateException e) {
            // Emitter已关闭（客户端断开、会话中断或已完成），忽略重复关闭
            log.debug("[SSE] 发送 [DONE] 失败(emitter已关闭)");
        } catch (IOException e) {
            log.warn("[SSE] 发送 [DONE] 失败: {}", e.getMessage());
        }
    }

    /**
     * 安全关闭 SSE Emitter。
     * <p>
     * 当 Emitter 已被关闭（客户端断开、会话中断、权限暂停等场景）时，
     * {@code emitter.complete()} 会抛出 {@link IllegalStateException}（ResponseBodyEmitter has already completed）。
     * 本方法捕获该异常并降级为 debug 日志，避免错误级联到 doOnError 导致 {@code ErrorCallbackNotImplemented}。
     * </p>
     *
     * @param emitter SSE 发射器
     */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("[SSE] Emitter已关闭，complete调用忽略");
        }
    }

    /**
     * 发送限流错误 SSE 事件并关闭连接。
     * <p>
     * 当 Redis 限流器拒绝请求时，通过 SSE 向前端推送限流错误事件，
     * 包含限流错误码与提示消息，随后关闭 SSE 连接。
     * </p>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     */
    private void sendRateLimitedError(SseEmitter emitter, String sessionId) {
        try {
            sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                    "error", Map.of(
                            BusinessConst.RESPONSE_KEY_CODE, ERROR_CODE_RATE_LIMITED,
                            BusinessConst.RESPONSE_KEY_MESSAGE, MSG_RATE_LIMIT_EXCEEDED)));
        } catch (IOException e) {
            log.warn("[SSE] 发送限流错误事件失败: {}", e.getMessage());
        }
        sendDone(emitter);
        safeComplete(emitter);
    }

    /**
     * 对象转 JSON 字符串（使用 Jackson，性能远优于 org.json）。
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("[Chat] JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    // ==================== 会话级别记录容器 ====================

    /**
     * 会话级别对话记录容器。
     * <p>
     * 在一次 {@link #streamEvents} 调用周期内累积对话全链路信息，
     * 包括 LLM 思考内容、最终回复、工具调用入参/出参/开始时间、Token 消耗。
     * 所有可变字段均使用线程安全容器（{@link StringBuilder} 在单线程 Reactive 流中安全，
     * {@link ConcurrentHashMap} 和 {@link java.util.concurrent.atomic.AtomicLong} 保证多事件线程安全）。
     * </p>
     *
     * @author zqs
     * @since 2.0.0
     */
    public static final class ChatSessionRecorder {

        /** 会话开始时间（用于计算总耗时） */
        private final long sessionStartTime = System.currentTimeMillis();

        /**
         * Agent 是否因权限确认（HITL）而暂停。
         * <p>当 {@link io.agentscope.core.event.RequireUserConfirmEvent} 触发时置为 true，
         * {@code doOnComplete} 据此区分正常结束与权限暂停。</p>
         * <p>使用 volatile 保证跨线程可见性（事件发射与 doOnComplete 可能在不同线程）。</p>
         */
        public volatile boolean permissionPaused = false;

        /** 自动批准模式：permission.enabled=false 时，doOnComplete 不关闭 emitter */
        public volatile boolean autoConfirmed = false;

        /**
         * 用户是否拒绝了全部工具调用（HITL 确认）。
         * <p>当 {@code confirmAndResume} 收到全部 {@code allowed=false} 时置为 true。
         * 框架 {@code applyConfirmResults} 在 confirmed=false 时只写入 "Permission denied by user"
         * 到上下文然后继续 ReAct 循环（官方源码 ReActAgent.java:1639-1653），
         * LLM 可能调用其他工具绕路。此标志用于在 {@code doOnNext} 中拦截 LLM 事件，
         * 不转发给前端；{@code doOnComplete} 中直接发送 agent_end。</p>
         */
        public volatile boolean userDenied = false;

        /** 当前思考块内容累积器（ThinkingBlockDeltaEvent 累加，ThinkingBlockEndEvent 落库后清空） */
        public final StringBuilder thinkingContent = new StringBuilder();

        /** 最终回复内容累积器（TextBlockDeltaEvent 累加） */
        public final StringBuilder finalReplyContent = new StringBuilder();

        /** 工具调用开始时间映射：toolCallId → 开始时间戳（毫秒） */
        public final Map<String, Long> toolCallStartTimes = new ConcurrentHashMap<>();

        /** 工具调用入参累积映射：toolCallId → arguments JSON 片段累积器 */
        public final Map<String, StringBuilder> toolCallArguments = new ConcurrentHashMap<>();

        /** 工具调用出参累积映射：toolCallId → result 文本累积器 */
        public final Map<String, StringBuilder> toolCallResults = new ConcurrentHashMap<>();

        /**
         * 模型调用开始时间映射：replyId → 开始时间戳（毫秒）。
         * <p>{@link io.agentscope.core.event.ModelCallStartEvent} 时写入，
         * {@link io.agentscope.core.event.ModelCallEndEvent} 时取出计算耗时。</p>
         */
        public final Map<String, Long> modelCallStartTimes = new ConcurrentHashMap<>();

        /**
         * 模型调用输出累积映射：replyId → 输出内容累积器。
         * <p>在 ModelCallStart 与 ModelCallEnd 之间，所有 TextBlock/ThinkingBlock/ToolCall
         * 的 delta 片段按 replyId 累积，ModelCallEnd 时取出落库到 ModelCallRecord。</p>
         */
        public final Map<String, StringBuilder> modelCallOutputs = new ConcurrentHashMap<>();

        /** 累积输入 Token 总量（所有模型调用累加） */
        public final java.util.concurrent.atomic.AtomicLong totalInputTokens = new java.util.concurrent.atomic.AtomicLong(0);

        /** 累积输出 Token 总量（所有模型调用累加） */
        public final java.util.concurrent.atomic.AtomicLong totalOutputTokens = new java.util.concurrent.atomic.AtomicLong(0);
    }
}
