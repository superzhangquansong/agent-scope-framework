package com.agent.scope.framework.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一异常编码枚举。
 * 格式：模块-序号，如 CHAT_001、PERMISSION_001、SESSION_001、INTERRUPT_001、TOOL_001、CONFIG_001。
 *
 * @author zqs
 * @since 2.0.0
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // ==================== 通用错误（GLOBAL_0xx）====================
    INTERNAL_ERROR("GLOBAL_001", "系统内部错误", 500),
    PARAM_INVALID("GLOBAL_002", "参数校验失败", 400),
    JSON_SERIALIZE_FAILED("GLOBAL_003", "JSON序列化失败", 500),
    REDIS_OPERATION_FAILED("GLOBAL_004", "Redis操作失败", 500),

    // ==================== 聊天模块（CHAT_1xx）====================
    AGENT_EXECUTION_FAILED("CHAT_001", "Agent执行异常", 500),
    SSE_SEND_FAILED("CHAT_002", "SSE事件发送失败", 500),
    SSE_EVENT_FORWARD_FAILED("CHAT_003", "SSE事件转发失败", 500),
    MESSAGE_BUILD_FAILED("CHAT_004", "消息构建失败", 500),

    // ==================== 权限模块（PERMISSION_2xx）====================
    PERMISSION_PENDING_NOT_FOUND("PERMISSION_001", "未找到待确认的权限请求，可能已超时或已被处理", 400),
    PERMISSION_RESUME_FAILED("PERMISSION_002", "权限确认恢复执行失败", 500),
    PERMISSION_ASKING_RESIDUAL("PERMISSION_003", "检测到上一轮会话残留的权限确认状态，请开启新的会话", 400),

    // ==================== 会话模块（SESSION_3xx）====================
    SESSION_CONTEXT_MISSING("SESSION_001", "会话上下文未注入到RuntimeContext", 400),
    SESSION_DESTROY_FAILED("SESSION_002", "销毁会话失败", 500),
    SESSION_LIST_FAILED("SESSION_003", "列出会话失败", 500),
    SESSION_CHECK_FAILED("SESSION_004", "检查会话存在性失败", 500),

    // ==================== 中断模块（INTERRUPT_4xx）====================
    INTERRUPT_PARAM_INVALID("INTERRUPT_001", "参数格式错误: userId应为纯数字, sessionId应为32位十六进制字符串", 400),
    INTERRUPT_FAILED("INTERRUPT_002", "中断失败", 500),

    // ==================== 工具模块（TOOL_5xx）====================
    TOOL_EXECUTION_FAILED("TOOL_001", "工具执行失败", 500),
    TOOL_ARGS_PARSE_FAILED("TOOL_002", "工具入参解析失败", 400),
    TOOL_REGISTER_FAILED("TOOL_003", "工具注册失败", 500),

    // ==================== 限流模块（RATE_5xx）====================
    RATE_LIMIT_EXCEEDED("RATE_001", "请求被限流，请稍后重试", 429),

    // ==================== 配置模块（CONFIG_6xx）====================
    CONFIG_LOAD_FAILED("CONFIG_001", "配置加载失败", 500),
    CONFIG_VERSION_SNAPSHOT_FAILED("CONFIG_002", "配置版本快照失败", 500),
    SKILL_DIR_CREATE_FAILED("CONFIG_003", "技能目录创建失败", 500),
    CONFIG_VERSION_QUERY_FAILED("CONFIG_004", "查询配置历史版本失败", 500),
    CONFIG_VERSION_ROLLBACK_FAILED("CONFIG_005", "回滚配置版本失败", 500),

    // ==================== 定时调度模块（SCHEDULER_7xx，特性38）====================
    SCHEDULER_PARAM_INVALID("SCHEDULER_001", "调度参数错误：需提供 name 且 cron/fixedRate/fixedDelay 三选一", 400),
    SCHEDULER_SCHEDULE_FAILED("SCHEDULER_002", "调度任务注册失败", 500),
    SCHEDULER_TASK_NOT_FOUND("SCHEDULER_003", "调度任务不存在或已取消", 404),

    // ==================== 任务队列模块（TASK_8xx，特性48）====================
    TASK_PARAM_INVALID("TASK_001", "任务参数错误：taskType 与 payload 不能为空", 400),
    TASK_NOT_FOUND("TASK_002", "任务不存在", 404),
    TASK_RESULT_NOT_READY("TASK_003", "任务结果未就绪", 409),

    // ==================== 技能模块（SKILL_7xx）====================
    SKILL_PROMOTION_FAILED("SKILL_001", "技能自动沉淀失败", 500),
    SKILL_GENERATE_FAILED("SKILL_002", "技能内容生成失败", 500),

    // ==================== MCP 协议模块（MCP_9xx，特性26）====================
    MCP_SERVER_MANAGER_INIT_FAILED("MCP_001", "MCP 服务器管理器初始化失败", 500),
    MCP_CLIENT_INIT_FAILED("MCP_002", "MCP 客户端初始化失败", 500),
    MCP_TOOL_REGISTER_FAILED("MCP_003", "MCP 工具注册失败", 500);

    /** 错误编码 */
    private final String code;
    /** 默认错误消息 */
    private final String message;
    /** HTTP 状态码 */
    private final int httpStatus;
}
