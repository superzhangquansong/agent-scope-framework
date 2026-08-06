package com.agent.scope.framework.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 事件类型枚举。
 * <p>
 * 描述 SSE 推送给前端的各类事件 type 字段值，与 AgentScope 2.0 事件一一对应。
 * </p>
 *
 * @author zqs
 * @description: Agent 事件类型枚举
 * @date 10:24 2026/8/6
 */
@Getter
@AllArgsConstructor
public enum AgentEventEnum {
    /** 其他兜底事件 */
    OTHER(0, "other"),
    /** 文本增量 */
    TEXT_DELTA(1, "text_delta"),
    /** 文本块结束 */
    TEXT_END(2, "text_end"),
    /** 思考块开始 */
    THINKING_START(3, "thinking_start"),
    /** 思考块增量 */
    THINKING_DELTA(4, "thinking_delta"),
    /** 思考块结束 */
    THINKING_END(5, "thinking_end"),
    /** 工具调用开始 */
    TOOL_CALL_START(6, "tool_call_start"),
    /** 工具调用参数增量 */
    TOOL_CALL_DELTA(7, "tool_call_delta"),
    /** 工具调用结束（参数构造完成） */
    TOOL_CALL_END(8, "tool_call_end"),
    /** 工具执行开始 */
    TOOL_RESULT_START(9, "tool_result_start"),
    /** 工具结果文本增量 */
    TOOL_RESULT_TEXT_DELTA(10, "tool_result_text_delta"),
    /** 工具执行结束 */
    TOOL_RESULT_END(11, "tool_result_end"),
    ;

    /** 事件编码 */
    private Integer code;

    /** 事件描述（SSE type 字段值） */
    private String desc;
}
