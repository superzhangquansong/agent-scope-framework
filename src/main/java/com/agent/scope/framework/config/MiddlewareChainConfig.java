package com.agent.scope.framework.config;

import com.agent.scope.framework.config.PromptTemplateConfig.PromptTemplateHolder;
import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.middleware.ObservabilityMiddleware;
import com.agent.scope.framework.middleware.PromptRefreshMiddleware;
import com.agent.scope.framework.middleware.ResilienceMiddleware;
import com.agent.scope.framework.middleware.ToolEnhancementMiddleware;
import com.agent.scope.framework.service.DeviceContextService;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final DeviceContextService deviceContextService;

    /**
     * 中间件链 Bean
     * <p>
     * 装配五阶段中间件链，按从外到内的顺序排列：
     * 1. OtelTracingMiddleware：OpenTelemetry 分布式链路追踪（特性39）
     * 2. ObservabilityMiddleware：Prometheus 指标收集（特性45）
     * 3. ResilienceMiddleware：限流熔断（特性43）
     * 4. ToolEnhancementMiddleware：工具超时控制 + 结果缓存（特性41/42）
     * </p>
     *
     * @return 中间件列表
     */
    @Bean
    public List<MiddlewareBase> middlewareChain(
            Optional<Counter> modelCallCounter,
            Optional<Timer> modelCallTimer,
            Optional<Counter> toolCallCounter,
            Optional<Duration> toolExecutionTimeout,
            Optional<ToolEnhancementConfig.ToolResultCache> toolResultCache,
            Optional<RateLimiter> modelCallRateLimiter,
            Optional<CircuitBreaker> modelCallCircuitBreaker,
            Optional<PromptTemplateHolder> promptTemplateHolder) {
        List<MiddlewareBase> middlewares = new ArrayList<>();

        // 1. OpenTelemetry 追踪中间件（特性39）
        if (properties.getAdvanced().isOtelTracingEnabled()) {
            middlewares.add(new OtelTracingMiddleware());
            log.info("[MiddlewareChainConfig] 已装配 OtelTracingMiddleware（分布式链路追踪）");
        }

        // 2. 可观测性中间件（特性45）— Prometheus 指标真正接入链路
        if (modelCallCounter.isPresent() && modelCallTimer.isPresent() && toolCallCounter.isPresent()) {
            middlewares.add(new ObservabilityMiddleware(
                    modelCallCounter.get(), modelCallTimer.get(), toolCallCounter.get()));
            log.info("[MiddlewareChainConfig] 已装配 ObservabilityMiddleware（Prometheus 指标收集）");
        }

        // 3. 限流熔断中间件（特性43）— Resilience4j 真正接入链路
        if (modelCallRateLimiter.isPresent() && modelCallCircuitBreaker.isPresent()) {
            middlewares.add(new ResilienceMiddleware(
                    modelCallRateLimiter.get(), modelCallCircuitBreaker.get()));
            log.info("[MiddlewareChainConfig] 已装配 ResilienceMiddleware（限流熔断）");
        }

        // 4. 工具增强中间件（特性41/42）— 超时控制 + 结果缓存真正接入链路
        if (toolExecutionTimeout.isPresent() && toolResultCache.isPresent()) {
            middlewares.add(new ToolEnhancementMiddleware(
                    toolExecutionTimeout.get(), toolResultCache.get()));
            log.info("[MiddlewareChainConfig] 已装配 ToolEnhancementMiddleware（工具超时+缓存）");
        }

        // 5. 系统提示词热更新中间件（特性44增强）— Nacos 变更后无需重启即生效 + 设备列表上下文注入
        promptTemplateHolder.ifPresent(holder -> {
            middlewares.add(new PromptRefreshMiddleware(holder, deviceContextService));
            log.info("[MiddlewareChainConfig] 已装配 PromptRefreshMiddleware（提示词热更新 + 设备列表注入）");
        });

        log.info("[MiddlewareChainConfig] 中间件链装配完成，共 {} 个中间件", middlewares.size());
        return middlewares;
    }
}
