package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolResultDataDeltaEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Source;
import io.agentscope.core.message.URLSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具二进制数据增量事件处理器。
 * <p>对应 AgentScope {@link ToolResultDataDeltaEvent}：工具返回二进制数据（如图片）时
 * 推送增量。与 {@link ToolResultTextDeltaHandler}（文本增量）并列，
 * 分别处理工具结果的文本和二进制两种输出形态。</p>
 *
 * <p>{@link ToolResultDataDeltaEvent#getData()} 返回 {@link ContentBlock}（实际为 {@link DataBlock}
 * 等子类），其 {@code source} 字段为 {@link Source} 的两个子类之一：
 * <ul>
 *   <li>{@link Base64Source}：携带 {@code mediaType} 和 {@code data}（base64 编码）</li>
 *   <li>{@link URLSource}：携带 {@code url} 和 {@code mimeType}</li>
 * </ul>
 * 本处理器按类型提取字段后填充 BO，前端按 toolCallId 累积渲染。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class ToolResultDataDeltaHandler implements AgentEventHandler<ToolResultDataDeltaEvent> {

    @Override
    public Class<ToolResultDataDeltaEvent> getEventType() {
        return ToolResultDataDeltaEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ToolResultDataDeltaEvent event) throws Exception {
        // 从 ContentBlock → Source 多态提取 mediaType/data/url
        // ContentBlock 父类未定义 getSource()，实际类型为 DataBlock/ImageBlock/AudioBlock/VideoBlock
        // 等子类，均持有 source 字段；工具结果场景下通常为 DataBlock
        String mediaType = null;
        String data = null;
        String url = null;

        ContentBlock block = event.getData();
        if (block instanceof DataBlock dataBlock) {
            Source source = dataBlock.getSource();
            if (source instanceof Base64Source b64) {
                mediaType = b64.getMediaType();
                data = b64.getData();
            } else if (source instanceof URLSource urlSrc) {
                url = urlSrc.getUrl();
                mediaType = urlSrc.getMimeType();
            }
        }

        log.debug("[Handler] 工具数据增量: sessionId={}, toolCallId={}, mediaType={}, hasData={}, hasUrl={}",
                ctx.getSessionId(), event.getToolCallId(), mediaType, data != null, url != null);

        ToolResultDataDeltaEventBO eventBO = ToolResultDataDeltaEventBO.builder()
                .type(AgentEventEnum.TOOL_RESULT_DATA_DELTA.getDesc())
                .sessionId(ctx.getSessionId())
                .toolCallId(event.getToolCallId())
                .mediaType(mediaType)
                .data(data)
                .url(url)
                .build();
        ctx.sendEventBo(eventBO);
    }
}
