package com.agent.scope.framework.exception;

/**
 * 会话模块异常。
 *
 * @author zqs
 * @since 2.0.0
 */
public class SessionException extends BusinessException {

    public SessionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SessionException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public SessionException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }
}
