package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.extensions.scheduler.AgentScheduler;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import io.agentscope.extensions.scheduler.quartz.QuartzAgentScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * AgentScope 2.0 GA 特性三十八：定时唤醒调度配置
 * <p>
 * 支持按 CRON 表达式或固定频率定时唤醒 Agent 执行任务，常用于：
 * <ul>
 *   <li>定时报告：每天早 8 点生成日报并推送</li>
 *   <li>定时巡检：每 10 分钟检查设备状态异常并告警</li>
 *   <li>定时同步：每小时同步外部数据源</li>
 *   <li>定时清理：每天凌晨清理过期会话</li>
 * </ul>
 * </p>
 * <p>
 * 调度器实现：
 * <ul>
 *   <li>{@link QuartzAgentScheduler}：基于 Quartz，支持持久化与集群</li>
 *   <li>{@code XxlJobAgentScheduler}：基于 XXL-Job，分布式调度（需 XXL-Job 依赖）</li>
 * </ul>
 * </p>
 * <p>
 * 调度模式（{@link io.agentscope.extensions.scheduler.config.ScheduleMode}）：
 * <ul>
 *   <li>CRON：基于 CRON 表达式（推荐，灵活）</li>
 *   <li>FIXED_RATE：固定频率（毫秒）</li>
 *   <li>FIXED_DELAY：固定延迟（毫秒）</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "scheduler-enabled", havingValue = "true")
public class SchedulerConfig {

    private final AgentScopeProperties properties;

    /**
     * Quartz 调度器 Bean。
     * <p>
     * 装配 {@link QuartzAgentScheduler}，支持持久化与集群部署。
     * 调度任务通过 {@code AgentScheduler.schedule(agentConfig, scheduleConfig)} 注册。
     * </p>
     * <p>
     * 配置示例：
     * <pre>
     * scope:
     *   agentscope:
     *     scheduler:
     *       tasks:
     *         - name: daily-report
     *           cron: "0 0 8 * * ?"
     *           message: "生成今日设备状态日报"
     * </pre>
     * </p>
     *
     * @return Agent 调度器
     */
    @Bean
    public Optional<AgentScheduler> agentScheduler() {
        try {
            QuartzAgentScheduler scheduler = QuartzAgentScheduler.builder()
                    .autoStart(true)
                    .build();
            log.info("[SchedulerConfig] Quartz 调度器已装配: autoStart=true");
            return Optional.of(scheduler);
        } catch (Exception e) {
            log.warn("[SchedulerConfig] Quartz 调度器装配失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
