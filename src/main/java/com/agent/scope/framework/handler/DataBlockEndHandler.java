package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.DataBlockEndEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.DataBlockEndEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据块结束事件处理器。
 * <p>对应 AgentScope {@link DataBlockEndEvent}：多模态数据块完成时触发。
 * 前端据此完成媒体渲染（如拼接完整 base64 后显示图片）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class DataBlockEndHandler implements AgentEventHandler<DataBlockEndEvent> {

    @Override
    public Class<DataBlockEndEvent> getEventType() {
        return DataBlockEndEvent.class;
    }

    @Override
    public void handle(EventContext ctx, DataBlockEndEvent event) throws Exception {
        log.info("[Handler] 数据块结束: sessionId={}, replyId={}, blockId={}",
                ctx.getSessionId(), event.getReplyId(), event.getBlockId());

        DataBlockEndEventBO eventBO = DataBlockEndEventBO.builder()
                .type(AgentEventEnum.DATA_BLOCK_END.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(event.getReplyId())
                .blockId(event.getBlockId())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
