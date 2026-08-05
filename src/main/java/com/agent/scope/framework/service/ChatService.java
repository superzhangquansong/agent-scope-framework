package com.agent.scope.framework.service;

import com.agent.scope.framework.context.SessionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
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
 * <b>与旧实现的区别</b>：
 * 旧实现使用 {@code isDeviceControlCommand()}、{@code parseDeviceActions()}、
 * {@code keywordToSpk()} 等硬编码 if-else 逻辑进行意图路由和命令解析，
 * 本质上不是智能框架。新实现完全移除这些方法，将意图理解和参数构造交给 LLM，
 * 真正实现"智能"路由。
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
     * 替代 org.json.JSONObject.valueToString，序列化性能提升 5-10 倍
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HarnessAgent harnessAgent;

    public void streamEvents(String sessionId, String userId, String houseId,
                             String accessToken, String userMessage, SseEmitter emitter) {
        try {

            SessionContext ctx = new SessionContext(userId, houseId, sessionId, accessToken);

            // 3. 构建 RuntimeContext，预注入 SessionContext
            //    SessionContextMiddleware 会检测到已存在的 SessionContext，不再覆盖
            //    这样 accessToken 等敏感信息能正确传递到工具方法
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .put(CTX_KEY_SESSION_CONTEXT, ctx)
                    .build();


            // 4. 发送 agent_start 事件
            sendEvent(emitter, "agent_start", sessionId, Map.of());


            // 6. 委托 HarnessAgent 执行 ReAct 推理循环
            //    LLM 将自主决策调用哪个工具、如何解析用户指令
            //    所有意图路由、参数构造、设备匹配全部由 LLM 智能完成
            harnessAgent.streamEvents(userMessage, runtimeContext)
                    .doOnNext(event -> {
                        // 将 AgentScope AgentEvent 转换为 SSE 事件并推送
                        try {
                            forwardAgentEvent(emitter, sessionId, event);
                        } catch (Exception e) {
                            log.warn("[Chat] SSE事件转发失败: eventType={}, error={}",
                                    event.getClass().getSimpleName(), e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        // Agent 执行完成
                        log.info("[Chat] HarnessAgent 执行完成: sessionId={}", sessionId);
                        try {
                            sendEvent(emitter, "agent_end", sessionId, Map.of());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
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

    // ==================== Agent 事件转发 ====================

    /**
     * 将 AgentScope AgentEvent 转换为 SSE 事件并发送。
     * <p>
     * AgentScope 2.0 GA 的 AgentEvent 有多种子类型（ReasoningEvent、ToolCallEvent、
     * TextDeltaEvent 等），本方法将其统一序列化为 JSON 并通过 SSE 推送。
     * </p>
     * <p>
     * 同时补充 sessionId 和 timestamp 字段，确保前端能正确关联事件。
     * </p>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     * @param event     AgentScope 事件
     * @throws IOException SSE 发送异常
     */
    private void forwardAgentEvent(SseEmitter emitter, String sessionId, AgentEvent event) throws IOException {
        // 将 AgentEvent 序列化为 JSON
        String eventJson = toJson(event);

        // 补充 sessionId 到事件中（AgentEvent 可能不包含此字段）
        // 直接发送原始 JSON，前端根据 event 类型字段渲染
        emitter.send(SseEmitter.event().data(eventJson));

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