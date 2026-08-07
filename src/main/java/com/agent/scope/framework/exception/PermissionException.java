package com.agent.scope.framework.exception;

/**
 * 权限模块异常（HITL 确认、权限恢复等）。
 *
 * @author zqs
 * @since 2.0.0
 */
public class PermissionException extends BusinessException {

    public PermissionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PermissionException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public PermissionException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }
}
