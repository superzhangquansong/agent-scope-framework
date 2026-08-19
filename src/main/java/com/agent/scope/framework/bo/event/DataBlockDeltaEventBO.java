package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 数据块增量事件 BO（对应 AgentScope {@code DataBlockDeltaEvent}）。
 * <p>多模态输出的增量 base64 编码数据，前端按 blockId 累积后解码渲染。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DataBlockDeltaEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "data_block_delta"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 回复消息 ID */
    private String replyId;
    /** 数据块唯一标识符 */
    private String blockId;
    /** 增量 base64 编码数据 */
    private String data;
}
