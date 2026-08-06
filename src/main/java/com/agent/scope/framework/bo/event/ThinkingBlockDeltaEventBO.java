package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 思考块增量事件 BO。
 * <p>
 * 对应 AgentScope {@code ThinkingBlockDeltaEvent}，承载思考过程的增量文本片段，
 * 前端可按顺序累加 {@link #delta} 渲染出完整的推理过程。
 * </p>
 *
 * @author zqs
 * @title: ThinkingBlockDeltaEventBO
 * @projectName agent-scope-framework
 * @date 2026/8/6 10:28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ThinkingBlockDeltaEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型（thinking_delta）
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

    /**
     * 思考增量文本
     */
    private String delta;
}
