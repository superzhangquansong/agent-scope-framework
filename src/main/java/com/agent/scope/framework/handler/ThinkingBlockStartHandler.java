package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ThinkingBlockStartEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import org.springframework.stereotype.Component;

/**
 * 思考块开始事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ThinkingBlockStartEvent 分支：
 * 通知前端开始渲染推理过程。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class ThinkingBlockStartHandler implements AgentEventHandler<ThinkingBlockStartEvent> {

    @Override
    public Class<ThinkingBlockStartEvent> getEventType() {
        return ThinkingBlockStartEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ThinkingBlockStartEvent tb) throws Exception {
        ThinkingBlockStartEventBO eventBO = ThinkingBlockStartEventBO.builder()
                .type(AgentEventEnum.THINKING_START.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(tb.getReplyId())
                .blockId(tb.getBlockId())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
