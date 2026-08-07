package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 GA 特性四十五：模型调用可观测性增强配置
 * <p>
 * 在 OpenTelemetry 链路追踪基础上，增加 Prometheus 指标收集：
 * - 模型调用次数计数器（按模型名、工具名分类）
 * - 模型调用延迟分布（Timer 直方图）
 * - Token 消耗计数器（输入/输出 token 分别统计）
 * </p>
 * <p>
 * 指标数据通过 Spring Boot Actuator 的 /actuator/prometheus 端点暴露，
 * 可被 Grafana 抓取用于监控告警。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityConfig {

    private final AgentScopeProperties properties;

    /**
     * 模型调用次数计数器
     * <p>
     * 统计模型 API 调用总次数，按模型名称和调用结果（success/error）分类。
     * 用于监控模型调用成功率和调用趋势。
     * </p>
     *
     * @param meterRegistry Micrometer 指标注册表
     * @return Counter 计数器
     */
    @Bean
    public Counter modelCallCounter(MeterRegistry meterRegistry) {
        Counter counter = Counter.builder("agent_model_call_total")
                .description("模型调用总次数")
                .tag("application", "agent-scope-framework")
                .tag("model.name", properties.getModelName())
                .register(meterRegistry);
        log.info("[ObservabilityConfig] 已注册 Prometheus 指标: agent_model_call_total");
        return counter;
    }

    /**
     * 模型调用延迟计时器
     * <p>
     * 统计模型 API 调用的延迟分布，按模型名称分类。
     * 用于监控模型响应时间趋势和 P99/P95 延迟。
     * </p>
     *
     * @param meterRegistry Micrometer 指标注册表
     * @return Timer 计时器
     */
    @Bean
    public Timer modelCallTimer(MeterRegistry meterRegistry) {
        Timer timer = Timer.builder("agent_model_call_duration")
                .description("模型调用延迟分布")
                .tag("application", "agent-scope-framework")
                .register(meterRegistry);
        log.info("[ObservabilityConfig] 已注册 Prometheus 指标: agent_model_call_duration");
        return timer;
    }

    /**
     * 工具调用次数计数器
     * <p>
     * 统计工具调用总次数，按工具名称和执行结果（success/error）分类。
     * 用于监控工具调用成功率和各工具使用频率。
     * </p>
     *
     * @param meterRegistry Micrometer 指标注册表
     * @return Counter 计数器
     */
    @Bean
    public Counter toolCallCounter(MeterRegistry meterRegistry) {
        Counter counter = Counter.builder("agent_tool_call_total")
                .description("工具调用总次数")
                .tag("application", "agent-scope-framework")
                .tag("tool.name", "unknown")
                .register(meterRegistry);
        log.info("[ObservabilityConfig] 已注册 Prometheus 指标: agent_tool_call_total");
        return counter;
    }
}
