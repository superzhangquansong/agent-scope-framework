package com.agent.scope.framework.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author zqs
 * @description:
 * @date 10:24 2026/8/6
 */
@Getter
@AllArgsConstructor
public enum AgentEventEnum {
    OTHER(0, "other"),
    TEXT_DELTA(1, "text_delta"),
    TEXT_END(2, "text_end"),
    TOOL_CALL_START(3, "tool_call_start"),
    TOOL_CALL_END(4, "tool_call_end"),
    TOOL_RESULT_START(5, "tool_result_start"),
    TOOL_RESULT_END(6, "tool_result_end"),
    ;

    private Integer code;
    private String desc;
}