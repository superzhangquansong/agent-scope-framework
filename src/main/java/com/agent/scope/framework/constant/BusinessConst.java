package com.agent.scope.framework.constant;

/**
 * 业务常量接口。
 * <p>
 * 集中管理所有业务常量，避免魔法值散落在代码各处。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface BusinessConst {
    String CTX_KEY_SESSION_CONTEXT = "sessionContextCxt";
    /**
     * Bearer Token 前缀
     */
    String BEARER_PREFIX = "Bearer ";

    /**
     * SSE Emitter 超时时间 5 分钟
     */
    long SSE_EMITTER_TIMEOUT = 300_000L;

    // ==================== 工具消息常量 ====================

    /** 查询设备信息成功消息 */
    String MSG_QUERY_DEVICE_SUCCESS = "成功查询到设备信息";
    /** 批量控制设备成功消息 */
    String MSG_BATCH_CONTROL_SUCCESS = "成功批量控制设备";
    /** 查询房屋列表成功消息 */
    String MSG_QUERY_HOME_SUCCESS = "成功查询到房屋列表";
    /** 查询产品信息成功消息 */
    String MSG_QUERY_PRODUCT_SUCCESS = "成功查询到产品信息";

    // ==================== HTTP 状态码常量 ====================

    /** HTTP 成功状态码 */
    int HTTP_OK = 200;
    /** HTTP 请求参数错误状态码 */
    int HTTP_BAD_REQUEST = 400;
    /** HTTP 服务器错误状态码 */
    int HTTP_INTERNAL_ERROR = 500;

    // ==================== SSE 事件类型常量 ====================

    /** SSE 事件类型：Agent 开始执行 */
    String SSE_EVENT_AGENT_START = "agent_start";
    /** SSE 事件类型：Agent 执行结束 */
    String SSE_EVENT_AGENT_END = "agent_end";
    /** SSE 事件类型：错误 */
    String SSE_EVENT_ERROR = "error";

    // ==================== 错误码常量 ====================

    /** 错误码：Agent 执行异常 */
    String ERROR_CODE_AGENT_ERROR = "AGENT_ERROR";
    /** 错误码：内部错误 */
    String ERROR_CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
}