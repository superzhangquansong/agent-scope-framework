package com.agent.scope.framework.config;

import io.agentscope.extensions.scheduler.AgentScheduler;
import io.agentscope.extensions.scheduler.quartz.QuartzAgentScheduler;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 GA 特性三十八：定时唤醒调度配置
 * <p>
 * 装配基于 Quartz 的 {@link QuartzAgentScheduler}，支持按 CRON 表达式或固定频率
 * 定时唤醒 Agent 执行任务。常见场景：
 * <ul>
 *   <li>定时报告：每天早 8 点生成日报并推送</li>
 *   <li>定时巡检：每 10 分钟检查设备状态异常并告警</li>
 *   <li>定时同步：每小时同步外部数据源</li>
 *   <li>定时清理：每天凌晨清理过期会话</li>
 * </ul>
 * </p>
 * <p>
 * 调度模式支持 CRON（CRON 表达式）、FIXED_RATE（固定频率毫秒）、
 * FIXED_DELAY（固定延迟毫秒），由 {@code ScheduleConfig} 描述。
 * </p>
 * <p>
 * 调度任务通过 REST 接口（{@code SchedulerController}）动态注册，例如：
 * <pre>
 * POST /api/scheduler/schedule
 * {
 *   "name": "daily-report",
 *   "cron": "0 0 8 * * ?",
 *   "message": "生成今日设备状态日报"
 * }
 * </pre>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "scheduler-enabled", havingValue = "true")
public class SchedulerConfig {

    /** 持有调度器引用，供销毁时关闭 */
    private volatile AgentScheduler agentScheduler;

    /**
     * Quartz 调度器 Bean。
     * <p>
     * 装配 {@link QuartzAgentScheduler}（autoStart=true，启动即生效），
     * 供 {@code SchedulerController} 注入后动态注册 / 列举 / 取消调度任务。
     * 配置类已由 {@code @ConditionalOnProperty} 守护，此处直接返回具体类型，
     * 便于控制器以构造器注入方式消费。
     * </p>
     *
     * @return Agent 调度器
     */
    @Bean
    public AgentScheduler agentScheduler() {
        QuartzAgentScheduler scheduler = QuartzAgentScheduler.builder()
                .autoStart(true)
                .build();
        this.agentScheduler = scheduler;
        log.info("[SchedulerConfig] Quartz 调度器已装配: autoStart=true, type={}", scheduler.getSchedulerType());
        return scheduler;
    }

    /**
     * 容器销毁时关闭调度器，释放 Quartz 线程池资源。
     */
    @PreDestroy
    public void shutdown() {
        if (agentScheduler != null) {
            log.info("[SchedulerConfig] 关闭 Quartz 调度器");
            agentScheduler.shutdown();
        }
    }
}
