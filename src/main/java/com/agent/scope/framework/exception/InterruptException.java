package com.agent.scope.framework.exception;

/**
 * 中断模块异常。
 *
 * @author zqs
 * @since 2.0.0
 */
public class InterruptException extends BusinessException {

    public InterruptException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InterruptException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public InterruptException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }
}
