package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 数据块开始事件 BO（对应 AgentScope {@code DataBlockStartEvent}）。
 * <p>多模态输出（图片/音频/视频）的新数据块开始时推送，前端据此创建媒体容器。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DataBlockStartEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "data_block_start"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 回复消息 ID */
    private String replyId;
    /** 数据块唯一标识符 */
    private String blockId;
}
