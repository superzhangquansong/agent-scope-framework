package com.agent.scope.framework.vo;

import java.io.Serializable;

/**
 * 统一响应基类 —— BaseResponse<T>
 * <p>
 * 所有接口返回统一格式，子业务继承扩展。
 * 支持成功/失败状态、错误码、提示信息、业务数据。
 * </p>
 *
 * @param <T> 业务数据泛型
 * @author agent-scope-start
 * @since 2.0.0
 */
public class BaseResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 响应码（200=成功，500=系统异常，400=参数错误，401=未授权，403=禁止访问） */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 全链路追踪 ID */
    private String traceId;

    /** 时间戳 */
    private long timestamp;

    /** 是否成功（code == 200 时为 true） */
    private boolean success;

    /**
     * 默认构造方法
     */
    public BaseResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 全参构造方法
     *
     * @param code    响应码
     * @param message 提示信息
     * @param data    业务数据
     */
    public BaseResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = (code == 200);
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 快速构建成功响应
     *
     * @param data    业务数据
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 成功响应对象
     */
    public static <T> BaseResponse<T> success(T data, String message) {
        return new BaseResponse<>(200, message, data);
    }

    /**
     * 快速构建成功响应（默认消息）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应对象
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(200, "操作成功", data);
    }

    /**
     * 快速构建失败响应
     *
     * @param code    错误码
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 失败响应对象
     */
    public static <T> BaseResponse<T> fail(int code, String message) {
        return new BaseResponse<>(code, message, null);
    }

    /**
     * 快速构建系统异常响应
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 异常响应对象
     */
    public static <T> BaseResponse<T> error(String message) {
        return new BaseResponse<>(500, message, null);
    }

    // ==================== Getter / Setter ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
