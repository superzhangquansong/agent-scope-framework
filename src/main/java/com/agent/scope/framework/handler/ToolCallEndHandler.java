package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolCallEndEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ToolCallEndEvent;
import org.springframework.stereotype.Component;

/**
 * 工具调用参数构造完成事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ToolCallEndEvent 分支：
 * 通知前端工具调用入参构造完成（完整 arguments 需前端通过 ToolCallDeltaEvent 累加获得）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class ToolCallEndHandler implements AgentEventHandler<ToolCallEndEvent> {

    @Override
    public Class<ToolCallEndEvent> getEventType() {
        return ToolCallEndEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ToolCallEndEvent tc) throws Exception {
        ToolCallEndEventBO eventBO = ToolCallEndEventBO.builder()
                .type(AgentEventEnum.TOOL_CALL_END.getDesc())
                .sessionId(ctx.getSessionId())
                .toolCallId(tc.getToolCallId())
                .toolName(tc.getToolCallName())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
