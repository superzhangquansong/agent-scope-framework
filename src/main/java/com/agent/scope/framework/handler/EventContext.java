package com.agent.scope.framework.handler;

import com.agent.scope.framework.service.ChatService.ChatSessionRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 事件处理上下文。
 * <p>封装各 {@link AgentEventHandler} 共享的会话级别依赖，避免处理器与 ChatService 耦合。
 * 通过值对象一次性传递，处理器不持有 ChatService 引用。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Getter
public class EventContext {

    private final SseEmitter emitter;
    private final String sessionId;
    private final String userId;
    private final ChatSessionRecorder recorder;
    private final ObjectMapper objectMapper;

    public EventContext(SseEmitter emitter, String sessionId, String userId,
                        ChatSessionRecorder recorder, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.sessionId = sessionId;
        this.userId = userId;
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    /** 发送 SSE 事件 JSON（附带 event: 头，前端按此分发回调） */
    public void sendEventBo(Object eventBO) throws Exception {
        String eventType = "unknown";
        try {
            eventType = (String) eventBO.getClass().getMethod("getType").invoke(eventBO);
        } catch (Exception ignored) {
            // 兜底：无法提取 type 时使用 unknown
        }
        emitter.send(SseEmitter.event().name(eventType).data(toJson(eventBO)));
    }

    /** 对象转 JSON 字符串 */
    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 按 {@code replyId} 累积模型调用输出片段到会话记录容器。
     * <p>替代 ChatService 中的 private appendModelCallOutput 方法，
     * 供 TextBlockDelta/ThinkingBlockDelta/ToolCallDelta 等处理器调用。</p>
     *
     * @param replyId 回复 ID
     * @param delta   增量片段
     */
    public void appendModelCallOutput(String replyId, String delta) {
        if (replyId == null || delta == null) {
            return;
        }
        StringBuilder output = recorder.modelCallOutputs.get(replyId);
        if (output != null) {
            output.append(delta);
        }
    }
}
