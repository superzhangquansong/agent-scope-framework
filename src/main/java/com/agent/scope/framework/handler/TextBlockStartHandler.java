package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.TextBlockStartEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.TextBlockStartEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文本块开始事件处理器。
 * <p>对应 AgentScope {@link TextBlockStartEvent}：新的文本块开始时触发。
 * 前端据此创建文本块容器，准备接收后续 {@code text_delta} 增量。</p>
 *
 * <p>与 {@link TextBlockDeltaHandler} 和 {@link TextBlockEndHandler} 配合，
 * 构成完整的 start → delta → end 文本流式事件链。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class TextBlockStartHandler implements AgentEventHandler<TextBlockStartEvent> {

    @Override
    public Class<TextBlockStartEvent> getEventType() {
        return TextBlockStartEvent.class;
    }

    @Override
    public void handle(EventContext ctx, TextBlockStartEvent event) throws Exception {
        log.debug("[Handler] 文本块开始: sessionId={}, replyId={}, blockId={}",
                ctx.getSessionId(), event.getReplyId(), event.getBlockId());

        TextBlockStartEventBO eventBO = TextBlockStartEventBO.builder()
                .type(AgentEventEnum.TEXT_START.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(event.getReplyId())
                .blockId(event.getBlockId())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
