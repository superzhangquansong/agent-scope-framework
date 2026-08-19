package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent 开始回复事件 BO（对应 AgentScope {@code AgentStartEvent}）。
 * <p>Agent 每次开始新的回复周期时推送，前端据此初始化回复容器。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentStartEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "agent_start"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 回复消息 ID（同一回复周期内所有事件共享） */
    private String replyId;
    /** 智能体名称 */
    private String agentName;
    /** 智能体角色（默认 "assistant"） */
    private String role;
}
