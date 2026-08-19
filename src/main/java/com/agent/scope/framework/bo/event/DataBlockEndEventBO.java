package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 数据块结束事件 BO（对应 AgentScope {@code DataBlockEndEvent}）。
 * <p>多模态数据块完成时推送，前端据此完成媒体渲染。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DataBlockEndEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "data_block_end"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 回复消息 ID */
    private String replyId;
    /** 数据块唯一标识符 */
    private String blockId;
}
