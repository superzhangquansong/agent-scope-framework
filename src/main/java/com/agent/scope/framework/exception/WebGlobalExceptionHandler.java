package com.agent.scope.framework.exception;

import com.agent.scope.framework.Result;
import com.agent.scope.framework.hdl.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器。
 *
 * <p>统一捕获 Controller 层抛出的异常，转换为标准 {@link Result} 响应。
 * 异常分类：</p>
 * <ul>
 *   <li>{@link BusinessException}：业务异常，使用其携带的 {@link ErrorCode} 转换</li>
 *   <li>{@link IllegalArgumentException}：参数异常，转换为 PARAM_INVALID</li>
 *   <li>{@link AsyncRequestTimeoutException}：SSE 异步超时，返回 204，不序列化 Result</li>
 *   <li>{@link Exception}：兜底异常，转换为 INTERNAL_ERROR</li>
 * </ul>
 *
 * @author zqs
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice("com.hdl.agent.web")
public class WebGlobalExceptionHandler {

    /**
     * 业务异常处理。
     *
     * @param e 业务异常
     * @return 统一失败结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.fail(e.getErrorCode());
    }

    /**
     * 参数校验异常处理。
     *
     * @param e 参数异常
     * @return 统一失败结果
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[参数异常] {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, e.getMessage());
    }

    /**
     * 文件上传大小超限异常处理。
     *
     * <p>当用户上传的户型图超过 {@code spring.servlet.multipart.max-file-size}（默认 5MB）时，
     * Spring 抛出此异常。若不处理，前端会收到非 JSON 响应导致
     * "Failed to execute 'json' on 'Response': Unexpected end of JSON input" 报错。</p>
     *
     * @param e 上传超限异常
     * @return 统一失败结果（携带友好提示）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("[上传超限] {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, "图片大小不能超过 5MB，请压缩后重试");
    }

    /**
     * SSE 异步请求超时处理。
     * <p>
     * SSE 长连接（{@code /api/observe/events}）超时后，Spring 会抛出此异常。
     * 此时响应的 Content-Type 已经是 {@code text/event-stream}，无法用 JSON 序列化 {@link Result} 对象，
     * 否则会触发 {@code HttpMessageNotWritableException: No converter for [class Result]
     * with preset Content-Type 'text/event-stream'}。
     * </p>
     * <p>
     * 处理策略：仅记录 debug 日志，返回 204 No Content，避免破坏已建立的 SSE 响应流。
     * 前端 {@code EventSource} 会自动重连。
     * </p>
     *
     * @param e SSE 超时异常
     * @return 空响应体（HTTP 204）
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException e) {
        // SSE 超时通常意味着请求处理时间超过 5 分钟，可能是线程池满或 LLM 调用阻塞，
        // 提升为 WARN 级别确保可见，便于排查"问问题无回复"类问题
        log.warn("[SSE 超时] 请求处理超时，可能线程池满或 LLM 调用阻塞: {}", e.getMessage());
        return ResponseEntity.noContent().build();
    }

    /**
     * 未知异常兜底处理。
     *
     * @param e 未知异常
     * @return 统一失败结果
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常]", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
