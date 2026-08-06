package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.JedisPooled;

/**
 * AgentScope 2.0 GA 特性六/三十四：状态持久化 + 分布式后端配置
 * <p>
 * 使用 RedisAgentStateStore 替代默认的 JsonFileAgentStateStore，实现：
 * - 跨节点会话恢复：任意副本都能恢复任意用户的完整上下文
 * - 零停机滚动发布：旧 pod 退出前自动保存，新 pod 接到流量时自动从存储还原
 * - 按 (userId, sessionId) 自动分区，支持多租户隔离
 * </p>
 * <p>
 * 生产环境必须使用分布式状态存储，否则滚动发布时会丢失会话状态。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.state-store", name = "type", havingValue = "redis", matchIfMissing = true)
public class StateStoreConfig {

    private final AgentScopeProperties properties;

    /**
     * Redis 分布式状态存储 Bean
     * <p>
     * 基于 Spring Data Redis 的连接参数创建 JedisPooled 客户端，
     * 再通过 RedisAgentStateStore.builder() 构建 Redis 状态存储，
     * 替代默认的 JsonFileAgentStateStore（本地文件存储）。
     * </p>
     * <p>
     * 状态数据按 (userId, sessionId) 二元组寻址，存储在 Redis DB 中。
     * 一次 call() 结束后框架自动把整份 AgentState 写入 Redis，
     * 下次相同 (userId, sessionId) 的 call() 自动从 Redis 读回。
     * </p>
     *
     * @param redisTemplate Spring Data Redis 模板（由 Spring Boot 自动配置注入）
     * @return AgentStateStore 实例（Redis 实现）
     */
    @Bean
    public AgentStateStore agentStateStore(RedisTemplate<String, Object> redisTemplate) {
        log.info("[StateStoreConfig] 创建 RedisAgentStateStore，替代默认 JsonFileAgentStateStore");

        // 从 RedisTemplate 获取 Lettuce 连接工厂，提取连接参数
        LettuceConnectionFactory connectionFactory = (LettuceConnectionFactory) redisTemplate.getConnectionFactory();
        String host = connectionFactory.getHostName();
        int port = connectionFactory.getPort();
        int database = connectionFactory.getDatabase();
        String password = connectionFactory.getPassword();

        // 构建 Jedis 客户端 URI（redis://[password@]host:port/database）
        String redisUri;
        if (password != null && !password.isEmpty()) {
            redisUri = String.format("redis://:%s@%s:%d/%d", password, host, port, database);
        } else {
            redisUri = String.format("redis://%s:%d/%d", host, port, database);
        }

        // 创建 JedisPooled 客户端（线程安全，内置连接池）
        JedisPooled jedis = new JedisPooled(redisUri);

        // 通过 RedisAgentStateStore.Builder 构建状态存储
        AgentStateStore stateStore = RedisAgentStateStore.builder()
                .jedisClient(jedis)
                .build();

        log.info("[StateStoreConfig] RedisAgentStateStore 创建完成: host={}, port={}, database={}", host, port, database);
        return stateStore;
    }
}
