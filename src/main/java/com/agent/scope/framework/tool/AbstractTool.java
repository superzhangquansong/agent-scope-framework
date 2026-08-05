package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import io.agentscope.core.agent.RuntimeContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具基类 —— 所有业务工具类的抽象父类。
 * <p>
 * 提供通用的会话上下文解析与结果记录方法。子类通过 {@code @Tool} 注解标注工具方法，
 * 由框架自动扫描注册为 Agent 可调用的工具。
 * </p>
 * <p>
 * <b>核心方法</b>：
 * <ul>
 *   <li>{@link #resolveSessionContext(RuntimeContext)} —— 从运行时上下文解析会话上下文</li>
 *   <li>{@link #recordResult(String, Object)} —— 记录工具执行结果与事件埋点</li>
 * </ul>
 * </p>
 * <p>
 * <b>上下文传递机制</b>：
 * {@link com.scope.starter.middleware.SessionContextMiddleware} 在 onAgent 阶段
 * 将 SessionContext 注入 RuntimeContext，工具方法通过本类提供的方法获取。
 * 工具方法参数中的 SessionContext 由框架自动注入，无需手动传递。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
public abstract class AbstractTool {

    /**
     * RuntimeContext 中 SessionContext 的存储 key
     */
    public static final String CTX_KEY_SESSION_CONTEXT = "sessionContext";

    /**
     * 从 AgentScope {@link RuntimeContext} 解析会话上下文。
     * <p>
     * Reactor 异步线程中 ThreadLocal 不可用，必须通过 RuntimeContext 传递。
     * SessionContext 由 {@link com.scope.starter.middleware.SessionContextMiddleware}
     * 在 onAgent 阶段注入到 RuntimeContext 的共享变量空间。
     * </p>
     *
     * @param ctx AgentScope 运行时上下文（可为 null）
     * @return 会话上下文，解析失败返回 null
     */
    protected SessionContext resolveSessionContext(RuntimeContext ctx) {
        if (ctx == null) {
            log.warn("RuntimeContext 为空，无法解析 SessionContext");
            return null;
        }
        // 优先从共享变量空间获取 SessionContext
        Object obj = ctx.get(CTX_KEY_SESSION_CONTEXT);
        if (obj instanceof SessionContext sessionContext) {
            return sessionContext;
        }
        // 兜底：尝试按类型获取（部分场景使用 Class 作为 key）
        if (obj == null) {
            log.warn("RuntimeContext 中未找到 SessionContext，key={}", CTX_KEY_SESSION_CONTEXT);
        }
        return obj instanceof SessionContext ? (SessionContext) obj : null;
    }

    /**
     * 记录工具执行结果与事件埋点。
     * <p>
     * 将工具执行结果以 INFO 级别日志输出，便于全链路审计与问题排查。
     * 后续可扩展为写入审计表或上报监控系统。
     * </p>
     *
     * @param toolName 工具名称
     * @param result   工具执行结果
     */
    protected void recordResult(String toolName, Object result) {
        if (result == null) {
            log.info("[ToolResult] tool={}, result=null", toolName);
            return;
        }
        String resultStr = result.toString();
        // 截断超长日志，避免日志文件膨胀
        if (resultStr.length() > 500) {
            resultStr = resultStr.substring(0, 500) + "...(truncated)";
        }
        log.info("[ToolResult] tool={}, result={}", toolName, resultStr);
    }
}
