package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolResultTextDeltaEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import org.springframework.stereotype.Component;

/**
 * 工具结果文本增量事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ToolResultTextDeltaEvent 分支：
 * 累积结果内容片段到 recorder，并转发增量片段（前端累加得到完整 result）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class ToolResultTextDeltaHandler implements AgentEventHandler<ToolResultTextDeltaEvent> {

    @Override
    public Class<ToolResultTextDeltaEvent> getEventType() {
        return ToolResultTextDeltaEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ToolResultTextDeltaEvent tr) throws Exception {
        if (tr.getToolCallId() != null && tr.getDelta() != null) {
            // 使用 computeIfAbsent 兜底：HITL 恢复路径中可能缺少 ToolCallStartEvent，
            // 导致 toolCallResults map 中没有为该 toolCallId 初始化 StringBuilder。
            // computeIfAbsent 确保无论如何都能创建并累积 delta。
            ctx.getRecorder().toolCallResults
                    .computeIfAbsent(tr.getToolCallId(), k -> new StringBuilder())
                    .append(tr.getDelta());
        }
        ToolResultTextDeltaEventBO eventBO = ToolResultTextDeltaEventBO.builder()
                .type(AgentEventEnum.TOOL_RESULT_TEXT_DELTA.getDesc())
                .sessionId(ctx.getSessionId())
                .toolCallId(tr.getToolCallId())
                .toolName(tr.getToolCallName())
                .delta(tr.getDelta())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
