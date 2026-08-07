package com.agent.scope.framework.exception;

/**
 * 工具模块异常。
 *
 * @author zqs
 * @since 2.0.0
 */
public class ToolException extends BusinessException {

    public ToolException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ToolException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public ToolException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }
}
