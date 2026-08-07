package com.agent.scope.framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解。
 * <p>
 * 标注在需要审计追踪的业务方法上，由 {@link com.agent.scope.framework.handler.AuditLogAspect}
 * 切面拦截并记录审计日志。适用于敏感操作场景，如：
 * <ul>
 *   <li>CONFIG_ROLLBACK —— 配置版本回滚</li>
 *   <li>SESSION_DESTROY —— 会话销毁</li>
 *   <li>TOOL_EXECUTE —— 敏感工具调用执行</li>
 * </ul>
 * </p>
 * <p>
 * 审计日志包含：操作人 ID、会话 ID、操作类型、操作目标、操作详情、
 * 请求 IP、链路 traceId，以结构化 JSON 输出。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 操作类型，标识当前方法执行的业务动作。
     * <p>建议使用大写蛇形命名，如 CONFIG_ROLLBACK、SESSION_DESTROY、TOOL_EXECUTE。</p>
     *
     * @return 操作类型字符串
     */
    String action();

    /**
     * 操作目标描述，说明本次操作作用于哪个对象或资源。
     * <p>如 "config:version:rollback"、"session:destroy" 等，可为空。</p>
     *
     * @return 操作目标描述，默认空字符串
     */
    String target() default "";
}
