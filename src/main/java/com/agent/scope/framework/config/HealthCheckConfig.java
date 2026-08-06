package com.agent.scope.framework.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * AgentScope 2.0 GA 特性四十七：Agent 健康检查配置
 * <p>
 * 提供 /actuator/health 端点的自定义健康指标，包含各组件状态探针：
 * - Redis 连接状态（分布式状态存储后端）
 * - MySQL 连接状态（业务数据库）
 * </p>
 * <p>
 * K8s 调度器通过 livenessProbe/readinessProbe 访问 /actuator/health，
 * 健康检查失败时自动重启 Pod 或从 Service 后端摘除。
 * </p>
 * <p>
 * 注意：Spring Boot 4.x 中 Health 和 HealthIndicator 的包路径已从
 * {@code org.springframework.boot.actuate.health} 迁移到
 * {@code org.springframework.boot.health.contributor}。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.health-check", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealthCheckConfig {

    /**
     * Redis 健康指标
     * <p>
     * 检测 Redis 连接是否正常，用于分布式状态存储后端健康监控。
     * </p>
     *
     * @param redisTemplate Redis 模板
     * @return HealthIndicator 健康指标
     */
    @Bean
    public HealthIndicator redisHealthIndicator(RedisTemplate<String, Object> redisTemplate) {
        return () -> {
            try {
                String result = redisTemplate.getConnectionFactory().getConnection().ping();
                Map<String, Object> details = new HashMap<>();
                details.put("status", "connected");
                details.put("ping", result);
                return Health.up().withDetails(details).build();
            } catch (Exception e) {
                log.warn("[HealthCheck] Redis 健康检查失败: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * MySQL 健康指标
     * <p>
     * 检测 MySQL 连接是否正常，用于业务数据库健康监控。
     * </p>
     *
     * @param dataSource 数据源
     * @return HealthIndicator 健康指标
     */
    @Bean
    public HealthIndicator mysqlHealthIndicator(DataSource dataSource) {
        return () -> {
            try (Connection connection = dataSource.getConnection()) {
                Map<String, Object> details = new HashMap<>();
                details.put("status", "connected");
                details.put("catalog", connection.getCatalog());
                details.put("driver", connection.getMetaData().getDriverName());
                return Health.up().withDetails(details).build();
            } catch (Exception e) {
                log.warn("[HealthCheck] MySQL 健康检查失败: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }
}
