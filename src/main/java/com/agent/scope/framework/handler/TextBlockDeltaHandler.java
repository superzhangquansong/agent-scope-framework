package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.TextBlockDeltaEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.TextBlockDeltaEvent;
import org.springframework.stereotype.Component;

/**
 * 文本块增量事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 TextBlockDeltaEvent 分支：
 * 累积最终回复内容、按 replyId 累积模型调用输出、转发轻量增量 JSON。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class TextBlockDeltaHandler implements AgentEventHandler<TextBlockDeltaEvent> {

    @Override
    public Class<TextBlockDeltaEvent> getEventType() {
        return TextBlockDeltaEvent.class;
    }

    @Override
    public void handle(EventContext ctx, TextBlockDeltaEvent delta) throws Exception {
        if (delta.getDelta() != null) {
            ctx.getRecorder().finalReplyContent.append(delta.getDelta());
            ctx.appendModelCallOutput(delta.getReplyId(), delta.getDelta());
        }
        TextBlockDeltaEventBO eventBO = TextBlockDeltaEventBO.builder()
                .type(AgentEventEnum.TEXT_DELTA.getDesc())
                .sessionId(ctx.getSessionId())
                .delta(delta.getDelta())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
