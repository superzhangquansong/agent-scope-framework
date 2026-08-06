package com.agent.scope.framework.service;

import com.agent.scope.framework.bo.event.*;
import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.constant.FileConst;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.dto.ChatStreamDTO;
import com.agent.scope.framework.dto.PermissionConfirmDTO;
import com.agent.scope.framework.enums.AgentEventEnum;
import com.agent.scope.framework.enums.ImageTypeEnum;
import com.agent.scope.framework.enums.MediaTypeEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.*;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
 * <b>事件处理遵循 AgentScope 2.0 官方规范</b>：
 * 使用 instanceof 按事件类型分别处理，对于流式文本片段（TextBlockDeltaEvent）
 * 只转发增量文本 getDelta()，避免对整个事件对象做全量 JSON 序列化。
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
     * Jackson ObjectMapper（线程安全，静态复用，避免每次创建）
     * <p>用于 Redis 存取待确认权限请求时的 JSON 手动序列化/反序列化，
     * 绕开 {@code GenericJackson2JsonRedisSerializer} 的 {@code activateDefaultTyping(NON_FINAL)}
     * 类型 ID 要求（该要求会导致 {@code List<ToolCallInfo>} 反序列化失败）。</p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Redis Key 前缀：待确认权限请求（HITL）。
     * <p>完整 Key 格式：{@code pending_confirm:{sessionId}}，按会话隔离。</p>
     */
    private static final String PENDING_CONFIRM_KEY_PREFIX = "pending_confirm:";

    /**
     * 待确认权限请求的 Redis TTL（分钟）。
     * <p>超时后自动清除，避免会话泄漏。30 分钟覆盖典型用户思考时间。</p>
     */
    private static final long PENDING_CONFIRM_TTL_MINUTES = 30L;

    /**
     * Redis 中待确认权限请求 JSON 的 TypeReference（用于 Jackson 反序列化）。
     */
    private static final TypeReference<List<PermissionAskEventBO.ToolCallInfo>> PENDING_CONFIRM_TYPE_REF =
            new TypeReference<>() {};

    /** 用户确认意图关键词（匹配到则视为允许执行） */
    private static final Set<String> CONFIRM_WORDS = Set.of(
            "继续", "确认", "同意", "允许", "是", "好", "好的", "可以", "执行", "ok", "yes", "y", "继续执行", "没问题"
    );

    /** 用户拒绝意图关键词（匹配到则视为拒绝执行） */
    private static final Set<String> DENY_WORDS = Set.of(
            "取消", "拒绝", "不要", "否", "不", "不行", "停止", "no", "cancel", "别"
    );

    private final HarnessAgent harnessAgent;

    private final ChatRecordService chatRecordService;

    private final AgentScopeProperties agentScopeProperties;

    /**
     * String Redis 模板（用于持久化待确认权限请求）。
     * <p>使用 {@link StringRedisTemplate}（Key/Value 均为 String 序列化），
     * 配合 {@link #OBJECT_MAPPER} 手动序列化/反序列化 JSON，彻底绕开
     * {@code GenericJackson2JsonRedisSerializer} 的类型 ID 约束问题。</p>
     * <p>Redis 中的待确认数据与 AgentScope RedisAgentStateStore 的 ASKING 状态同生命周期，
     * 服务重启后不丢失，确保"继续"等自然语言恢复消息能正确携带 {@link ConfirmResult} 元数据。</p>
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 活跃会话注册表：sessionId → (Disposable, SseEmitter)。
     * <p>
     * 用于支持运行时中断：当用户调用 /api/chat/interrupt 时，直接 dispose 对应的 Reactor 订阅，
     * 并关闭 SSE 连接。这比依赖框架的 interrupt 标志更可靠，因为 HarnessAgent 的 streamEvents
     * 产生的 Flux 与 ReActAgent.interrupt() 的中断标志可能不在同一执行上下文。
     * </p>
     */
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();

    /**
     * 中断指定会话的 Agent 执行。
     * <p>
     * 直接取消 Reactor 订阅并关闭 SSE 连接，确保 Agent 立即停止。
     * 同时调用 reActAgent 的 interrupt 作为框架级补充。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return true 如果成功中断
     */
    public boolean interruptSession(String sessionId) {
        log.info("[Chat] 中断会话: sessionId={}", sessionId);

        boolean interrupted = false;

        // 1. 取消 Reactor 订阅（直接停止 Agent 执行）
        Disposable subscription = activeSubscriptions.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            interrupted = true;
            log.info("[Chat] Reactor 订阅已取消: sessionId={}", sessionId);
        }

        // 2. 关闭 SSE 连接
        SseEmitter emitter = activeEmitters.remove(sessionId);
        if (emitter != null) {
            try {
                sendEvent(emitter, "interrupted", sessionId, Map.of(
                        "message", "用户已中断执行"));
                sendDone(emitter);
                emitter.complete();
                interrupted = true;
                log.info("[Chat] SSE 连接已关闭: sessionId={}", sessionId);
            } catch (IOException e) {
                log.warn("[Chat] 关闭 SSE 失败: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }

        if (!interrupted) {
            log.warn("[Chat] 未找到活跃会话: sessionId={}", sessionId);
        }

        return interrupted;
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

        // 会话记录容器：累积执行过程中的思考内容、最终回复、Token 消耗
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
                    .put(BusinessConst.CTX_KEY_SESSION_CONTEXT, ctx)
                    .build();

            // 始终从 Redis 加载待确认的完整数据（含 input 字段），确保 ToolUseBlock 与
            // 原始 RequireUserConfirmEvent 中的完全一致，Agent 才能匹配 ASKING 状态并恢复
            List<PermissionAskEventBO.ToolCallInfo> pendingInfos = loadPendingConfirmations(sessionId);
            if (pendingInfos.isEmpty()) {
                throw new IllegalStateException(
                        "未找到待确认的权限请求，可能已超时或已被处理: sessionId=" + sessionId);
            }

            // 将前端确认决策（如有）与 Redis 中的完整数据合并
            // 前端传入的 confirms 只含 toolCallId + allowed，需要从 Redis 补充 input 字段
            Map<String, Boolean> userDecisions = new HashMap<>();
            List<PermissionConfirmDTO.ConfirmItem> items = dto.getConfirms();
            if (items != null) {
                for (PermissionConfirmDTO.ConfirmItem item : items) {
                    userDecisions.put(item.getToolCallId(), item.isAllowed());
                }
            }

            // 构建 ConfirmResult 列表：使用 Redis 中的完整 ToolCallInfo（含 input + suggestedRules）
            // 如果前端未传入决策（confirms 为空），默认全部允许
            List<ConfirmResult> confirmResults = pendingInfos.stream()
                    .map(info -> {
                        // 重建完整的 ToolUseBlock（id + name + input），与原始 ASKING 状态匹配
                        ToolUseBlock toolUse = ToolUseBlock.builder()
                                .id(info.getToolCallId())
                                .name(info.getToolName())
                                .input(info.getInput())
                                .build();
                        // 优先使用前端显式决策，无则默认允许
                        boolean allowed = userDecisions.getOrDefault(info.getToolCallId(), true);
                        log.info("[Chat] 权限确认决策: sessionId={}, toolCallId={}, toolName={}, allowed={}, input={}",
                                sessionId, info.getToolCallId(), info.getToolName(), allowed,
                                info.getInput() != null ? info.getInput() : "null");

                        // 关键修复：使用官方文档推荐的方式 —— 传入 RequireUserConfirmEvent 中
                        // 权限系统自动生成的 suggestedRules（已序列化存储到 Redis，此处恢复）。
                        // 官方文档：new ConfirmResult(true, tc, tc.getSuggestedRules())
                        // 建议规则由权限引擎基于本次调用自动生成，引擎知道如何匹配和放行后续相同调用。
                        // 手动构造的 PermissionRule 无法被引擎正确匹配，导致恢复后二次调用仍触发 HITL。
                        List<PermissionRule> rules = toPermissionRules(info.getSuggestedRules());

                        // 安全兜底：如果 suggestedRules 为空（权限系统未生成建议规则），
                        // 且用户确认允许，则手动构造一条 ALLOW 规则
                        if (rules == null && allowed) {
                            rules = List.of(new PermissionRule(
                                    info.getToolName(),
                                    null,
                                    PermissionBehavior.ALLOW,
                                    "suggested"));
                        }

                        return new ConfirmResult(allowed, toolUse, rules);
                    })
                    .toList();

            // 清除 Redis 中的待确认缓存（已通过 API 确认，避免残留数据干扰后续自然语言确认）
            clearPendingConfirmations(sessionId);

            // 构建携带确认结果的用户消息（遵循官方示例：不设 textContent，只发 metadata）
            // 设置 textContent 会导致 LLM 当作新用户消息处理，而非恢复 ASKING 状态
            Msg resumeMsg = UserMessage.builder()
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults))
                    .build();

            log.info("[Chat] 发送权限确认恢复消息: sessionId={}, confirmCount={}", sessionId, confirmResults.size());

            sendEvent(emitter, SSE_EVENT_AGENT_START, sessionId, Map.of());

            // 注册活跃会话（支持恢复执行期间中断）
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
                        // 清理活跃会话注册
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);

                        long totalDurationMs = System.currentTimeMillis() - recorder.sessionStartTime;
                        log.info("[Chat] 权限确认后恢复执行完成: sessionId={}, 总耗时={}ms", sessionId, totalDurationMs);

                        if (recorder.finalReplyContent.length() > 0) {
                            chatRecordService.saveAssistantMessage(sessionId, userId, recorder.finalReplyContent.toString());
                        }

                        chatRecordService.saveTokenUsage(sessionId,
                                recorder.totalInputTokens.get(),
                                recorder.totalOutputTokens.get(),
                                agentScopeProperties.getModelName());

                        try {
                            sendEvent(emitter, SSE_EVENT_AGENT_END, sessionId, Map.of());
                        } catch (IOException e) {
                            log.warn("[Chat] 发送 agent_end 失败: {}", e.getMessage());
                        }
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .doOnError(err -> {
                        // 清理活跃会话注册
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);

                        log.error("[Chat] 权限确认后恢复执行异常: sessionId={}", sessionId, err);
                        try {
                            sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                                    "error", Map.of("code", ERROR_CODE_AGENT_ERROR,
                                            "message", err.getMessage() != null ? err.getMessage() : "Agent恢复执行异常")));
                        } catch (IOException ignored) {
                        }
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .subscribe();

            // 注册订阅，支持恢复执行期间被 /api/chat/interrupt 中断
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

        // 会话级别记录容器：累积思考内容、最终回复、工具调用入参/出参/开始时间、Token 消耗
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
            // 这样 accessToken 等敏感信息能正确传递到工具方法
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .put(CTX_KEY_SESSION_CONTEXT, ctx)
                    .build();

            // 异步保存用户输入消息（不阻塞主流程）
            chatRecordService.saveUserMessage(sessionId, userId, houseId, dto.getUserMessage());

            // 发送 agent_start 事件
            sendEvent(emitter, SSE_EVENT_AGENT_START, sessionId, Map.of());

            // 注册活跃会话（支持中断）
            activeEmitters.put(sessionId, emitter);

            // 构建 Agent 消息：检测是否有待确认的权限请求（HITL ASKING 状态）
            Msg agentMessage = buildAgentMessageWithPermissionCheck(dto, sessionId);

            // 委托 HarnessAgent 执行 ReAct 推理循环
            // LLM 将自主决策调用哪个工具、如何解析用户指令
            // 所有意图路由、参数构造、设备匹配全部由 LLM 智能完成
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
                        // 清理活跃会话注册
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);

                        long totalDurationMs = System.currentTimeMillis() - recorder.sessionStartTime;

                        // 权限暂停场景：Agent 因 HITL 确认而暂停，不发送 agent_end
                        // 改发 permission_paused 事件，告知前端 Agent 正在等待用户确认
                        if (recorder.permissionPaused) {
                            log.info("[Chat] Agent 因权限确认暂停: sessionId={}, 耗时={}ms", sessionId, totalDurationMs);

                            // 保存已有的回复内容（Agent 在调用工具前的说明文字）
                            if (recorder.finalReplyContent.length() > 0) {
                                chatRecordService.saveAssistantMessage(sessionId, userId, recorder.finalReplyContent.toString());
                            }

                            // 保存 Token 消耗
                            chatRecordService.saveTokenUsage(sessionId,
                                    recorder.totalInputTokens.get(),
                                    recorder.totalOutputTokens.get(),
                                    agentScopeProperties.getModelName());

                            // 发送 permission_paused 事件（替代 agent_end，表明 Agent 暂停而非结束）
                            try {
                                sendEvent(emitter, "permission_paused", sessionId, Map.of(
                                        "message", "Agent 等待权限确认，请回复\"继续\"确认或\"取消\"拒绝"));
                            } catch (IOException e) {
                                log.warn("[Chat] 发送 permission_paused 失败: {}", e.getMessage());
                            }
                            sendDone(emitter);
                            emitter.complete();
                            return;
                        }

                        // 正常完成：Agent 执行完毕（含记忆整合等后台收尾）
                        log.info("[Chat] HarnessAgent 执行完成: sessionId={}, 总耗时={}ms", sessionId, totalDurationMs);

                        // 异步保存 LLM 最终回复
                        if (recorder.finalReplyContent.length() > 0) {
                            chatRecordService.saveAssistantMessage(sessionId, userId, recorder.finalReplyContent.toString());
                        }

                        // 异步保存 Token 消耗（累积所有 ModelCallEndEvent 的 usage）
                        chatRecordService.saveTokenUsage(sessionId,
                                recorder.totalInputTokens.get(),
                                recorder.totalOutputTokens.get(),
                                agentScopeProperties.getModelName());

                        try {
                            sendEvent(emitter, SSE_EVENT_AGENT_END, sessionId, Map.of());
                        } catch (IOException e) {
                            log.warn("[Chat] 发送 agent_end 失败: {}", e.getMessage());
                        }
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .doOnError(err -> {
                        // 清理活跃会话注册
                        activeEmitters.remove(sessionId);
                        activeSubscriptions.remove(sessionId);

                        log.error("[Chat] HarnessAgent 执行异常: sessionId={}", sessionId, err);
                        String errMsg = err.getMessage() != null ? err.getMessage() : "";

                        // 检测残留 ASKING 状态错误（Redis 中有持久化状态但本次消息未携带 ConfirmResult）
                        // 触发场景：服务重启后内存缓存丢失、或会话 ID 复用导致旧状态残留
                        if (errMsg.contains("paused for human-in-the-loop confirmation")) {
                            log.warn("[Chat] 检测到残留 ASKING 状态: sessionId={}, 尝试从 Redis 恢复待确认数据", sessionId);
                            List<PermissionAskEventBO.ToolCallInfo> pending = loadPendingConfirmations(sessionId);
                            if (!pending.isEmpty()) {
                                // Redis 中有待确认数据，重新发送 permission_ask 事件
                                log.info("[Chat] 从 Redis 恢复待确认数据成功，重新发送权限确认: sessionId={}", sessionId);
                                try {
                                    PermissionAskEventBO eventBO = PermissionAskEventBO.builder()
                                            .type(AgentEventEnum.PERMISSION_ASK.getDesc())
                                            .sessionId(sessionId)
                                            .toolCalls(pending)
                                            .build();
                                    emitter.send(SseEmitter.event().data(toJson(eventBO)));

                                    sendEvent(emitter, "permission_paused", sessionId, Map.of(
                                            "message", "检测到未完成的权限确认，请回复\"继续\"确认或\"取消\"拒绝"));
                                } catch (IOException e) {
                                    log.warn("[Chat] 重新发送 permission_ask 失败: {}", e.getMessage());
                                }
                            } else {
                                // Redis 中无待确认数据，提示用户开启新会话
                                log.warn("[Chat] Redis 中无待确认数据，需清除残留状态: sessionId={}", sessionId);
                                try {
                                    sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                                            "error", Map.of("code", ERROR_CODE_AGENT_ERROR,
                                                    "message", "检测到上一轮会话残留的权限确认状态，请开启新的会话")));
                                } catch (IOException ignored) {
                                }
                            }
                        } else {
                            // 其他异常：正常错误处理
                            try {
                                sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                                        "error", Map.of("code", ERROR_CODE_AGENT_ERROR,
                                                "message", errMsg.isEmpty() ? "Agent执行异常" : errMsg)));
                            } catch (IOException ignored) {
                                // SSE 发送失败时无法再通知客户端
                            }
                        }
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .subscribe(); // 触发异步执行

            // 关键修复：将订阅存入 activeSubscriptions，供 interruptSession() 直接 dispose
            // 此前漏存导致 /api/chat/interrupt 无法取消正在运行的 Reactor Flux，Agent 继续执行
            activeSubscriptions.put(sessionId, subscription);
            log.info("[Chat] 已注册活跃订阅: sessionId={}, 可被中断", sessionId);

        } catch (Exception e) {
            log.error("[Chat] 消息处理异常: sessionId={}", sessionId, e);
            try {
                sendEvent(emitter, SSE_EVENT_ERROR, sessionId, Map.of(
                        "error", Map.of("code", ERROR_CODE_INTERNAL_ERROR, "message", e.getMessage())));
            } catch (IOException ignored) {
                // SSE 发送失败时无法再通知客户端
            }
            sendDone(emitter);
            emitter.complete();
        }
    }

    // ==================== Redis 待确认权限请求管理（HITL）====================

    /**
     * 将 AgentScope 的 {@link PermissionRule} 列表转换为可序列化的 {@link SuggestedRuleInfo} 列表。
     * <p>PermissionRule 是 AgentScope 库的 record，直接 JSON 序列化/反序列化可能因缺少无参构造器而失败，
     * 因此拆为 DTO 存储 4 个 String 字段。</p>
     *
     * @param rules 权限规则列表（来自 ToolUseBlock.getSuggestedRules()，可能为 null）
     * @return 可序列化的 DTO 列表（null 输入返回 null）
     */
    private List<PermissionAskEventBO.SuggestedRuleInfo> toSuggestedRuleInfos(List<PermissionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        return rules.stream()
                .map(rule -> PermissionAskEventBO.SuggestedRuleInfo.builder()
                        .toolName(rule.toolName())
                        .ruleContent(rule.ruleContent())
                        .behavior(rule.behavior() != null ? rule.behavior().name() : null)
                        .source(rule.source())
                        .build())
                .toList();
    }

    /**
     * 将可序列化的 {@link SuggestedRuleInfo} 列表转换回 AgentScope 的 {@link PermissionRule} 列表。
     * <p>使用 PermissionRule 的 4 参数构造函数重建：
     * {@code new PermissionRule(toolName, ruleContent, PermissionBehavior.valueOf(behavior), source)}</p>
     *
     * @param infos 可序列化 DTO 列表（可能为 null）
     * @return 权限规则列表（null 输入返回 null）
     */
    private List<PermissionRule> toPermissionRules(List<PermissionAskEventBO.SuggestedRuleInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return null;
        }
        return infos.stream()
                .map(info -> new PermissionRule(
                        info.getToolName(),
                        info.getRuleContent(),
                        info.getBehavior() != null ? PermissionBehavior.valueOf(info.getBehavior()) : PermissionBehavior.ALLOW,
                        info.getSource() != null ? info.getSource() : "suggested"))
                .toList();
    }

    /**
     * 将待确认的工具调用缓存到 Redis（替代内存 ConcurrentHashMap）。
     * <p>
     * 当 {@link RequireUserConfirmEvent} 触发时调用。将 ToolUseBlock 列表转换为
     * {@link PermissionAskEventBO.ToolCallInfo} 列表，使用 {@link #OBJECT_MAPPER} 序列化为 JSON 字符串
     * 存入 Redis，Key 为 {@code pending_confirm:{sessionId}}，TTL 30 分钟。
     * </p>
     * <p>使用 {@link StringRedisTemplate} 而非 {@code RedisTemplate<String,Object>} 的原因：
     * 后者配置的 {@code GenericJackson2JsonRedisSerializer} 启用了 {@code activateDefaultTyping(NON_FINAL)}，
     * 要求 {@code Object} 类型值携带 {@code @class} 类型 ID，会导致
     * {@code List<ToolCallInfo>} 反序列化失败（数组无类型 ID）。</p>
     * <p>使用 Redis 而非内存缓存的原因：AgentScope 的 Agent 状态通过 RedisAgentStateStore
     * 持久化到 Redis，两者必须同生命周期。内存缓存在服务重启后丢失，而 Redis 中的 ASKING 状态
     * 仍然存在，导致"继续"消息缺少 ConfirmResult 元数据而报错。</p>
     *
     * @param sessionId  会话 ID
     * @param toolCalls  待确认的 ToolUseBlock 列表（来自 RequireUserConfirmEvent）
     */
    private void cachePendingConfirmations(String sessionId, List<ToolUseBlock> toolCalls,
                                            Map<String, StringBuilder> fallbackArguments) {
        try {
            List<PermissionAskEventBO.ToolCallInfo> infos = toolCalls.stream()
                    .map(tc -> {
                        Map<String, Object> input = tc.getInput();
                        // 关键修复：RequireUserConfirmEvent 中的 ToolUseBlock.getInput() 可能返回 null
                        // 此时从 recorder 的 toolCallArguments（ToolCallDeltaEvent 累积的入参 JSON）回退
                        if (input == null || input.isEmpty()) {
                            StringBuilder argsBuilder = fallbackArguments != null ? fallbackArguments.get(tc.getId()) : null;
                            if (argsBuilder != null && argsBuilder.length() > 0) {
                                try {
                                    input = OBJECT_MAPPER.readValue(argsBuilder.toString(),
                                            new TypeReference<Map<String, Object>>() {});
                                    log.info("[Chat] ToolUseBlock.input 为空，从 recorder 回退入参: toolCallId={}, input={}",
                                            tc.getId(), input);
                                } catch (Exception e) {
                                    log.warn("[Chat] 解析回退入参失败: toolCallId={}, args={}",
                                            tc.getId(), argsBuilder, e);
                                }
                            } else {
                                log.warn("[Chat] ToolUseBlock.input 为空且无回退入参: toolCallId={}, toolName={}",
                                        tc.getId(), tc.getName());
                            }
                        }
                        // AgentScope 2.0.0 的 ToolUseBlock 不存在 getSuggestedRules() 方法
                        // （官方文档描述的 API 与 2.0.0 实际发布版本不一致）。
                        // 此处传 null，由下方安全兜底逻辑构造 ALLOW 规则。
                        List<PermissionAskEventBO.SuggestedRuleInfo> suggestedRuleInfos = null;
                        return PermissionAskEventBO.ToolCallInfo.builder()
                                .toolCallId(tc.getId())
                                .toolName(tc.getName())
                                .input(input)
                                .suggestedRules(suggestedRuleInfos)
                                .build();
                    })
                    .toList();
            String key = PENDING_CONFIRM_KEY_PREFIX + sessionId;
            String json = OBJECT_MAPPER.writeValueAsString(infos);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofMinutes(PENDING_CONFIRM_TTL_MINUTES));
            log.info("[Chat] 待确认权限请求已缓存到 Redis: sessionId={}, toolCount={}, inputs={}",
                    sessionId, infos.size(),
                    infos.stream().map(i -> i.getToolCallId() + ":" + (i.getInput() != null ? "有入参" : "无入参")).toList());
        } catch (Exception e) {
            log.error("[Chat] 缓存待确认权限请求失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 从 Redis 加载待确认的工具调用信息。
     * <p>读取 Key {@code pending_confirm:{sessionId}} 的 JSON 字符串，使用 {@link #OBJECT_MAPPER}
     * 反序列化为 {@link PermissionAskEventBO.ToolCallInfo} 列表。Key 不存在或过期时返回空列表。</p>
     *
     * @param sessionId 会话 ID
     * @return 待确认工具调用信息列表（可能为空，不会为 null）
     */
    private List<PermissionAskEventBO.ToolCallInfo> loadPendingConfirmations(String sessionId) {
        try {
            String key = PENDING_CONFIRM_KEY_PREFIX + sessionId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return OBJECT_MAPPER.readValue(json, PENDING_CONFIRM_TYPE_REF);
        } catch (Exception e) {
            log.error("[Chat] 加载待确认权限请求失败: sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 清除 Redis 中的待确认权限请求。
     * <p>在以下场景调用：
     * <ul>
     *   <li>用户通过自然语言（继续/确认）恢复后</li>
     *   <li>用户通过 /api/chat/confirm API 恢复后</li>
     *   <li>用户拒绝执行后</li>
     * </ul>
     * </p>
     *
     * @param sessionId 会话 ID
     */
    private void clearPendingConfirmations(String sessionId) {
        try {
            stringRedisTemplate.delete(PENDING_CONFIRM_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("[Chat] 清除待确认权限请求失败: sessionId={}", sessionId, e);
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
     *   <li>存在且用户消息匹配确认关键词（继续/确认/同意等）→ 构建 allowed=true 的 ConfirmResult 恢复执行</li>
     *   <li>存在且用户消息匹配拒绝关键词（取消/拒绝/不要等）→ 构建 allowed=false 的 ConfirmResult 拒绝执行</li>
     *   <li>存在但用户消息是其他内容 → 默认视为确认（用户可能直接说设备指令而非"确认"二字）</li>
     *   <li>不存在 → 普通用户消息，走正常 ReAct 流程</li>
     * </ul>
     * </p>
     *
     * @param dto       聊天请求 DTO
     * @param sessionId 会话 ID
     * @return Agent 消息（可能携带 ConfirmResult 元数据）
     */
    private Msg buildAgentMessageWithPermissionCheck(ChatStreamDTO dto, String sessionId) {
        // 从 Redis 加载待确认的工具调用（替代内存缓存，支持服务重启后恢复）
        List<PermissionAskEventBO.ToolCallInfo> pendingTools = loadPendingConfirmations(sessionId);
        if (pendingTools.isEmpty()) {
            // 无待确认权限请求，走正常消息构建
            return buildUserMessage(dto);
        }

        // 存在待确认的工具调用，检测用户意图
        String userMessage = dto.getUserMessage();
        boolean isDeny = DENY_WORDS.stream().anyMatch(userMessage::contains);

        // 拒绝意图 → allowed=false；其他所有情况（确认词或其他内容）→ allowed=true
        boolean allowed = !isDeny;
        log.info("[Chat] 检测到待确认权限请求，自动处理: sessionId={}, userMessage={}, allowed={}",
                sessionId, userMessage, allowed);

        // 从 Redis 加载的 ToolCallInfo 重建完整 ToolUseBlock（id + name + input）
        // input 字段必须携带，AgentScope 的 applyConfirmResults 会用此 ToolUseBlock 替换
        // ASKING 状态的原始 ToolUseBlock，如果 input 缺失，工具执行时将无入参
        List<ConfirmResult> confirmResults = pendingTools.stream()
                .map(info -> {
                    ToolUseBlock toolUse = ToolUseBlock.builder()
                            .id(info.getToolCallId())
                            .name(info.getToolName())
                            .input(info.getInput())
                            .build();
                    log.info("[Chat] 自然语言恢复确认: sessionId={}, toolCallId={}, toolName={}, allowed={}, input={}",
                            sessionId, info.getToolCallId(), info.getToolName(), allowed,
                            info.getInput() != null ? info.getInput() : "null");

                    // 关键修复：使用官方文档推荐的 getSuggestedRules() 恢复权限规则
                    // 建议规则由权限引擎自动生成，引擎知道如何匹配和放行后续相同调用
                    List<PermissionRule> rules = toPermissionRules(info.getSuggestedRules());

                    // 安全兜底：如果 suggestedRules 为空且用户确认允许，手动构造 ALLOW 规则
                    if (rules == null && allowed) {
                        rules = List.of(new PermissionRule(
                                info.getToolName(),
                                null,
                                PermissionBehavior.ALLOW,
                                "suggested"));
                    }

                    return new ConfirmResult(allowed, toolUse, rules);
                })
                .toList();

        // 清除 Redis 中的待确认缓存（已处理完毕）
        clearPendingConfirmations(sessionId);

        // 构建携带确认结果的用户消息，触发 Agent 从 ASKING 状态恢复
        // 注意：textContent 保留用户原始消息，因为 /stream 路径下用户可能说"继续"或直接说设备指令
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

        // 三类媒体均为空时回退纯文本消息
        boolean noMedia = (images == null || images.isEmpty())
                && (audios == null || audios.isEmpty())
                && (videos == null || videos.isEmpty());
        if (noMedia) {
            return new UserMessage(userMessage);
        }

        // 多模态消息：文本 + N 个媒体 ContentBlock
        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(TextBlock.builder().text(userMessage).build());

        // 图片块
        boolean isImageBase64 = ImageTypeEnum.BASE64.equals(dto.getImageType());
        appendMediaBlocks(blocks, images, isImageBase64, MediaKind.IMAGE);

        // 音频块
        boolean isAudioBase64 = MediaTypeEnum.BASE64.equals(dto.getAudioType());
        appendMediaBlocks(blocks, audios, isAudioBase64, MediaKind.AUDIO);

        // 视频块（多媒体）
        boolean isVideoBase64 = MediaTypeEnum.BASE64.equals(dto.getVideoType());
        appendMediaBlocks(blocks, videos, isVideoBase64, MediaKind.VIDEO);

        return new UserMessage(blocks);
    }

    /**
     * 媒体种类枚举（内部使用，区分图片/音频/视频的构造逻辑与默认 MIME）。
     */
    private enum MediaKind {
        /** 图片 */
        IMAGE,
        /** 音频 */
        AUDIO,
        /** 视频 */
        VIDEO
    }

    /**
     * 批量追加媒体 ContentBlock 到 blocks 列表。
     * <p>
     * 根据 {@code mediaKind} 构造对应 {@link ImageBlock}/{@link AudioBlock}/{@link VideoBlock}，
     * 跳过空白元素。
     * </p>
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

    /**
     * 将 AgentScope AgentEvent 转换为 SSE 事件并发送。
     * <p>
     * 遵循 AgentScope 2.0 官方文档的事件处理模式，使用 instanceof 按事件类型分别处理。
     * 同时通过 {@link ChatSessionRecorder} 累积对话全链路信息，在关键节点异步落库。
     * </p>
     * <p>
     * <b>记录时机</b>：
     * <ul>
     *   <li>{@link ThinkingBlockDeltaEvent} — 累积思考内容</li>
     *   <li>{@link ThinkingBlockEndEvent} — 保存思考过程摘要</li>
     *   <li>{@link ToolCallStartEvent} — 记录工具调用开始时间</li>
     *   <li>{@link ToolCallDeltaEvent} — 累积工具入参 arguments</li>
     *   <li>{@link ToolResultTextDeltaEvent} — 累积工具出参 result</li>
     *   <li>{@link ToolResultEndEvent} — 保存工具调用完整记录（含耗时）</li>
     *   <li>{@link TextBlockDeltaEvent} — 累积最终回复内容</li>
     *   <li>{@link ModelCallEndEvent} — 累积 Token 消耗</li>
     * </ul>
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
        // 独立处理模型调用 Start/End：记录耗时、累积 Token、保存模型调用记录、推送前端事件
        // 此处先处理，避免落入后续 else 兜底分支导致信息丢失
        if (event instanceof ModelCallStartEvent mcs) {
            handleModelCallStart(emitter, sessionId, mcs, recorder);
        } else if (event instanceof ModelCallEndEvent mce) {
            handleModelCallEnd(emitter, sessionId, mce, recorder);
        }

        // 按官方文档的 instanceof 模式分别处理各类事件
        if (event instanceof TextBlockDeltaEvent delta) {
            // 流式文本片段：只转发增量文本，构造轻量 JSON（最热点路径，必须轻量）
            if (delta.getDelta() != null) {
                recorder.finalReplyContent.append(delta.getDelta());
                // 按 replyId 累积到对应模型调用输出，用于落库 ModelCallRecord
                appendModelCallOutput(recorder, delta.getReplyId(), delta.getDelta());
            }
            TextBlockDeltaEventBO eventBO = TextBlockDeltaEventBO.builder()
                    .type(AgentEventEnum.TEXT_DELTA.getDesc())
                    .sessionId(sessionId)
                    .delta(delta.getDelta())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof TextBlockEndEvent end) {
            // 文本块完成：标记一次完整文本输出结束
            TextBlockEndEventBO eventBO = TextBlockEndEventBO.builder()
                    .type(AgentEventEnum.TEXT_END.getDesc())
                    .sessionId(sessionId)
                    .blockId(end.getBlockId())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ThinkingBlockStartEvent tb) {
            // 思考过程开始：通知前端开始渲染推理过程
            ThinkingBlockStartEventBO eventBO = ThinkingBlockStartEventBO.builder()
                    .type(AgentEventEnum.THINKING_START.getDesc())
                    .sessionId(sessionId)
                    .replyId(tb.getReplyId())
                    .blockId(tb.getBlockId())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ThinkingBlockDeltaEvent tb) {
            // 思考过程增量：转发增量文本，前端累加渲染推理过程
            if (tb.getDelta() != null) {
                recorder.thinkingContent.append(tb.getDelta());
                // 按 replyId 累积到对应模型调用输出，用于落库 ModelCallRecord
                appendModelCallOutput(recorder, tb.getReplyId(), tb.getDelta());
            }
            ThinkingBlockDeltaEventBO eventBO = ThinkingBlockDeltaEventBO.builder()
                    .type(AgentEventEnum.THINKING_DELTA.getDesc())
                    .sessionId(sessionId)
                    .replyId(tb.getReplyId())
                    .blockId(tb.getBlockId())
                    .delta(tb.getDelta())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ThinkingBlockEndEvent tb) {
            // 思考过程结束：标记一次推理过程完成
            ThinkingBlockEndEventBO eventBO = ThinkingBlockEndEventBO.builder()
                    .type(AgentEventEnum.THINKING_END.getDesc())
                    .sessionId(sessionId)
                    .replyId(tb.getReplyId())
                    .blockId(tb.getBlockId())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

            // 异步保存本次思考过程摘要（一次 ReAct 迭代可能产生多次思考，分别落库）
            if (recorder.thinkingContent.length() > 0) {
                chatRecordService.saveThinkingMessage(sessionId, userId, recorder.thinkingContent.toString());
                recorder.thinkingContent.setLength(0);
            }

        } else if (event instanceof ToolCallStartEvent tc) {
            // 工具调用开始：通知前端正在调用哪个工具
            // 注意：ToolCallStartEvent 不携带入参，入参通过后续 ToolCallDeltaEvent 流式推送
            String toolCallId = tc.getToolCallId();
            if (toolCallId != null) {
                recorder.toolCallStartTimes.put(toolCallId, System.currentTimeMillis());
                recorder.toolCallArguments.put(toolCallId, new StringBuilder());
                recorder.toolCallResults.put(toolCallId, new StringBuilder());
            }
            ToolCallStartEventBO eventBO = ToolCallStartEventBO.builder()
                    .type(AgentEventEnum.TOOL_CALL_START.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(toolCallId)
                    .toolName(tc.getToolCallName())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolCallDeltaEvent tc) {
            // 工具调用入参增量：转发 arguments JSON 片段，前端累加得到完整入参
            if (tc.getToolCallId() != null && tc.getDelta() != null) {
                StringBuilder args = recorder.toolCallArguments.get(tc.getToolCallId());
                if (args != null) {
                    args.append(tc.getDelta());
                }
                // 按 replyId 累积工具入参片段到模型调用输出，用于落库 ModelCallRecord
                appendModelCallOutput(recorder, tc.getReplyId(), tc.getDelta());
            }
            ToolCallDeltaEventBO eventBO = ToolCallDeltaEventBO.builder()
                    .type(AgentEventEnum.TOOL_CALL_DELTA.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(tc.getToolCallId())
                    .toolName(tc.getToolCallName())
                    .delta(tc.getDelta())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolCallEndEvent tc) {
            // 工具调用参数构造完成
            // 注意：完整 arguments JSON 需前端通过 ToolCallDeltaEvent 累加获得
            ToolCallEndEventBO eventBO = ToolCallEndEventBO.builder()
                    .type(AgentEventEnum.TOOL_CALL_END.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(tc.getToolCallId())
                    .toolName(tc.getToolCallName())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolResultStartEvent tr) {
            // 工具开始执行
            ToolResultStartEventBO eventBO = ToolResultStartEventBO.builder()
                    .type(AgentEventEnum.TOOL_RESULT_START.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(tr.getToolCallId())
                    .toolName(tr.getToolCallName())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolResultTextDeltaEvent tr) {
            // 工具结果文本增量：转发结果内容片段，前端累加得到完整 result
            if (tr.getToolCallId() != null && tr.getDelta() != null) {
                StringBuilder result = recorder.toolCallResults.get(tr.getToolCallId());
                if (result != null) {
                    result.append(tr.getDelta());
                }
            }
            ToolResultTextDeltaEventBO eventBO = ToolResultTextDeltaEventBO.builder()
                    .type(AgentEventEnum.TOOL_RESULT_TEXT_DELTA.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(tr.getToolCallId())
                    .toolName(tr.getToolCallName())
                    .delta(tr.getDelta())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolResultEndEvent tr) {
            // 工具执行完成：转发执行状态（SUCCESS/ERROR等）
            // 注意：完整 result 内容需前端通过 ToolResultTextDeltaEvent 累加获得
            String toolCallId = tr.getToolCallId();
            String state = tr.getState() != null ? tr.getState().name() : "UNKNOWN";
            ToolResultEndEventBO eventBO = ToolResultEndEventBO.builder()
                    .type(AgentEventEnum.TOOL_RESULT_END.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(toolCallId)
                    .toolName(tr.getToolCallName())
                    .state(state)
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

            // 异步保存工具调用完整记录（入参/出参/状态/耗时）
            saveToolCallRecord(sessionId, tr, state, recorder);

        } else if (event instanceof RequireUserConfirmEvent ruc) {
            // 特性14/15：权限 HITL — 敏感工具调用需用户确认
            // Agent 暂停执行，缓存待确认的 ToolUseBlock 到 Redis，前端展示确认界面
            // 用户可通过 /api/chat/confirm 接口或直接发"继续"/"确认"/"拒绝"等自然语言恢复

            log.info("[Chat] 权限确认请求: sessionId={}, replyId={}, toolCalls={}",
                    sessionId, ruc.getReplyId(),
                    ruc.getToolCalls().stream().map(tcb -> tcb.getName()
                            + "(input=" + (tcb.getInput() != null ? "有" : "无") + ")").toList());

            // 缓存到 Redis，传入 recorder.toolCallArguments 作为入参回退来源
            // 关键修复：RequireUserConfirmEvent 的 ToolUseBlock.getInput() 可能返回 null，
            // 需要从 recorder 累积的 ToolCallDeltaEvent 入参中回退
            cachePendingConfirmations(sessionId, ruc.getToolCalls(), recorder.toolCallArguments);

            // 标记会话为权限暂停状态，doOnComplete 时据此区分正常结束与暂停
            recorder.permissionPaused = true;

            List<PermissionAskEventBO.ToolCallInfo> toolCallInfos = ruc.getToolCalls().stream()
                    .map(tc -> {
                        Map<String, Object> input = tc.getInput();
                        if (input == null || input.isEmpty()) {
                            StringBuilder argsBuilder = recorder.toolCallArguments.get(tc.getId());
                            if (argsBuilder != null && argsBuilder.length() > 0) {
                                try {
                                    input = OBJECT_MAPPER.readValue(argsBuilder.toString(),
                                            new TypeReference<Map<String, Object>>() {});
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        return PermissionAskEventBO.ToolCallInfo.builder()
                                .toolCallId(tc.getId())
                                .toolName(tc.getName())
                                .input(input)
                                .suggestedRules(null)
                                .build();
                    })
                    .toList();

            PermissionAskEventBO eventBO = PermissionAskEventBO.builder()
                    .type(AgentEventEnum.PERMISSION_ASK.getDesc())
                    .sessionId(sessionId)
                    .replyId(ruc.getReplyId())
                    .toolCalls(toolCallInfos)
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof AgentEndEvent end) {
            // Agent 完全结束（含记忆整合后）：关闭 SSE
            // 注意：AgentEndEvent 在记忆整合之后才触发，此时 SSE 才最终关闭
            log.info("[Chat] 收到 AgentEndEvent，关闭SSE: sessionId={}", sessionId);
            // AgentEndEvent 作为最终兜底，如果前面没有提前关闭，这里关闭
            // 当前实现不提前关闭，等 AgentEndEvent 统一关闭，保证消息完整性

        } else {
            // 其他事件（AgentStartEvent/ExceedMaxItersEvent 等）
            // ModelCallStartEvent/ModelCallEndEvent 已在方法开头显式处理，不会落入此分支
            // 这些事件频率低且非热点，可全量序列化
            AgentOtherEventBO eventBO = AgentOtherEventBO.builder()
                    .type(event.getClass().getSimpleName())
                    .sessionId(sessionId)
                    .eventType(event.getType() != null ? event.getType().name() : "UNKNOWN")
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));
        }

        log.debug("[SSE] 转发Agent事件: sessionId={}, eventType={}",
                sessionId, event.getClass().getSimpleName());
    }

    /**
     * 处理模型调用开始事件。
     * <p>
     * 记录调用起始时间（按 replyId），并向前端推送 {@code model_call_start} 事件，
     * 前端可据此渲染"AI 正在思考..."状态。
     * </p>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     * @param event     模型调用开始事件
     * @param recorder  会话记录容器
     * @throws IOException SSE 发送异常
     */
    private void handleModelCallStart(SseEmitter emitter, String sessionId,
                                      ModelCallStartEvent event, ChatSessionRecorder recorder) throws IOException {
        String replyId = event.getReplyId();
        if (replyId != null) {
            recorder.modelCallStartTimes.put(replyId, System.currentTimeMillis());
            recorder.modelCallOutputs.put(replyId, new StringBuilder());
        }
        ModelCallStartEventBO eventBO = ModelCallStartEventBO.builder()
                .type(AgentEventEnum.MODEL_CALL_START.getDesc())
                .sessionId(sessionId)
                .replyId(replyId)
                .build();
        emitter.send(SseEmitter.event().data(toJson(eventBO)));
    }

    /**
     * 处理模型调用结束事件。
     * <p>
     * 累积本次调用的 Token 消耗到会话总量，异步保存单次模型调用记录
     * （输出内容 / Token / 耗时），并向前端推送 {@code model_call_end} 事件，
     * 前端可据此更新 Token 用量统计与"思考结束"状态。
     * </p>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     * @param event     模型调用结束事件
     * @param recorder  会话记录容器
     * @throws IOException SSE 发送异常
     */
    private void handleModelCallEnd(SseEmitter emitter, String sessionId,
                                    ModelCallEndEvent event, ChatSessionRecorder recorder) throws IOException {
        String replyId = event.getReplyId();
        int inputTokens = 0;
        int outputTokens = 0;
        int cachedTokens = 0;
        try {
            ChatUsage usage = event.getUsage();
            if (usage != null) {
                inputTokens = usage.getInputTokens();
                outputTokens = usage.getOutputTokens();
                cachedTokens = usage.getCachedTokens();
                // 累积到会话总量，doOnComplete 时统一保存 TokenUsageRecord
                recorder.totalInputTokens.addAndGet(inputTokens);
                recorder.totalOutputTokens.addAndGet(outputTokens);
            }
        } catch (Exception e) {
            log.debug("[Chat] 获取Token使用量失败: {}", e.getMessage());
        }

        // 计算本次模型调用耗时并取出累积输出内容
        Long startTime = replyId != null ? recorder.modelCallStartTimes.remove(replyId) : null;
        long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0L;
        StringBuilder outputBuilder = replyId != null ? recorder.modelCallOutputs.remove(replyId) : null;
        String outputContent = outputBuilder != null ? outputBuilder.toString() : null;

        // 异步保存单次模型调用记录（输出内容 / Token / 耗时 / replyId）
        chatRecordService.saveModelCall(sessionId, replyId, outputContent,
                inputTokens, outputTokens, cachedTokens, agentScopeProperties.getModelName(), durationMs);

        ModelCallEndEventBO eventBO = ModelCallEndEventBO.builder()
                .type(AgentEventEnum.MODEL_CALL_END.getDesc())
                .sessionId(sessionId)
                .replyId(replyId)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .build();
        emitter.send(SseEmitter.event().data(toJson(eventBO)));
    }

    /**
     * 按 {@code replyId} 累积模型调用输出片段到会话记录容器。
     * <p>
     * 在 {@link ModelCallStartEvent} 之后、{@link ModelCallEndEvent} 之前，
     * 所有 TextBlock/ThinkingBlock/ToolCall 的 delta 片段都归属于同一 replyId，
     * 此处统一累积，{@link #handleModelCallEnd} 时取出作为模型调用输出落库。
     * </p>
     *
     * @param recorder 会话记录容器
     * @param replyId  回复 ID
     * @param delta    增量片段
     */
    private void appendModelCallOutput(ChatSessionRecorder recorder, String replyId, String delta) {
        if (replyId == null || delta == null) {
            return;
        }
        StringBuilder output = recorder.modelCallOutputs.get(replyId);
        if (output != null) {
            output.append(delta);
        }
    }

    /**
     * 异步保存工具调用完整记录。
     * <p>
     * 从会话记录容器中取出该工具调用的开始时间、累积入参和出参，
     * 计算执行耗时后通过 {@link ChatRecordService} 异步落库。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param tr        工具执行结束事件
     * @param state     执行状态字符串
     * @param recorder  会话记录容器
     */
    private void saveToolCallRecord(String sessionId, ToolResultEndEvent tr, String state,
                                    ChatSessionRecorder recorder) {
        String toolCallId = tr.getToolCallId();
        if (toolCallId == null) {
            return;
        }
        Long startTime = recorder.toolCallStartTimes.remove(toolCallId);
        StringBuilder argsBuilder = recorder.toolCallArguments.remove(toolCallId);
        StringBuilder resultBuilder = recorder.toolCallResults.remove(toolCallId);
        String arguments = argsBuilder != null ? argsBuilder.toString() : null;
        String result = resultBuilder != null ? resultBuilder.toString() : null;
        long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0L;

        chatRecordService.saveToolCall(sessionId, toolCallId, tr.getToolCallName(),
                arguments, result, state, durationMs);
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
     * {@link ConcurrentHashMap} 和 {@link AtomicLong} 保证多事件线程安全）。
     * </p>
     *
     * @author zqs
     * @since 2.0.0
     */
    private static final class ChatSessionRecorder {

        /**
         * 会话开始时间（用于计算总耗时）
         */
        private final long sessionStartTime = System.currentTimeMillis();

        /**
         * Agent 是否因权限确认（HITL）而暂停。
         * <p>当 {@link RequireUserConfirmEvent} 触发时置为 true，{@code doOnComplete} 据此
         * 区分正常结束与权限暂停：暂停时不发送 {@code agent_end}，改发 {@code permission_paused}。</p>
         * <p>使用 volatile 保证跨线程可见性（事件发射与 doOnComplete 可能在不同线程）。</p>
         */
        private volatile boolean permissionPaused = false;

        /**
         * 当前思考块内容累积器（ThinkingBlockDeltaEvent 累加，ThinkingBlockEndEvent 落库后清空）
         */
        private final StringBuilder thinkingContent = new StringBuilder();

        /**
         * 最终回复内容累积器（TextBlockDeltaEvent 累加）
         */
        private final StringBuilder finalReplyContent = new StringBuilder();

        /**
         * 工具调用开始时间映射：toolCallId → 开始时间戳（毫秒）
         */
        private final Map<String, Long> toolCallStartTimes = new ConcurrentHashMap<>();

        /**
         * 工具调用入参累积映射：toolCallId → arguments JSON 片段累积器
         */
        private final Map<String, StringBuilder> toolCallArguments = new ConcurrentHashMap<>();

        /**
         * 工具调用出参累积映射：toolCallId → result 文本累积器
         */
        private final Map<String, StringBuilder> toolCallResults = new ConcurrentHashMap<>();

        /**
         * 模型调用开始时间映射：replyId → 开始时间戳（毫秒）
         * <p>
         * {@link ModelCallStartEvent} 时写入，{@link ModelCallEndEvent} 时取出计算耗时。
         * </p>
         */
        private final Map<String, Long> modelCallStartTimes = new ConcurrentHashMap<>();

        /**
         * 模型调用输出累积映射：replyId → 输出内容累积器
         * <p>
         * 在 ModelCallStart 与 ModelCallEnd 之间，所有 TextBlock/ThinkingBlock/ToolCall
         * 的 delta 片段按 replyId 累积，ModelCallEnd 时取出落库到 ModelCallRecord。
         * </p>
         */
        private final Map<String, StringBuilder> modelCallOutputs = new ConcurrentHashMap<>();

        /**
         * 累积输入 Token 总量（所有模型调用累加）
         */
        private final AtomicLong totalInputTokens = new AtomicLong(0);

        /**
         * 累积输出 Token 总量（所有模型调用累加）
         */
        private final AtomicLong totalOutputTokens = new AtomicLong(0);
    }
}
