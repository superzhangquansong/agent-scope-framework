package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具调用参数增量事件 BO。
 * <p>
 * 对应 AgentScope {@code ToolCallDeltaEvent}，承载工具调用入参的流式增量片段
 * （即 arguments JSON 的增量文本），前端可按顺序累加 {@link #delta} 还原完整入参。
 * </p>
 *
 * @author zqs
 * @title: ToolCallDeltaEventBO
 * @projectName agent-scope-framework
 * @date 2026/8/6 10:28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ToolCallDeltaEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型（tool_call_delta）
     */
    private String type;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 工具调用 ID
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具入参增量文本（arguments JSON 片段）
     */
    private String delta;
}
