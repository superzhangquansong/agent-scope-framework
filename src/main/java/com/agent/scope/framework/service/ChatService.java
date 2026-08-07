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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.*;
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
            emitter.complete();
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

            // 将前端确认决策（如有）与 Redis 中的完整数据合并
            Map<String, Boolean> userDecisions = new HashMap<>();
            List<PermissionConfirmDTO.ConfirmItem> items = dto.getConfirms();
            if (items != null) {
                for (PermissionConfirmDTO.ConfirmItem item : items) {
                    userDecisions.put(item.getToolCallId(), item.isAllowed());
                }
            }

            // 当 confirms 数组为空时，通过 userMessage 自然语言检测用户意图
            // 匹配 DENY_WORDS（取消/拒绝/不要等）→ allowed=false，其他一律 allowed=true
            String userMessage = dto.getUserMessage();
            boolean naturalLanguageDeny = userMessage != null
                    && DENY_WORDS.stream().anyMatch(userMessage::contains);
            if (naturalLanguageDeny) {
                log.info("[Chat] 自然语言检测到拒绝意图: sessionId={}, userMessage={}", sessionId, userMessage);
            }

            // 构建 ConfirmResult 列表：使用 Redis 中的完整 ToolCallInfo（含 input）
            List<ConfirmResult> confirmResults = pendingInfos.stream()
                    .map(info -> {
                        ToolUseBlock toolUse = ToolUseBlock.builder()
                                .id(info.getToolCallId())
                                .name(info.getToolName())
                                .input(info.getInput())
                                .build();
                        boolean allowed = userDecisions.containsKey(info.getToolCallId())
                                ? userDecisions.get(info.getToolCallId())
                                : !naturalLanguageDeny;
                        log.info("[Chat] 权限确认决策: sessionId={}, toolCallId={}, toolName={}, allowed={}, input={}",
                                sessionId, info.getToolCallId(), info.getToolName(), allowed,
                                info.getInput() != null ? info.getInput() : "null");

                        // 不添加 ALLOW 规则：PermissionContextState 是单例 Bean，ALLOW 规则会跨会话持久化，
                        // 导致后续相同工具调用不再触发 HITL。每次设备控制都应触发权限确认。
                        return new ConfirmResult(allowed, toolUse, null);
                    })
                    .toList();

            // 清除 Redis 中的待确认缓存
            pendingConfirmationService.clearPendingConfirmations(sessionId);

            // 检查是否全部拒绝：如果用户拒绝了所有工具调用，直接返回取消消息，不恢复 Agent 执行
            // 通过遍历 pendingInfos 和 userDecisions/naturalLanguageDeny 来判断，避免访问 ConfirmResult 内部
            boolean allDenied = pendingInfos.stream().allMatch(info -> {
                boolean allowed = userDecisions.containsKey(info.getToolCallId())
                        ? userDecisions.get(info.getToolCallId())
                        : !naturalLanguageDeny;
                return !allowed;
            });
            if (allDenied) {
                log.info("[Chat] 用户拒绝了全部工具调用，直接返回取消消息: sessionId={}", sessionId);

                String cancelReply = "好的，已取消执行该操作。如果您需要其他帮助，请随时告诉我。";

                // 直接通过 SSE 返回取消回复，不恢复 Agent 执行（避免 LLM 重新发起工具调用询问）
                sendEvent(emitter, SSE_EVENT_AGENT_START, sessionId, Map.of());
                sendEvent(emitter, SSE_EVENT_AGENT_END, sessionId, Map.of(
                        "cancelled", true,
                        BusinessConst.RESPONSE_KEY_MESSAGE, cancelReply));

                // 保存用户取消消息和 Agent 取消回复到对话记录
                chatRecordService.saveUserMessage(sessionId, userId, houseId,
                        userMessage != null ? userMessage : "取消");
                chatRecordService.saveAssistantMessage(sessionId, userId, cancelReply);

                sendDone(emitter);
                emitter.complete();
                return;
            }

            // 部分拒绝场景：在 resumeMsg 中添加文本说明，让 LLM 知道哪些工具被拒绝了
            // 避免 LLM 恢复后看到之前的工具调用上下文但不知道用户已拒绝，重新发起询问
            StringBuilder textContent = new StringBuilder();
            List<ToolCallInfo> deniedInfos = pendingInfos.stream()
                    .filter(info -> {
                        boolean allowed = userDecisions.containsKey(info.getToolCallId())
                                ? userDecisions.get(info.getToolCallId())
                                : !naturalLanguageDeny;
                        return !allowed;
                    })
                    .toList();
            if (!deniedInfos.isEmpty()) {
                textContent.append("用户已拒绝以下工具调用，请不要再询问是否执行，直接告知用户已取消：\n");
                for (ToolCallInfo denied : deniedInfos) {
                    textContent.append("- 工具 ").append(denied.getToolName())
                            .append("（调用ID: ").append(denied.getToolCallId()).append("）已被用户拒绝\n");
                }
                textContent.append("对于用户同意的工具调用，请继续执行。");
            }

            // 用户确认场景：在 resumeMsg 中添加文本说明，告诉 LLM 工具已被用户授权执行
            // 避免 LLM 误判工具返回结果后重试调用（重试会再次触发 HITL，形成循环）
            List<ToolCallInfo> allowedInfos = pendingInfos.stream()
                    .filter(info -> {
                        boolean allowed = userDecisions.containsKey(info.getToolCallId())
                                ? userDecisions.get(info.getToolCallId())
                                : !naturalLanguageDeny;
                        return allowed;
                    })
                    .toList();
            if (!allowedInfos.isEmpty() && deniedInfos.isEmpty()) {
                textContent.append("用户已确认同意执行以下工具调用，请直接执行，不要重复调用已执行成功的工具：\n");
                for (ToolCallInfo allowed : allowedInfos) {
                    textContent.append("- 工具 ").append(allowed.getToolName())
                            .append("（调用ID: ").append(allowed.getToolCallId()).append("）已被用户授权\n");
                }
                textContent.append("工具执行成功后，请直接告知用户执行结果，不要重复调用。");
            }

            // 构建携带确认结果的用户消息（metadata 传 ConfirmResult + textContent 传拒绝说明）
            Msg resumeMsg = UserMessage.builder()
                    .textContent(textContent.length() > 0 ? textContent.toString() : null)
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults))
                    .build();

            log.info("[Chat] 发送权限确认恢复消息: sessionId={}, confirmCount={}, deniedCount={}",
                    sessionId, confirmResults.size(), deniedInfos.size());

            sendEvent(emitter, SSE_EVENT_AGENT_START, sessionId, Map.of());

            activeEmitters.put(sessionId, emitter);

            Disposable resumeSubscription = harnessAgent.streamEvents(resumeMsg, runtimeContext)
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

                        // 权限暂停场景：Agent 在恢复执行后再次触发 HITL（如 LLM 重试工具调用），
                        // 不发送 agent_end，保持 SSE 等待用户第二次确认
                        if (recorder.permissionPaused) {
                            log.info("[Chat] 权限恢复后再次触发 HITL 暂停: sessionId={}, 耗时={}ms",
                                    sessionId, totalDurationMs);
                            try {
                                sendEvent(emitter, SSE_EVENT_PERMISSION_PAUSED, sessionId, Map.of(
                                        "message", MSG_PERMISSION_PAUSED));
                            } catch (IOException e) {
                                log.warn("[Chat] 发送 permission_paused 失败: {}", e.getMessage());
                            }
                            sendDone(emitter);
                            emitter.complete();
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
                        sendDone(emitter);
                        emitter.complete();
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
                        sendDone(emitter);
                        emitter.complete();
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
            emitter.complete();
        }
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

            // 构建 Agent 消息：检测是否有待确认的权限请求（HITL ASKING 状态）
            Msg agentMessage = buildAgentMessageWithPermissionCheck(dto, sessionId);

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
                            log.info("[Chat] 框架优雅中断完成，AgentState 已保存: sessionId={}, 耗时={}ms",
                                    sessionId, totalDurationMs);
                            return;
                        }

                        // 权限暂停场景：Agent 因 HITL 确认而暂停，不发送 agent_end
                        if (recorder.permissionPaused) {
                            log.info("[Chat] Agent 因权限确认暂停: sessionId={}, 耗时={}ms", sessionId, totalDurationMs);
                            try {
                                sendEvent(emitter, SSE_EVENT_PERMISSION_PAUSED, sessionId, Map.of(
                                        "message", MSG_PERMISSION_PAUSED));
                            } catch (IOException e) {
                                log.warn("[Chat] 发送 permission_paused 失败: {}", e.getMessage());
                            }
                            sendDone(emitter);
                            emitter.complete();
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
                        sendDone(emitter);
                        emitter.complete();
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
                            log.warn("[Chat] 检测到残留 ASKING 状态: sessionId={}, 尝试从 Redis 恢复待确认数据", sessionId);
                            handleAskingResidualError(emitter, sessionId);
                        } else {
                            try {
                                sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                                        "error", Map.of("code", ERROR_CODE_AGENT_ERROR,
                                                "message", errMsg.isEmpty() ? MSG_AGENT_EXECUTION_ERROR : errMsg)));
                            } catch (IOException ignored) {
                            }
                        }
                        sendDone(emitter);
                        emitter.complete();
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
            emitter.complete();
        }
    }

    /**
     * 处理残留 ASKING 状态错误。
     * <p>当 AgentScope 报 "paused for human-in-the-loop confirmation" 错误时，
     * 尝试从 Redis 恢复待确认数据并重新发送 permission_ask 事件；
     * 若 Redis 中无数据，则提示用户开启新会话。</p>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     */
    private void handleAskingResidualError(SseEmitter emitter, String sessionId) {
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
                emitter.send(SseEmitter.event().data(toJson(eventBO)));

                sendEvent(emitter, SSE_EVENT_PERMISSION_PAUSED, sessionId, Map.of(
                        "message", MSG_ASKING_RESIDUAL));
            } catch (IOException e) {
                log.warn("[Chat] 重新发送 permission_ask 失败: {}", e.getMessage());
            }
        } else {
            log.warn("[Chat] Redis 中无待确认数据，需清除残留状态: sessionId={}", sessionId);
            try {
                sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                        "error", Map.of("code", ERROR_CODE_AGENT_ERROR,
                                "message", MSG_ASKING_RESIDUAL_NO_DATA)));
            } catch (IOException ignored) {
            }
        }
    }

    // ==================== Agent 消息构建（含 HITL 权限确认处理）====================

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

        String userMessage = dto.getUserMessage();
        boolean isDeny = DENY_WORDS.stream().anyMatch(userMessage::contains);
        boolean allowed = !isDeny;
        log.info("[Chat] 检测到待确认权限请求，自动处理: sessionId={}, userMessage={}, allowed={}",
                sessionId, userMessage, allowed);

        List<ConfirmResult> confirmResults = pendingTools.stream()
                .map(info -> {
                    ToolUseBlock toolUse = ToolUseBlock.builder()
                            .id(info.getToolCallId())
                            .name(info.getToolName())
                            .input(info.getInput())
                            .build();
                    log.info("[Chat] 自然语言恢复确认: sessionId={}, toolCallId={}, toolName={}, allowed={}",
                            sessionId, info.getToolCallId(), info.getToolName(), allowed);

                    // 不添加 ALLOW 规则：PermissionContextState 是单例 Bean，ALLOW 规则会跨会话持久化，
                    // 导致后续相同工具调用不再触发 HITL。每次设备控制都应触发权限确认。
                    return new ConfirmResult(allowed, toolUse, null);
                })
                .toList();

        pendingConfirmationService.clearPendingConfirmations(sessionId);

        return UserMessage.builder()
                .textContent(userMessage)
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults))
                .build();
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
            emitter.send(SseEmitter.event().data(toJson(eventBO)));
        }

        if (event instanceof AgentEndEvent) {
            log.info("[Chat] 收到 AgentEndEvent，等待 doOnComplete 关闭 SSE: sessionId={}", sessionId);
        }

        log.debug("[SSE] 转发Agent事件: sessionId={}, eventType={}",
                sessionId, event.getClass().getSimpleName());
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
        emitter.send(SseEmitter.event().data(json));
        log.debug("[SSE] 发送事件: type={}, sessionId={}", type, sessionId);
    }

    /**
     * 发送 SSE 结束标记 [DONE]。
     *
     * @param emitter SSE 发射器
     */
    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException e) {
            log.warn("[SSE] 发送 [DONE] 失败: {}", e.getMessage());
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
        emitter.complete();
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
