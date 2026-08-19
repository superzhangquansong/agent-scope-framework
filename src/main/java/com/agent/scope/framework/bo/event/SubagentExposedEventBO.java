package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 子 Agent 暴露事件 BO（对应 AgentScope {@code SubagentExposedEvent}）。
 * <p>通过 {@code agent_spawn(expose_to_user=true)} 生成的子 Agent 被暴露为
 * 用户可寻址的入口点时推送，前端据此渲染新的会话入口。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubagentExposedEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "subagent_exposed"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 子 Agent 的唯一标识 */
    private String subagentId;
    /** 子 Agent 的 agent 类型 ID */
    private String agentId;
    /** 子 Agent 的会话 ID */
    private String subSessionId;
    /** 用户可见的标签名（可选） */
    private String label;
}
