package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import io.agentscope.core.agent.RuntimeContext;
import lombok.extern.slf4j.Slf4j;

import static com.agent.scope.framework.constant.BusinessConst.CTX_KEY_SESSION_CONTEXT;

/**
 * 工具基类。
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
public abstract class AbstractTool {


    /**
     * 从 RuntimeContext 解析 SessionContext。
     *
     * <p>RuntimeContext 由 AgentScopeIntegration 在调用 HarnessAgent.call() 前注入，
     * 包含当前请求的 sessionToken、homeId、userId 等会话信息。</p>
     *
     * <p><b>多租户隔离关键</b>：每个请求独立的 RuntimeContext，确保 Agent A 调用 Agent B 时
     * 会话上下文正确传递，不同租户的请求不会串扰。</p>
     *
     * @param runtimeContext 运行时上下文（AgentScope 框架自动注入）
     * @return SessionContext 实例；若未注入则返回空上下文（工具调用会因鉴权失败而报错）
     */
    protected SessionContext resolveSessionContext(RuntimeContext runtimeContext) {
        if (runtimeContext == null) {
            log.warn("[AbstractTool] RuntimeContext 为空，使用空会话上下文（可能因鉴权失败）");
            return new SessionContext();
        }
        SessionContext ctx = runtimeContext.get(CTX_KEY_SESSION_CONTEXT);
        if (ctx == null) {
            log.warn("[AbstractTool] SessionContext 未注入到 RuntimeContext");
            return new SessionContext();
        }
        return ctx;
    }
}