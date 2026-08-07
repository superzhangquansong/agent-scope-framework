package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolCallDeltaEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ToolCallDeltaEvent;
import org.springframework.stereotype.Component;

/**
 * 工具调用入参增量事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ToolCallDeltaEvent 分支：
 * 累积 arguments JSON 片段到 recorder、按 replyId 累积模型调用输出，并转发增量片段。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class ToolCallDeltaHandler implements AgentEventHandler<ToolCallDeltaEvent> {

    @Override
    public Class<ToolCallDeltaEvent> getEventType() {
        return ToolCallDeltaEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ToolCallDeltaEvent tc) throws Exception {
        if (tc.getToolCallId() != null && tc.getDelta() != null) {
            StringBuilder args = ctx.getRecorder().toolCallArguments.get(tc.getToolCallId());
            if (args != null) {
                args.append(tc.getDelta());
            }
            ctx.appendModelCallOutput(tc.getReplyId(), tc.getDelta());
        }
        ToolCallDeltaEventBO eventBO = ToolCallDeltaEventBO.builder()
                .type(AgentEventEnum.TOOL_CALL_DELTA.getDesc())
                .sessionId(ctx.getSessionId())
                .toolCallId(tc.getToolCallId())
                .toolName(tc.getToolCallName())
                .delta(tc.getDelta())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
