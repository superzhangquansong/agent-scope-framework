package com.agent.scope.framework.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * 统一响应包装类。
 * <p>
 * 所有 Controller 接口返回此对象，统一前后端交互结构，便于：
 * <ul>
 *   <li>前端按固定结构解析 code/message/data，无需针对不同接口适配</li>
 *   <li>通过 requestId 串联一次请求的完整链路日志（含异步落库、链路追踪）</li>
 *   <li>通过 timestamp 标记响应生成时间，辅助排查时序问题</li>
 * </ul>
 * </p>
 *
 * @param <T> 业务数据泛型类型
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 响应码常量 ====================

    /** 成功响应码 */
    private static final int CODE_SUCCESS = 200;

    /** 默认错误响应码（兜底） */
    private static final int CODE_ERROR_DEFAULT = 500;

    /** 默认成功消息 */
    private static final String MESSAGE_SUCCESS = "success";

    // ==================== 字段 ====================

    /**
     * 业务响应码（200=成功，4xx=客户端错误，5xx=服务端错误）。
     * 与 HTTP 状态码语义保持一致，但由业务层控制，不强制等同于 HTTP 状态码。
     */
    private int code;

    /**
     * 响应消息（成功提示或错误描述），面向前端展示。
     */
    private String message;

    /**
     * 业务数据载荷，成功时填充，失败时为 null。
     */
    private T data;

    /**
     * 请求唯一标识（UUID），由服务端自动生成。
     * 用于串联一次请求的完整链路日志，便于问题排查。
     */
    private String requestId;

    /**
     * 响应生成时间戳（毫秒），由服务端自动填充。
     */
    private long timestamp;

    /**
     * 是否成功（code == 200 时为 true）。
     * <p>前端 ApiResult 接口约定字段，与 {@code code} 冗余但便于前端统一判断。</p>
     */
    private boolean success;

    // ==================== 静态工厂方法 ====================

    /**
     * 构建成功响应（含业务数据），消息默认为 "success"。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return Response 实例
     */
    public static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(CODE_SUCCESS)
                .message(MESSAGE_SUCCESS)
                .data(data)
                .success(true)
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 构建成功响应（含业务数据和自定义消息）。
     *
     * @param data    业务数据
     * @param message 自定义成功消息
     * @param <T>     数据类型
     * @return Response 实例
     */
    public static <T> Response<T> success(T data, String message) {
        return Response.<T>builder()
                .code(CODE_SUCCESS)
                .message(message)
                .data(data)
                .success(true)
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 构建成功响应（无业务数据），消息默认为 "success"。
     * <p>适用于无需返回数据的操作型接口（如删除、更新）。</p>
     *
     * @param <T> 数据类型
     * @return Response 实例，data 为 null
     */
    public static <T> Response<T> success() {
        return Response.<T>builder()
                .code(CODE_SUCCESS)
                .message(MESSAGE_SUCCESS)
                .data(null)
                .success(true)
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 构建错误响应（含自定义错误码和消息），data 为 null。
     *
     * @param code    错误码（如 400、403、500）
     * @param message 错误消息
     * @param <T>     数据类型
     * @return Response 实例，data 为 null
     */
    public static <T> Response<T> error(int code, String message) {
        return Response.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .success(false)
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 构建错误响应（仅含消息），错误码默认为 500。
     * <p>适用于未明确分类的兜底错误场景。</p>
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return Response 实例，code=500，data 为 null
     */
    public static <T> Response<T> error(String message) {
        return Response.<T>builder()
                .code(CODE_ERROR_DEFAULT)
                .message(message)
                .data(null)
                .success(false)
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
