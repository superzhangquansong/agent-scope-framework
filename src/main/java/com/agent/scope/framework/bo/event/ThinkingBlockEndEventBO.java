package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 思考块结束事件 BO。
 * <p>
 * 对应 AgentScope {@code ThinkingBlockEndEvent}，标记一次推理思考过程的结束。
 * </p>
 *
 * @author zqs
 * @title: ThinkingBlockEndEventBO
 * @projectName agent-scope-framework
 * @date 2026/8/6 10:28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ThinkingBlockEndEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型（thinking_end）
     */
    private String type;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 回复 ID（同一次 Assistant 回复内共享）
     */
    private String replyId;

    /**
     * 思考块 ID
     */
    private String blockId;
}
