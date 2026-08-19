package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 达到最大迭代次数事件 BO（对应 AgentScope {@code ExceedMaxItersEvent}）。
 * <p>Agent 达到 maxIters 限制时推送，前端据此提示用户"已达到最大迭代次数"。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExceedMaxItersEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "exceed_max_iters"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 回复消息 ID */
    private String replyId;
}
