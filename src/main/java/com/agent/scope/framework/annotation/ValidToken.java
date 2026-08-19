package com.agent.scope.framework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要校验 HDL Token 的 Controller 类或方法。
 *
 * <p>参考 hdl-framework 的 {@code @ValidRestAuth}，由 {@code TokenAspect} 切面拦截：
 * <ol>
 *   <li>从请求头 {@code X-Session-Token} 解析 {@link com.agent.scope.framework.model.UserSession}</li>
 *   <li>访问令牌过期 → 调用 {@link com.agent.scope.framework.hdl.HdlIotService#refreshToken} 自动刷新</li>
 *   <li>刷新令牌也过期 → 抛出 {@link com.agent.scope.framework.exception.BusinessException}
 *       （SESSION_REFRESH_TOKEN_EXPIRED），通知前端重新登录</li>
 * </ol>
 * </p>
 *
 * <p>使用方式：标注在 Controller 类上（该类所有方法生效）或单个方法上（仅该方法生效）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ValidToken {
}
