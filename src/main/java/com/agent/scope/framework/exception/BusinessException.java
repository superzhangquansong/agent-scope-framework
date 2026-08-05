package com.agent.scope.framework.exception;

import com.agent.scope.framework.hdl.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>携带统一异常码的运行时异常，全局异常处理器统一捕获并转换为 {@code Result}。</p>
 *
 * <p>所有业务异常均使用本异常或其子类（如 {@link ConfigValidationException}）抛出，
 * 通过 {@link ErrorCode} 枚举携带业务码与默认消息，避免裸 {@link RuntimeException}。</p>
 *
 * @author zqs
 * @since 1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 异常码
     */
    private final ErrorCode errorCode;

    /**
     * 构造业务异常。
     *
     * @param errorCode 异常码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常（覆盖默认消息）。
     *
     * @param errorCode     异常码枚举
     * @param detailMessage 详细异常消息
     */
    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常（带原始异常）。
     *
     * @param errorCode 异常码枚举
     * @param cause     原始异常
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常（覆盖默认消息并带原始异常）。
     *
     * @param errorCode     异常码枚举
     * @param detailMessage 详细异常消息
     * @param cause         原始异常
     */
    public BusinessException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        this.errorCode = errorCode;
    }
}
