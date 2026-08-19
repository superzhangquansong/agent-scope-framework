package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.DataBlockStartEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.DataBlockStartEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据块开始事件处理器。
 * <p>对应 AgentScope {@link DataBlockStartEvent}：多模态输出（图片/音频/视频）
 * 的新数据块开始时触发。前端据此创建媒体容器，准备接收后续 base64 增量数据。</p>
 *
 * <p>与 {@link DataBlockDeltaHandler} 和 {@link DataBlockEndHandler} 配合，
 * 构成完整的 start → delta → end 数据流式事件链。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class DataBlockStartHandler implements AgentEventHandler<DataBlockStartEvent> {

    @Override
    public Class<DataBlockStartEvent> getEventType() {
        return DataBlockStartEvent.class;
    }

    @Override
    public void handle(EventContext ctx, DataBlockStartEvent event) throws Exception {
        // DataBlockStartEvent 仅携带 replyId 和 blockId，不包含 mediaType
        // 实际的 MIME 类型在 DataBlockDeltaEvent 的 base64 数据中由前端解析
        log.info("[Handler] 数据块开始: sessionId={}, replyId={}, blockId={}",
                ctx.getSessionId(), event.getReplyId(), event.getBlockId());

        DataBlockStartEventBO eventBO = DataBlockStartEventBO.builder()
                .type(AgentEventEnum.DATA_BLOCK_START.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(event.getReplyId())
                .blockId(event.getBlockId())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
