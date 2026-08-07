package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.TextBlockEndEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.TextBlockEndEvent;
import org.springframework.stereotype.Component;

/**
 * 文本块结束事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 TextBlockEndEvent 分支：
 * 标记一次完整文本输出结束。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class TextBlockEndHandler implements AgentEventHandler<TextBlockEndEvent> {

    @Override
    public Class<TextBlockEndEvent> getEventType() {
        return TextBlockEndEvent.class;
    }

    @Override
    public void handle(EventContext ctx, TextBlockEndEvent end) throws Exception {
        TextBlockEndEventBO eventBO = TextBlockEndEventBO.builder()
                .type(AgentEventEnum.TEXT_END.getDesc())
                .sessionId(ctx.getSessionId())
                .blockId(end.getBlockId())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
