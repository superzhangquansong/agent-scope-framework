package com.agent.scope.framework.handler;

import com.agent.scope.framework.annotation.Auditable;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计日志 AOP 切面。
 * <p>
 * 拦截标注了 {@link Auditable} 注解的方法，在方法执行后记录结构化审计日志。
 * 适用于敏感操作追踪（配置回滚、会话销毁、工具执行等），满足企业级安全合规要求。
 * </p>
 * <p>
 * 审计日志包含以下信息（以 JSON 格式输出）：
 * <ul>
 *   <li>userId —— 当前操作人 ID（从 SecurityContextHolder 获取）</li>
 *   <li>sessionId —— 会话 ID（从方法参数中名为 sessionId 的 String 参数提取）</li>
 *   <li>action —— 操作类型（从 {@link Auditable#action()} 获取）</li>
 *   <li>target —— 操作目标（从 {@link Auditable#target()} 获取）</li>
 *   <li>detail —— 操作详情（方法返回值的 JSON 描述，异常时记录异常信息）</li>
 *   <li>ipAddress —— 请求 IP 地址（从 RequestContextHolder 获取）</li>
 *   <li>traceId —— 链路追踪 ID（从 MDC 获取，由 Micrometer Tracing 自动注入）</li>
 * </ul>
 * </p>
 * <p>
 * <b>异步记录设计</b>：
 * 审计日志通过 {@link Async} 异步记录，避免 IO 开销阻塞业务主流程。
 * 由于 Spring AOP 切面方法由框架直接调用（不经过 Spring 代理），
 * {@code @Async} 无法直接作用于 @Around 方法。
 * 因此通过 {@code @Lazy @Autowired} 自注入代理实例，
 * 在 @Around 方法中调用代理的 @Async 方法实现异步记录。
 * </p>
 * <p>
 * <b>上下文提取时机</b>：
 * SecurityContextHolder、RequestContextHolder、MDC 均为 ThreadLocal，
 * 异步线程无法继承。因此在 @Around 方法（主线程）中提取全部上下文，
 * 作为参数传递给 @Async 方法。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    /** MDC 中 traceId 的键名（与 logback-spring.xml 中 %X{traceId} 对应） */
    private static final String MDC_KEY_TRACE_ID = "traceId";

    /** 审计详情最大长度（字符），超出截断避免日志膨胀 */
    private static final int DETAIL_MAX_LENGTH = 200;

    /** 未知用户标识（SecurityContext 中无认证信息时使用） */
    private static final String UNKNOWN_USER = "anonymous";

    /** 未知 IP 标识（无法获取请求 IP 时使用） */
    private static final String UNKNOWN_IP = "unknown";

    /** Jackson ObjectMapper（线程安全，静态共享） */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 自注入代理实例。
     * <p>
     * Spring AOP 切面方法由框架直接调用，不经过 Spring 代理，
     * 因此 @Async 注解无法直接作用于切面方法。
     * 通过 {@code @Lazy @Autowired} 注入自身的 Spring 代理，
     * 调用 {@code self.logAudit(...)} 时经过代理，@Async 生效。
     * </p>
     */
    @Lazy
    @Autowired
    private AuditLogAspect self;

    /**
     * 环绕通知：拦截标注 {@link Auditable} 的方法。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>在主线程提取审计上下文（userId、sessionId、ipAddress、traceId）</li>
     *   <li>执行目标方法（proceed）</li>
     *   <li>构建操作详情（成功用返回值描述，异常用异常信息）</li>
     *   <li>异步记录审计日志</li>
     *   <li>如有异常，重新抛出（不影响业务逻辑）</li>
     * </ol>
     * </p>
     *
     * @param pjp       连接点
     * @param auditable 审计注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常（原样抛出，不吞没）
     */
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        // 在主线程提取审计上下文（ThreadLocal 在异步线程中不可用）
        String userId = extractUserId();
        String sessionId = extractSessionId(pjp);
        String ipAddress = extractIpAddress();
        String traceId = MDC.get(MDC_KEY_TRACE_ID);

        // 执行目标方法，捕获结果与异常
        Object result = null;
        Throwable thrown = null;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            thrown = e;
        }

        // 构建操作详情：成功用返回值的 JSON 描述，失败用异常类型与消息
        String detail = (thrown != null)
                ? "exception: " + thrown.getClass().getSimpleName() + ": " + thrown.getMessage()
                : buildDetail(result);

        // 异步记录审计日志（通过 self 代理调用，确保 @Async 生效）
        self.logAudit(userId, sessionId, auditable.action(), auditable.target(),
                detail, ipAddress, traceId);

        // 如果目标方法抛出异常，重新抛出（审计日志已记录，不影响业务异常传播）
        if (thrown != null) {
            throw thrown;
        }
        return result;
    }

    /**
     * 异步记录审计日志（结构化 JSON 输出）。
     * <p>
     * 通过 {@link Async} 在独立线程池中执行，不阻塞业务主流程。
     * 审计日志以 JSON 格式输出，便于 ELK/Loki 等日志系统采集与分析。
     * </p>
     * <p>
     * 此方法必须通过 Spring 代理调用（即 {@code self.logAudit(...)}），
     * 直接通过 {@code this.logAudit(...)} 调用不会触发 @Async。
     * </p>
     *
     * @param userId    操作人 ID
     * @param sessionId 会话 ID（可能为 null）
     * @param action    操作类型
     * @param target    操作目标描述
     * @param detail    操作详情（返回值或异常信息）
     * @param ipAddress 请求 IP 地址
     * @param traceId   链路追踪 ID
     */
    @Async
    public void logAudit(String userId, String sessionId, String action, String target,
                         String detail, String ipAddress, String traceId) {
        try {
            // 使用 LinkedHashMap 保持 JSON 字段顺序
            Map<String, Object> auditLog = new LinkedHashMap<>();
            auditLog.put("userId", userId);
            auditLog.put("sessionId", sessionId);
            auditLog.put("action", action);
            auditLog.put("target", target);
            auditLog.put("detail", detail);
            auditLog.put("ipAddress", ipAddress);
            auditLog.put("traceId", traceId);

            String jsonLog = objectMapper.writeValueAsString(auditLog);
            log.info("[AUDIT] {}", jsonLog);
        } catch (Exception e) {
            // 审计日志记录失败不应影响业务，降级为普通日志输出
            log.warn("[AuditLogAspect] 审计日志序列化失败: userId={}, action={}, error={}",
                    userId, action, e.getMessage());
        }
    }

    /**
     * 从 {@link SecurityContextHolder} 提取当前操作人 ID。
     * <p>
     * API Key 认证模式下，Principal 为固定标识 "api-client"。
     * 未认证或 SecurityContext 为空时返回 "anonymous"。
     * </p>
     *
     * @return 操作人 ID，未认证时返回 "anonymous"
     */
    private String extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("[AuditLogAspect] 提取 userId 失败: {}", e.getMessage());
        }
        return UNKNOWN_USER;
    }

    /**
     * 从方法参数中提取 sessionId。
     * <p>
     * 遍历目标方法参数，找到类型为 String 且参数名为 "sessionId" 的参数值。
     * 依赖编译时 -parameters 选项（pom.xml 已配置），否则参数名为 null。
     * </p>
     *
     * @param pjp 连接点
     * @return sessionId 值，未找到时返回 null
     */
    private String extractSessionId(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] paramNames = signature.getParameterNames();
        Class<?>[] paramTypes = signature.getParameterTypes();
        Object[] args = pjp.getArgs();

        if (paramNames == null) {
            return null;
        }

        for (int i = 0; i < paramNames.length; i++) {
            if ("sessionId".equals(paramNames[i])
                    && paramTypes[i] == String.class
                    && i < args.length
                    && args[i] != null) {
                return (String) args[i];
            }
        }
        return null;
    }

    /**
     * 从 {@link RequestContextHolder} 提取请求 IP 地址。
     * <p>
     * 依次检查以下来源（适配反向代理场景）：
     * <ol>
     *   <li>X-Forwarded-For 请求头（取第一个 IP，可能含多个）</li>
     *   <li>X-Real-IP 请求头</li>
     *   <li>HttpServletRequest.getRemoteAddr()</li>
     * </ol>
     * </p>
     *
     * @return 请求 IP 地址，无法获取时返回 "unknown"
     */
    private String extractIpAddress() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();

            // 优先从 X-Forwarded-For 获取（反向代理场景）
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                // 其次从 X-Real-IP 获取
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                // 最后从 RemoteAddr 获取
                ip = request.getRemoteAddr();
            }
            // X-Forwarded-For 可能包含多个 IP（客户端 → 代理1 → 代理2），取第一个
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip != null && !ip.isEmpty() ? ip : UNKNOWN_IP;
        } catch (Exception e) {
            log.debug("[AuditLogAspect] 提取 IP 地址失败: {}", e.getMessage());
            return UNKNOWN_IP;
        }
    }

    /**
     * 构建操作详情（方法返回值的简短描述）。
     * <p>
     * 使用 Jackson ObjectMapper 将返回值序列化为 JSON 字符串，
     * 超过 {@link #DETAIL_MAX_LENGTH} 字符时截断并添加 "(truncated)" 标记。
     * 序列化失败时降级为 toString()。
     * </p>
     *
     * @param result 方法返回值
     * @return 返回值的 JSON 描述，null 返回值返回 "void/null"
     */
    private String buildDetail(Object result) {
        if (result == null) {
            return "void/null";
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            if (json.length() > DETAIL_MAX_LENGTH) {
                return json.substring(0, DETAIL_MAX_LENGTH) + "...(truncated)";
            }
            return json;
        } catch (Exception e) {
            // Jackson 序列化失败时降级为 toString
            String str = String.valueOf(result);
            if (str.length() > DETAIL_MAX_LENGTH) {
                return str.substring(0, DETAIL_MAX_LENGTH) + "...(truncated)";
            }
            return str;
        }
    }
}
