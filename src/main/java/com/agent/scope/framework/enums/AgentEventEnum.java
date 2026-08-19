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
    /** 模型调用开始 */
    MODEL_CALL_START(12, "model_call_start"),
    /** 模型调用结束 */
    MODEL_CALL_END(13, "model_call_end"),
    /** 权限确认请求（HITL：需用户审批敏感工具调用） */
    PERMISSION_ASK(14, "permission_ask"),

    // ==================== 以下为补充事件（对齐 AgentScope 2.0 完整事件列表）====================

    /** 智能体开始新的回复（生命周期事件） */
    AGENT_START(15, "agent_start"),
    /** 智能体完成回复（生命周期事件） */
    AGENT_END(16, "agent_end"),
    /** 达到最大推理-执行迭代次数（生命周期事件） */
    EXCEED_MAX_ITERS(17, "exceed_max_iters"),
    /** 中间件或工具发起的提前停止请求（生命周期事件） */
    REQUEST_STOP(18, "request_stop"),
    /** 文本块开始（文本流式事件） */
    TEXT_START(19, "text_start"),
    /** 数据块开始——图片/音频/视频等多模态输出（数据流式事件） */
    DATA_BLOCK_START(20, "data_block_start"),
    /** 数据块增量——base64 编码数据（数据流式事件） */
    DATA_BLOCK_DELTA(21, "data_block_delta"),
    /** 数据块结束（数据流式事件） */
    DATA_BLOCK_END(22, "data_block_end"),
    /** 工具二进制数据输出增量（工具结果流式事件） */
    TOOL_RESULT_DATA_DELTA(23, "tool_result_data_delta"),
    /** 子 Agent 被暴露为用户可寻址的入口点（子 Agent 事件） */
    SUBAGENT_EXPOSED(24, "subagent_exposed"),
    /** 智能体暂停等待外部执行（人工介入事件） */
    REQUIRE_EXTERNAL_EXECUTION(25, "require_external_execution"),
    ;

    /** 事件编码 */
    private Integer code;

    /** 事件描述（SSE type 字段值） */
    private String desc;
}
