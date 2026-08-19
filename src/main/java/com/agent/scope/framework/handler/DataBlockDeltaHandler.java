package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.DataBlockDeltaEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.DataBlockDeltaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据块增量事件处理器。
 * <p>对应 AgentScope {@link DataBlockDeltaEvent}：多模态输出的增量 base64 编码数据。
 * 前端按 blockId 累积后解码渲染为图片/音频/视频。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class DataBlockDeltaHandler implements AgentEventHandler<DataBlockDeltaEvent> {

    @Override
    public Class<DataBlockDeltaEvent> getEventType() {
        return DataBlockDeltaEvent.class;
    }

    @Override
    public void handle(EventContext ctx, DataBlockDeltaEvent event) throws Exception {
        log.debug("[Handler] 数据块增量: sessionId={}, blockId={}, dataLen={}",
                ctx.getSessionId(), event.getBlockId(),
                event.getDelta() != null ? event.getDelta().length() : 0);

        DataBlockDeltaEventBO eventBO = DataBlockDeltaEventBO.builder()
                .type(AgentEventEnum.DATA_BLOCK_DELTA.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(event.getReplyId())
                .blockId(event.getBlockId())
                .data(event.getDelta())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
