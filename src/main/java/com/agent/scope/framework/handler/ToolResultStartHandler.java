package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolResultStartEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ToolResultStartEvent;
import org.springframework.stereotype.Component;

/**
 * 工具执行开始事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ToolResultStartEvent 分支：
 * 通知前端工具开始执行。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class ToolResultStartHandler implements AgentEventHandler<ToolResultStartEvent> {

    @Override
    public Class<ToolResultStartEvent> getEventType() {
        return ToolResultStartEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ToolResultStartEvent tr) throws Exception {
        ToolResultStartEventBO eventBO = ToolResultStartEventBO.builder()
                .type(AgentEventEnum.TOOL_RESULT_START.getDesc())
                .sessionId(ctx.getSessionId())
                .toolCallId(tr.getToolCallId())
                .toolName(tr.getToolCallName())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
