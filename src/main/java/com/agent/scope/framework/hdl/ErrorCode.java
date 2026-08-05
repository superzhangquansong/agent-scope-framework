package com.agent.scope.framework.hdl;

import lombok.Getter;

/**
 * 统一异常码枚举。
 *
 * <p>消除代码中硬编码的错误码和错误消息，所有异常统一通过枚举管理。
 * 异常码按业务域分段：</p>
 * <ul>
 *   <li>0：成功</li>
 *   <li>1xxxx：通用错误</li>
 *   <li>2xxxx：认证错误</li>
 *   <li>3xxxx：设备错误</li>
 *   <li>4xxxx：场景错误</li>
 *   <li>5xxxx：产品错误</li>
 *   <li>6xxxx：购物车错误</li>
 *   <li>7xxxx：文件错误</li>
 *   <li>8xxxx：LLM 错误</li>
 *   <li>9xxxx：基础设施错误</li>
 *   <li>10xxxx：配置错误</li>
 * </ul>
 *
 * @author zqs
 * @since 1.0.0
 */
@Getter
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "成功"),

    // ---- 通用错误 1xxxx ----
    /** 参数无效 */
    PARAM_INVALID(10001, "参数无效"),
    /** 未授权 */
    UNAUTHORIZED(10002, "未授权"),
    /** 禁止访问 */
    FORBIDDEN(10003, "禁止访问"),
    /** 资源不存在 */
    NOT_FOUND(10004, "资源不存在"),
    /** 系统内部错误 */
    INTERNAL_ERROR(10005, "系统内部错误"),
    /** 未识别到意图 */
    INTENT_NOT_FOUND(10006, "未识别到意图，请重新描述您的需求"),
    /** 操作失败 */
    OPERATION_FAILED(10007, "操作失败"),
    /** HDL 后端 API 调用失败（业务错误，需透传 HDL 返回的 msg 给用户） */
    HDL_API_ERROR(10008, "HDL 服务调用失败"),
    /** 用户未登录（会话未建立或已失效） */
    NOT_LOGGED_IN(10009, "未登录，请先登录"),

    // ---- 认证错误 2xxxx ----
    /** 登录失败 */
    LOGIN_FAILED(20001, "登录失败"),
    /** Token 无效 */
    TOKEN_INVALID(20002, "Token 无效"),
    /** Token 已过期 */
    TOKEN_EXPIRED(20003, "Token 已过期"),
    /** 用户名和密码不能为空 */
    USERNAME_PWD_EMPTY(20004, "用户名和密码不能为空"),
    /** 用户名或密码不正确 */
    USER_PWD_ERROR(20005, "用户名或密码不正确"),

    // ---- 设备错误 3xxxx ----
    /** 设备不存在 */
    DEVICE_NOT_FOUND(30001, "设备不存在"),
    /** 设备控制失败 */
    DEVICE_CONTROL_FAILED(30002, "设备控制失败"),
    /** 设备离线 */
    DEVICE_OFFLINE(30003, "设备离线"),

    // ---- 场景错误 4xxxx ----
    /** 场景不存在 */
    SCENE_NOT_FOUND(40001, "场景不存在"),
    /** 场景创建失败 */
    SCENE_CREATE_FAILED(40002, "场景创建失败"),

    // ---- 产品错误 5xxxx ----
    /** 产品不存在 */
    PRODUCT_NOT_FOUND(50001, "产品不存在"),

    // ---- 购物车错误 6xxxx ----
    /** 加入购物车失败 */
    CART_ADD_FAILED(60001, "加入购物车失败"),

    // ---- 文件错误 7xxxx ----
    /** 非法文件名 */
    FILE_NAME_INVALID(70001, "非法文件名"),
    /** 不支持的文件类型 */
    FILE_TYPE_NOT_SUPPORTED(70002, "不支持的文件类型"),
    /** 文件读写失败 */
    FILE_IO_ERROR(70003, "文件读写失败"),
    /** RAG 文档解析失败 */
    RAG_DOC_PARSE_FAILED(70004, "RAG 文档解析失败"),

    // ---- LLM 错误 8xxxx ----
    /** LLM 调用失败 */
    LLM_CALL_FAILED(80001, "LLM 调用失败"),
    /** 用户输入为空 */
    LLM_INPUT_EMPTY(80002, "用户输入为空"),
    /** Agent 配置未加载 */
    LLM_AGENT_NOT_FOUND(80003, "Agent 配置未加载"),
    /** LLM 响应解析失败 */
    LLM_RESPONSE_PARSE_FAILED(80004, "LLM 响应解析失败"),
    /** LLM 流式调用失败 */
    LLM_STREAM_FAILED(80005, "LLM 流式调用失败"),

    // ---- 基础设施错误 9xxxx ----
    /** 向量数据库写入失败 */
    INFRA_QDRANT_WRITE_FAILED(90001, "向量数据库写入失败"),
    /** 向量数据库检索失败 */
    INFRA_QDRANT_SEARCH_FAILED(90002, "向量数据库检索失败"),
    /** 文件存储上传失败 */
    INFRA_MINIO_UPLOAD_FAILED(90003, "文件存储上传失败"),
    /** 文件存储下载失败 */
    INFRA_MINIO_DOWNLOAD_FAILED(90004, "文件存储下载失败"),

    // ---- 配置错误 10xxxx ----
    /** 配置加载失败 */
    CONFIG_LOAD_FAILED(100001, "配置加载失败"),
    /** 配置校验失败 */
    CONFIG_VALIDATION_FAILED(100002, "配置校验失败"),
    /** 配置文件不存在 */
    CONFIG_FILE_NOT_FOUND(100003, "配置文件不存在");

    /** 业务码 */
    private final int code;

    /** 消息 */
    private final String message;

    /**
     * 构造异常码枚举。
     *
     * @param code    业务码
     * @param message 消息
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
