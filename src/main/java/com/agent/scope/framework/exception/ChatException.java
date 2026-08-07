package com.agent.scope.framework.exception;

/**
 * 聊天模块异常。
 *
 * @author zqs
 * @since 2.0.0
 */
public class ChatException extends BusinessException {

    public ChatException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ChatException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public ChatException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }
}
