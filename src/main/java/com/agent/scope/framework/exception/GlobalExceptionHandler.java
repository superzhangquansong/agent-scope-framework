package com.agent.scope.framework.exception;

import com.agent.scope.framework.vo.Response;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 统一拦截 Controller 层抛出的异常，转换为标准 {@link Response} 格式返回前端。
 * 避免异常堆栈直接暴露给客户端，同时通过日志记录完整异常信息供排查。
 * </p>
 * <p>
 * 异常处理优先级：Spring 自动匹配最具体的 {@link ExceptionHandler}，
 * 因此 {@link PermissionException}（继承 {@link BusinessException}）会被其专属处理器捕获，
 * 不会落入 {@link BusinessException} 兜底处理器。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    /**
     * 处理业务异常（含会话令牌失效的特化分支）。
     *
     * <p>所有 {@link BusinessException} 均由本方法统一拦截：</p>
     * <ul>
     *   <li>SESSION_REFRESH_TOKEN_EXPIRED / SESSION_TOKEN_INVALID → 返回 HTTP 401，
     *       body.data 注入 {@code needLogin: true} 标记，前端据此弹出登录框</li>
     *   <li>其他 ErrorCode → 返回 HTTP 状态码由 ErrorCode.httpStatus 决定，
     *       data 为 null</li>
     * </ul>
     *
     * <p>使用 {@link ResponseEntity} 动态设置 HTTP 状态码，
     * 因 {@code @ResponseStatus} 是编译期固定值，无法按 ErrorCode 运行时切换。</p>
     *
     * @param e 业务异常
     * @return 动态 HTTP 状态码的统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response<Map<String, Object>>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        int httpStatus = errorCode != null ? errorCode.getHttpStatus() : HttpStatus.BAD_REQUEST.value();

        // 会话令牌失效：data 注入 needLogin 标记，前端检测此字段触发登录框
        if (errorCode == ErrorCode.SESSION_REFRESH_TOKEN_EXPIRED
                || errorCode == ErrorCode.SESSION_TOKEN_INVALID) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("needLogin", true);
            data.put("errorCode", errorCode.getCode());
            log.warn("[GlobalException] 会话令牌失效: code={}, message={}",
                    errorCode.getCode(), e.getMessage());
            Response<Map<String, Object>> body = Response.<Map<String, Object>>builder()
                    .code(httpStatus)
                    .message(e.getMessage())
                    .data(data)
                    .success(false)
                    .build();
            return ResponseEntity.status(httpStatus).body(body);
        }

        // 通用业务异常：data 为 null，HTTP 状态码由 ErrorCode 决定
        log.error("[GlobalException] 业务异常: code={}, message={}", httpStatus, e.getMessage(), e);
        Response<Map<String, Object>> body = Response.<Map<String, Object>>builder()
                .code(httpStatus)
                .message(e.getMessage())
                .data(null)
                .success(false)
                .build();
        return ResponseEntity.status(httpStatus).body(body);
    }

    /**
     * 处理权限异常。
     * <p>
     * {@link PermissionException} 继承自 {@link BusinessException}，但统一返回 403 状态码，
     * 表示当前用户无权执行该操作。
     * </p>
     *
     * @param e 权限异常
     * @return 统一错误响应（code=403）
     */
    @ExceptionHandler(PermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Response<Void> handlePermissionException(PermissionException e) {
        String message = e.getMessage();
        log.error("[GlobalException] 权限异常: message={}", message, e);
        return Response.error(HttpStatus.FORBIDDEN.value(), message);
    }

    // ==================== 参数校验异常 ====================

    /**
     * 处理请求体参数校验异常（@RequestBody + @Valid 触发）。
     * <p>
     * 拼接所有字段校验错误信息，格式：{@code field1: message1; field2: message2}。
     * </p>
     *
     * @param e 参数校验异常
     * @return 统一错误响应（code=400）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 拼接所有字段校验错误信息
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.error("[GlobalException] 参数校验失败: {}", message, e);
        return Response.error(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * 处理 Bean Validation 约束违反异常（@RequestParam / @PathVariable + @Validated 触发）。
     * <p>
     * 拼接所有约束违反信息，格式：{@code path1: message1; path2: message2}。
     * </p>
     *
     * @param e 约束违反异常
     * @return 统一错误响应（code=400）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleConstraintViolationException(ConstraintViolationException e) {
        // 拼接所有约束违反信息
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.error("[GlobalException] 约束校验失败: {}", message, e);
        return Response.error(HttpStatus.BAD_REQUEST.value(), message);
    }

    // ==================== 安全异常 ====================

    /**
     * 处理 Spring Security 访问拒绝异常。
     * <p>
     * 当已认证用户尝试访问无权限的资源时，Spring Security 抛出此异常。
     * 返回固定消息 "Access Denied"，不暴露具体权限规则。
     * </p>
     *
     * @param e 访问拒绝异常
     * @return 统一错误响应（code=403）
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Response<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.error("[GlobalException] 访问拒绝: {}", e.getMessage(), e);
        return Response.error(HttpStatus.FORBIDDEN.value(), "Access Denied");
    }

    // ==================== SSE 客户端断开异常 ====================

    /**
     * 处理 SSE 客户端断开连接异常。
     * <p>
     * 当 SSE 流式响应过程中客户端主动断开连接（如关闭页面、切换会话、超时）时，
     * Spring 抛出 {@link AsyncRequestNotUsableException}（包装了 {@link IOException} Broken pipe）。
     * </p>
     * <p>
     * <b>返回 void</b>：连接已不可用，无需也无法向客户端写入响应体。
     * 若返回 {@link Response} 对象，Spring 会尝试以 {@code text/event-stream} 内容类型序列化，
     * 导致 {@code HttpMessageNotWritableException: No converter for [class Response] with preset Content-Type 'text/event-stream'}。
     * </p>
     *
     * @param e SSE 客户端断开异常
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e) {
        log.debug("[GlobalException] SSE客户端断开连接: {}", e.getMessage());
    }

    // ==================== 兜底异常 ====================

    /**
     * 处理所有未捕获的异常（兜底）。
     * <p>
     * 返回固定消息 "Internal Server Error"，不暴露堆栈信息给客户端。
     * 完整堆栈通过 ERROR 日志记录，便于服务端排查。
     * </p>
     *
     * @param e 未捕获异常
     * @return 统一错误响应（code=500）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<Void> handleException(Exception e) {
        log.error("[GlobalException] 系统内部异常: {}", e.getMessage(), e);
        return Response.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
    }
}
