package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author zqs
 * @title: TextBlockDeltaEventBO
 * @projectName agent-scope-framework
 * @description:
 * @date 2026/8/6 10:28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ToolResultEndEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String type;
    private String sessionId;
    private String toolCallId;
    private String state;
}