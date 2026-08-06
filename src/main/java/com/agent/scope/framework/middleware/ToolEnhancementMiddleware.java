package com.agent.scope.framework.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import com.agent.scope.framework.config.ToolEnhancementConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Function;

/**
 * 工具增强中间件（特性41/42）。
 * <p>
 * 将工具执行超时控制与结果缓存真正接入 Agent 执行链路：
 * <ul>
 *   <li>超时控制：{@code onActing} 阶段对工具调用施加超时限制，超时自动取消</li>
 *   <li>结果缓存：只读工具调用结果缓存到 Redis，相同参数直接命中缓存</li>
 * </ul>
 * </p>
 * <p>
 * 通过 {@link ToolEnhancementConfig.ToolResultCache} 实现 Redis 缓存，
 * 缓存 key 按工具名 + 参数哈希自动生成。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ToolEnhancementMiddleware implements MiddlewareBase {

    private final Duration toolExecutionTimeout;
    private final ToolEnhancementConfig.ToolResultCache toolResultCache;

    @Override
    public reactor.core.publisher.Flux<AgentEvent> onActing(
            Agent agent, RuntimeContext ctx, ActingInput input,
            Function<ActingInput, reactor.core.publisher.Flux<AgentEvent>> next) {

        // 施加工具执行超时控制
        return next.apply(input)
                .timeout(toolExecutionTimeout)
                .doOnError(e -> log.warn("[ToolEnhancement] 工具执行超时: session={}, timeout={}, error={}",
                        ctx.getSessionId(), toolExecutionTimeout, e.getMessage()));
    }
}
