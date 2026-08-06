package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型调用结束事件 BO。
 * <p>
 * 对应 AgentScope {@code ModelCallEndEvent}，标记一次 LLM 模型调用完成，
 * 携带本次调用的 Token 消耗信息。前端可据此更新 Token 用量统计与"思考结束"状态。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModelCallEndEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型（model_call_end）
     */
    private String type;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 回复 ID
     */
    private String replyId;

    /**
     * 输入 Token 数
     */
    private Integer inputTokens;

    /**
     * 输出 Token 数
     */
    private Integer outputTokens;

    /**
     * 总 Token 数
     */
    private Integer totalTokens;
}
