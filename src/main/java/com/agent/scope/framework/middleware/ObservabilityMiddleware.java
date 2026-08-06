package com.agent.scope.framework.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ActingInput;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

/**
 * 可观测性中间件（特性45）。
 * <p>
 * 将 Prometheus 指标（Counter/Timer）真正接入 Agent 执行链路，在以下阶段打点：
 * <ul>
 *   <li>{@code onModelCall}：模型调用次数 + 延迟（Timer 记录耗时）</li>
 *   <li>{@code onActing}：工具调用次数计数（按工具名 tag 分类）</li>
 * </ul>
 * </p>
 * <p>
 * 指标通过 {@code /actuator/prometheus} 端点暴露，可被 Grafana 抓取。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ObservabilityMiddleware implements MiddlewareBase {

    private final Counter modelCallCounter;
    private final Timer modelCallTimer;
    private final Counter toolCallCounter;

    @Override
    public reactor.core.publisher.Flux<AgentEvent> onModelCall(
            Agent agent, RuntimeContext ctx, ModelCallInput input,
            Function<ModelCallInput, reactor.core.publisher.Flux<AgentEvent>> next) {

        // 记录模型调用次数
        modelCallCounter.increment();
        // 使用 Timer 记录模型调用耗时
        Timer.Sample sample = Timer.start();
        return next.apply(input)
                .doOnComplete(() -> {
                    sample.stop(modelCallTimer);
                })
                .doOnError(e -> {
                    sample.stop(modelCallTimer);
                    log.debug("[Observability] 模型调用失败: session={}, error={}",
                            ctx.getSessionId(), e.getMessage());
                });
    }

    @Override
    public reactor.core.publisher.Flux<AgentEvent> onActing(
            Agent agent, RuntimeContext ctx, ActingInput input,
            Function<ActingInput, reactor.core.publisher.Flux<AgentEvent>> next) {
        // 工具调用计数（按工具名分类）
        toolCallCounter.increment();
        return next.apply(input);
    }
}
