package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具结果文本增量事件 BO。
 * <p>
 * 对应 AgentScope {@code ToolResultTextDeltaEvent}，承载工具执行结果文本的流式增量片段，
 * 前端可按顺序累加 {@link #delta} 还原完整的工具返回内容。
 * </p>
 *
 * @author zqs
 * @title: ToolResultTextDeltaEventBO
 * @projectName agent-scope-framework
 * @date 2026/8/6 10:28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ToolResultTextDeltaEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型（tool_result_text_delta）
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
     * 工具结果增量文本
     */
    private String delta;
}
