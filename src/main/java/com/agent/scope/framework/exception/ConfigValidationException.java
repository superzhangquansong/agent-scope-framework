package com.agent.scope.framework.exception;


import com.agent.scope.framework.hdl.ErrorCode;

/**
 * 配置校验异常。
 *
 * <p>当 ConfigManager 加载或热重载配置时，三文件关联性校验失败抛出，
 * 表示主入口索引、子 yml、behaviors、frontend-routes 之间的引用关系不正确。</p>
 *
 * @author zqs
 * @since 1.0.0
 */
public class ConfigValidationException extends BusinessException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /**
     * 构造异常（使用 CONFIG_VALIDATION_FAILED 默认异常码）。
     *
     * @param message 异常描述
     */
    public ConfigValidationException(String message) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, message);
    }

    /**
     * 构造异常（使用 CONFIG_VALIDATION_FAILED 默认异常码并携带原始异常）。
     *
     * @param message 异常描述
     * @param cause   原始异常
     */
    public ConfigValidationException(String message, Throwable cause) {
        super(ErrorCode.CONFIG_VALIDATION_FAILED, message, cause);
    }

    /**
     * 构造异常（指定异常码，便于区分加载失败 / 文件不存在等场景）。
     *
     * @param errorCode 异常码枚举（应为 CONFIG_* 系列）
     * @param message   异常描述
     */
    public ConfigValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造异常（指定异常码并携带原始异常）。
     *
     * @param errorCode 异常码枚举（应为 CONFIG_* 系列）
     * @param message   异常描述
     * @param cause     原始异常
     */
    public ConfigValidationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
