package com.agent.scope.framework;

import com.agent.scope.framework.hdl.ErrorCode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一 API 响应结果 VO。
 *
 * <p>所有 Controller 和 Factory 的返回结果统一使用此对象，消除 Map&lt;String, Object&gt;
 * 和魔法字符串 key。前端兼容现有的 success / code / message / data / total 结构。</p>
 *
 * @param <T> 业务数据类型
 * @author zqs
 * @since 1.0.0
 */
@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 业务码：0=成功，其他=失败 */
    private int code;

    /** 消息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 列表总数（分页场景） */
    private Integer total;

    /**
     * 成功（无数据）。
     *
     * @param <T> 业务数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok() {
        Result<T> r = new Result<>();
        r.success = true;
        r.code = ErrorCode.SUCCESS.getCode();
        r.message = ErrorCode.SUCCESS.getMessage();
        return r;
    }

    /**
     * 成功（带数据）。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = ok();
        r.data = data;
        return r;
    }

    /**
     * 成功（带数据和总数，分页场景）。
     *
     * @param data  业务数据
     * @param total 列表总数
     * @param <T>   业务数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data, int total) {
        Result<T> r = ok(data);
        r.total = total;
        return r;
    }

    /**
     * 失败（仅消息）。
     *
     * @param message 失败消息
     * @param <T>     业务数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(String message) {
        Result<T> r = new Result<>();
        r.success = false;
        r.code = ErrorCode.INTERNAL_ERROR.getCode();
        r.message = message;
        return r;
    }

    /**
     * 失败（指定错误码和消息）。
     *
     * @param code    业务码
     * @param message 失败消息
     * @param <T>     业务数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.success = false;
        r.code = code;
        r.message = message;
        return r;
    }

    /**
     * 失败（基于异常码枚举）。
     *
     * @param errorCode 异常码枚举
     * @param <T>       业务数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        Result<T> r = new Result<>();
        r.success = false;
        r.code = errorCode.getCode();
        r.message = errorCode.getMessage();
        return r;
    }

    /**
     * 失败（基于异常码枚举并覆盖消息）。
     * <p>用于在通用异常码基础上附加具体原因描述。</p>
     *
     * @param errorCode     异常码枚举
     * @param detailMessage 详细失败消息
     * @param <T>           业务数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String detailMessage) {
        Result<T> r = new Result<>();
        r.success = false;
        r.code = errorCode.getCode();
        r.message = detailMessage;
        return r;
    }
}
