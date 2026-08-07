package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolCallStartEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ToolCallStartEvent;
import org.springframework.stereotype.Component;

/**
 * 工具调用开始事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ToolCallStartEvent 分支：
 * 记录开始时间、初始化入参/出参累积器，并通知前端正在调用哪个工具。
 * 注意：ToolCallStartEvent 不携带入参，入参通过后续 ToolCallDeltaEvent 流式推送。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class ToolCallStartHandler implements AgentEventHandler<ToolCallStartEvent> {

    @Override
    public Class<ToolCallStartEvent> getEventType() {
        return ToolCallStartEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ToolCallStartEvent tc) throws Exception {
        String toolCallId = tc.getToolCallId();
        if (toolCallId != null) {
            ctx.getRecorder().toolCallStartTimes.put(toolCallId, System.currentTimeMillis());
            ctx.getRecorder().toolCallArguments.put(toolCallId, new StringBuilder());
            ctx.getRecorder().toolCallResults.put(toolCallId, new StringBuilder());
        }
        ToolCallStartEventBO eventBO = ToolCallStartEventBO.builder()
                .type(AgentEventEnum.TOOL_CALL_START.getDesc())
                .sessionId(ctx.getSessionId())
                .toolCallId(toolCallId)
                .toolName(tc.getToolCallName())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
