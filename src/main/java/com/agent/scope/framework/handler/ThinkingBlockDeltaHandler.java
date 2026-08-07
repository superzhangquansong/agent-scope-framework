package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ThinkingBlockDeltaEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import org.springframework.stereotype.Component;

/**
 * 思考块增量事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ThinkingBlockDeltaEvent 分支：
 * 累积思考内容、按 replyId 累积模型调用输出、转发增量文本。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class ThinkingBlockDeltaHandler implements AgentEventHandler<ThinkingBlockDeltaEvent> {

    @Override
    public Class<ThinkingBlockDeltaEvent> getEventType() {
        return ThinkingBlockDeltaEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ThinkingBlockDeltaEvent tb) throws Exception {
        if (tb.getDelta() != null) {
            ctx.getRecorder().thinkingContent.append(tb.getDelta());
            ctx.appendModelCallOutput(tb.getReplyId(), tb.getDelta());
        }
        ThinkingBlockDeltaEventBO eventBO = ThinkingBlockDeltaEventBO.builder()
                .type(AgentEventEnum.THINKING_DELTA.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(tb.getReplyId())
                .blockId(tb.getBlockId())
                .delta(tb.getDelta())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
