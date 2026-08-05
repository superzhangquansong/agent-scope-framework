package com.agent.scope.framework.service;

import com.agent.scope.framework.context.SessionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.agent.scope.framework.constant.BusinessConst.CTX_KEY_SESSION_CONTEXT;


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
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HarnessAgent harnessAgent;

    public void streamEvents(String sessionId, String userId, String houseId,
                             String accessToken, String userMessage, SseEmitter emitter) {
        try {

            SessionContext ctx = new SessionContext(userId, houseId, sessionId, accessToken);

            // 构建 RuntimeContext，预注入 SessionContext
            // SessionContextMiddleware 会检测到已存在的 SessionContext，不再覆盖
            // 这样 accessToken 等敏感信息能正确传递到工具方法
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .put(CTX_KEY_SESSION_CONTEXT, ctx)
                    .build();

            // 发送 agent_start 事件
            sendEvent(emitter, "agent_start", sessionId, Map.of());

            // 委托 HarnessAgent 执行 ReAct 推理循环
            // LLM 将自主决策调用哪个工具、如何解析用户指令
            // 所有意图路由、参数构造、设备匹配全部由 LLM 智能完成
            harnessAgent.streamEvents(userMessage, runtimeContext)
                    .doOnNext(event -> {
                        try {
                            forwardAgentEvent(emitter, sessionId, event);
                        } catch (Exception e) {
                            log.warn("[Chat] SSE事件转发失败: eventType={}, error={}",
                                    event.getClass().getSimpleName(), e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        // Agent 执行完成（含记忆整合等后台收尾）
                        log.info("[Chat] HarnessAgent 执行完成: sessionId={}", sessionId);
                        try {
                            sendEvent(emitter, "agent_end", sessionId, Map.of());
                        } catch (IOException e) {
                            log.warn("[Chat] 发送 agent_end 失败: {}", e.getMessage());
                        }
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .doOnError(err -> {
                        log.error("[Chat] HarnessAgent 执行异常: sessionId={}", sessionId, err);
                        try {
                            sendEvent(emitter, "error", sessionId, Map.of(
                                    "error", Map.of("code", "AGENT_ERROR",
                                            "message", err.getMessage() != null ? err.getMessage() : "Agent执行异常")));
                        } catch (IOException ignored) {
                            // SSE 发送失败时无法再通知客户端
                        }
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .subscribe(); // 触发异步执行

        } catch (Exception e) {
            log.error("[Chat] 消息处理异常: sessionId={}", sessionId, e);
            try {
                sendEvent(emitter, "error", sessionId, Map.of(
                        "error", Map.of("code", "INTERNAL_ERROR", "message", e.getMessage())));
            } catch (IOException ignored) {
                // SSE 发送失败时无法再通知客户端
            }
            sendDone(emitter);
            emitter.complete();
        }
    }

    // ==================== Agent 事件转发（遵循官方 instanceof 模式）====================

    /**
     * 将 AgentScope AgentEvent 转换为 SSE 事件并发送。
     * <p>
     * 遵循 AgentScope 2.0 官方文档的事件处理模式，使用 instanceof 按事件类型分别处理：
     * <ul>
     *   <li>{@link TextBlockDeltaEvent} — 流式文本片段，只转发 getDelta() 增量文本，不序列化整个事件</li>
     *   <li>{@link ToolCallStartEvent} — 工具调用开始，转发工具名</li>
     *   <li>{@link ToolResultEndEvent} — 工具执行完成，转发执行状态</li>
     *   <li>{@link AgentEndEvent} — Agent 完全结束（含记忆整合后），关闭 SSE</li>
     * </ul>
     * </p>
     * <p>
     * <b>性能优化</b>：对 TextBlockDeltaEvent 只提取 getDelta() 字符串构造轻量 JSON，
     * 避免对整个事件对象做全量序列化（每个 token 节省 5-10 倍开销）。
     * </p>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     * @param event     AgentScope 事件
     * @throws IOException SSE 发送异常
     */
    private void forwardAgentEvent(SseEmitter emitter, String sessionId, AgentEvent event) throws IOException {
        // 按官方文档的 instanceof 模式分别处理各类事件
        if (event instanceof TextBlockDeltaEvent delta) {
            // 流式文本片段：只转发增量文本，构造轻量 JSON（最热点路径，必须轻量）
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "text_delta");
            payload.put("sessionId", sessionId);
            payload.put("delta", delta.getDelta());
            emitter.send(SseEmitter.event().data(toJson(payload)));

        } else if (event instanceof TextBlockEndEvent end) {
            // 文本块完成：标记一次完整文本输出结束
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "text_end");
            payload.put("sessionId", sessionId);
            payload.put("blockId", end.getBlockId());
            emitter.send(SseEmitter.event().data(toJson(payload)));

        } else if (event instanceof ToolCallStartEvent tc) {
            // 工具调用开始：通知前端正在调用哪个工具
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "tool_call_start");
            payload.put("sessionId", sessionId);
            payload.put("toolCallId", tc.getToolCallId());
            payload.put("toolName", tc.getToolCallName());
            emitter.send(SseEmitter.event().data(toJson(payload)));

        } else if (event instanceof ToolCallEndEvent tc) {
            // 工具调用参数构造完成
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "tool_call_end");
            payload.put("sessionId", sessionId);
            payload.put("toolCallId", tc.getToolCallId());
            emitter.send(SseEmitter.event().data(toJson(payload)));

        } else if (event instanceof ToolResultStartEvent tr) {
            // 工具开始执行
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "tool_result_start");
            payload.put("sessionId", sessionId);
            payload.put("toolCallId", tr.getToolCallId());
            payload.put("toolName", tr.getToolCallName());
            emitter.send(SseEmitter.event().data(toJson(payload)));

        } else if (event instanceof ToolResultEndEvent tr) {
            // 工具执行完成：转发执行状态（SUCCESS/ERROR等）
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "tool_result_end");
            payload.put("sessionId", sessionId);
            payload.put("toolCallId", tr.getToolCallId());
            payload.put("state", tr.getState() != null ? tr.getState().name() : "UNKNOWN");
            emitter.send(SseEmitter.event().data(toJson(payload)));

        } else if (event instanceof AgentEndEvent end) {
            // Agent 完全结束（含记忆整合后）：关闭 SSE
            // 注意：AgentEndEvent 在记忆整合之后才触发，此时 SSE 才最终关闭
            log.info("[Chat] 收到 AgentEndEvent，关闭SSE: sessionId={}", sessionId);
            // AgentEndEvent 作为最终兜底，如果前面没有提前关闭，这里关闭
            // 当前实现不提前关闭，等 AgentEndEvent 统一关闭，保证消息完整性

        } else {
            // 其他事件（AgentStartEvent/ModelCallStartEvent/ThinkingBlock*等）
            // 这些事件频率低且非热点，可全量序列化
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", event.getClass().getSimpleName());
            payload.put("sessionId", sessionId);
            payload.put("eventType", event.getType() != null ? event.getType().name() : "UNKNOWN");
            emitter.send(SseEmitter.event().data(toJson(payload)));
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
}
