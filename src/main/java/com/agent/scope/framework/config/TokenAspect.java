package com.agent.scope.framework.config;

import com.agent.scope.framework.annotation.ValidToken;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import com.agent.scope.framework.hdl.HdlIotService;
import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Token 校验与自动刷新切面。
 *
 * <p>参考 hdl-framework 的 {@code ValidRestAspect}，拦截标注 {@link ValidToken} 的
 * Controller 类/方法，在业务逻辑执行前完成 HDL 访问令牌的过期检测与自动刷新：</p>
 *
 * <ol>
 *   <li>从请求头 {@code X-Session-Token} 解析 {@link UserSession}</li>
 *   <li>访问令牌未过期 → 直接放行</li>
 *   <li>访问令牌已过期、刷新令牌未过期 → 调用 {@link HdlIotService#refreshToken} 刷新，
 *       更新 session 后放行</li>
 *   <li>刷新令牌也过期 → 抛出 {@link BusinessException}（SESSION_REFRESH_TOKEN_EXPIRED），
 *       由 {@link com.agent.scope.framework.exception.GlobalExceptionHandler} 统一返回 401，
 *       前端据此弹出登录框</li>
 *   <li>会话不存在或未登录 → 抛出 SESSION_TOKEN_INVALID</li>
 * </ol>
 *
 * <p>{@code @Order(-30)}：在审计日志切面（{@code @Around("@annotation(auditable)")}）之前执行，
 * 确保审计日志记录的是已通过鉴权的请求。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Aspect
@Order(-30)
@Component
@RequiredArgsConstructor
public class TokenAspect {

    /** 请求头名：平台会话 Token */
    private static final String HEADER_SESSION_TOKEN = "X-Session-Token";

    private final SessionManager sessionManager;
    private final HdlIotService hdlIotService;

    /**
     * 拦截标注 {@link ValidToken} 的 Controller 方法或类。
     *
     * <p>切点表达式匹配 {@code com.agent.scope.framework.controller} 包下所有 public 方法，
     * 通过 {@code @ValidToken} 注解（类级或方法级）过滤，与 {@code ValidRestAspect} 拦截方式一致。</p>
     *
     * @param joinPoint AOP 连接点
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常或鉴权失败异常
     */
    @Around("execution(public * com.agent.scope.framework.controller..*Controller.*(..))")
    public Object validateToken(ProceedingJoinPoint joinPoint) throws Throwable {
        // 仅处理标注了 @ValidToken（方法级或类级）的请求
        if (!hasValidTokenAnnotation(joinPoint)) {
            return joinPoint.proceed();
        }

        // 1. 从请求头提取 sessionToken
        String sessionToken = extractSessionToken();
        if (sessionToken == null || sessionToken.isEmpty()) {
            log.warn("[TokenAspect] 请求未携带 X-Session-Token: method={}", getMethodName(joinPoint));
            throw new BusinessException(ErrorCode.SESSION_TOKEN_INVALID, "缺少会话令牌，请重新登录");
        }

        // 2. 加载 UserSession
        UserSession session = sessionManager.getSession(sessionToken);
        if (session == null || !session.isLoggedIn()) {
            log.warn("[TokenAspect] 会话不存在或未登录: token={}, method={}",
                    sessionToken, getMethodName(joinPoint));
            throw new BusinessException(ErrorCode.SESSION_TOKEN_INVALID, "会话已过期，请重新登录");
        }

        // 3. 访问令牌未过期 → 直接放行
        if (!session.isHdlAccessTokenExpired()) {
            return joinPoint.proceed();
        }

        // 4. 访问令牌已过期，检查刷新令牌
        log.info("[TokenAspect] 访问令牌已过期，尝试刷新: loginName={}", session.getLoginName());
        if (session.isHdlRefreshTokenExpired()) {
            // 刷新令牌也过期 → 清空会话并通知前端重新登录
            log.warn("[TokenAspect] 刷新令牌已过期，需重新登录: loginName={}", session.getLoginName());
            session.clearToken();
            sessionManager.save(session);
            throw new BusinessException(ErrorCode.SESSION_REFRESH_TOKEN_EXPIRED);
        }

        // 5. 刷新令牌未过期 → 调用 HDL 刷新接口
        HdlIotService.LoginResult refreshResult = hdlIotService.refreshToken(session);
        if (!refreshResult.isSuccess()) {
            // 刷新失败（HDL 后端拒绝）→ 通知前端重新登录
            log.warn("[TokenAspect] 刷新令牌失败: loginName={}, msg={}",
                    session.getLoginName(), refreshResult.getMsg());
            session.clearToken();
            sessionManager.save(session);
            throw new BusinessException(ErrorCode.SESSION_REFRESH_TOKEN_EXPIRED, refreshResult.getMsg());
        }

        // 6. 刷新成功 → 持久化新 session 并放行
        sessionManager.save(session);
        log.info("[TokenAspect] 令牌刷新成功，继续执行: loginName={}, method={}",
                session.getLoginName(), getMethodName(joinPoint));

        return joinPoint.proceed();
    }

    /**
     * 判断目标方法或其所在类是否标注了 {@link ValidToken}。
     *
     * @param joinPoint AOP 连接点
     * @return true 表示需要校验 Token
     */
    private boolean hasValidTokenAnnotation(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature methodSignature)) {
            return false;
        }
        Method method = methodSignature.getMethod();
        // 方法级注解优先
        if (method.isAnnotationPresent(ValidToken.class)) {
            return true;
        }
        // 类级注解兜底
        Class<?> targetClass = joinPoint.getTarget().getClass();
        return targetClass.isAnnotationPresent(ValidToken.class);
    }

    /**
     * 从当前 HTTP 请求头提取 {@code X-Session-Token}。
     *
     * @return sessionToken 值；非 HTTP 请求环境返回 null
     */
    private String extractSessionToken() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader(HEADER_SESSION_TOKEN);
    }

    /**
     * 获取目标方法名（用于日志）。
     *
     * @param joinPoint AOP 连接点
     * @return 格式：ClassName.methodName
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
    }
}
