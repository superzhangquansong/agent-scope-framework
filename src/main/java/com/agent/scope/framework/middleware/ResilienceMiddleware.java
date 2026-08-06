package com.agent.scope.framework.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

/**
 * 限流熔断中间件（特性43）。
 * <p>
 * 将 Resilience4j 限流器与熔断器真正接入 Agent 执行链路：
 * <ul>
 *   <li>限流（RateLimiter）：{@code onModelCall} 阶段限制模型调用频率，防止过载</li>
 *   <li>熔断（CircuitBreaker）：模型调用连续失败时自动熔断，快速失败保护系统</li>
 * </ul>
 * </p>
 * <p>
 * Resilience4j 配置通过 {@code application.yaml} 的
 * {@code resilience4j.ratelimiter} 和 {@code resilience4j.circuitbreaker} 定义，
 * 本中间件通过 Bean 名称引用对应实例。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ResilienceMiddleware implements MiddlewareBase {

    private final RateLimiter modelCallRateLimiter;
    private final CircuitBreaker modelCallCircuitBreaker;

    @Override
    public reactor.core.publisher.Flux<AgentEvent> onModelCall(
            Agent agent, RuntimeContext ctx, ModelCallInput input,
            Function<ModelCallInput, reactor.core.publisher.Flux<AgentEvent>> next) {

        return next.apply(input)
                // 限流：超过速率限制时抛出 RequestNotPermitted
                .transformDeferred(RateLimiterOperator.of(modelCallRateLimiter))
                // 熔断：熔断器打开时抛出 CallNotPermitted
                .transformDeferred(CircuitBreakerOperator.of(modelCallCircuitBreaker))
                .doOnError(CallNotPermittedException.class, e ->
                        log.warn("[Resilience] 模型调用被熔断: session={}, error={}",
                                ctx.getSessionId(), e.getMessage()))
                .doOnError(io.github.resilience4j.ratelimiter.RequestNotPermitted.class, e ->
                        log.warn("[Resilience] 模型调用被限流: session={}, error={}",
                                ctx.getSessionId(), e.getMessage()));
    }
}
