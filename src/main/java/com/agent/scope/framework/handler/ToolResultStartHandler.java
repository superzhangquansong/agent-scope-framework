package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolResultStartEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ToolResultStartEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具执行开始事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ToolResultStartEvent 分支：
 * 通知前端工具开始执行。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class ToolResultStartHandler implements AgentEventHandler<ToolResultStartEvent> {

    @Override
    public Class<ToolResultStartEvent> getEventType() {
        return ToolResultStartEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ToolResultStartEvent tr) throws Exception {
        String toolCallId = tr.getToolCallId();

        // HITL 恢复路径兜底：恢复路径中框架跳过 reasoning 直接 acting，
        // 不会发出 ToolCallStartEvent（工具调用在第一轮 reasoning 时就产生了）。
        // 但会发出 ToolResultStartEvent / ToolResultTextDeltaEvent / ToolResultEndEvent。
        // 如果 recorder.toolCallResults 中没有为该 toolCallId 初始化 StringBuilder，
        // ToolResultTextDeltaHandler 的 get(toolCallId) 会返回 null，导致 delta 被丢弃。
        // 此处在 ToolResultStartEvent 中兜底初始化，确保 delta 能被正确累积。
        if (toolCallId != null) {
            ctx.getRecorder().toolCallResults.computeIfAbsent(toolCallId, k -> new StringBuilder());
            ctx.getRecorder().toolCallStartTimes.computeIfAbsent(toolCallId, k -> System.currentTimeMillis());
        }

        ToolResultStartEventBO eventBO = ToolResultStartEventBO.builder()
                .type(AgentEventEnum.TOOL_RESULT_START.getDesc())
                .sessionId(ctx.getSessionId())
                .toolCallId(toolCallId)
                .toolName(tr.getToolCallName())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
