package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 2.0 GA 特性十二/十三/三十九：中间件链 + Hook 系统 + OpenTelemetry 集成配置
 * <p>
 * 五阶段洋葱+管道混合模型，在以下五个生命周期阶段注入自定义逻辑：
 * - onAgent：包裹一次完整的 reply 流程
 * - onReasoning：包裹一轮 ReAct 中的推理步骤
 * - onActing：包裹一次工具调用的执行
 * - onModelCall：包裹一次底层 ChatModel API 调用
 * - onSystemPrompt：在每次组装 system prompt 时触发（Transformer 模式）
 * </p>
 * <p>
 * 中间件链装配顺序（从外到内）：
 * 1. OtelTracingMiddleware：OpenTelemetry 分布式链路追踪
 * 2. 自定义业务中间件（如权限检查、日志审计等）
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.middleware", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MiddlewareChainConfig {

    private final AgentScopeProperties properties;

    /**
     * 中间件链 Bean
     * <p>
     * 装配五阶段中间件链，按从外到内的顺序排列：
     * - OtelTracingMiddleware：在 onAgent/onModelCall/onActing 三个位置打点，
     *   按层级生成 span（invoke_agent → chat → execute_tool）
     * </p>
     * <p>
     * 当 OpenTelemetry SDK 未配置时，OtelTracingMiddleware 的所有 hook 会直接短路，
     * 几乎零开销。
     * </p>
     *
     * @return 中间件列表
     */
    @Bean
    public List<MiddlewareBase> middlewareChain() {
        List<MiddlewareBase> middlewares = new ArrayList<>();

        // 1. OpenTelemetry 追踪中间件（特性39）
        if (properties.getAdvanced().isOtelTracingEnabled()) {
            middlewares.add(new OtelTracingMiddleware());
            log.info("[MiddlewareChainConfig] 已装配 OtelTracingMiddleware（分布式链路追踪）");
        }

        // 2. 后续可扩展：权限中间件、日志审计中间件、限流中间件等
        // middlewares.add(new PermissionMiddleware());
        // middlewares.add(new AuditLogMiddleware());

        log.info("[MiddlewareChainConfig] 中间件链装配完成，共 {} 个中间件", middlewares.size());
        return middlewares;
    }
}
