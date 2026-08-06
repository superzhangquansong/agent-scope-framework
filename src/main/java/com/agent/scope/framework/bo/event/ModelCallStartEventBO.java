package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型调用开始事件 BO。
 * <p>
 * 对应 AgentScope {@code ModelCallStartEvent}，标记一次 LLM 模型调用的开始。
 * 前端可据此渲染"AI 正在思考..."状态，并记录调用起始时间用于耗时计算。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModelCallStartEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型（model_call_start）
     */
    private String type;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 回复 ID（同一次模型调用内共享，前端可据此关联后续 delta 事件）
     */
    private String replyId;
}
