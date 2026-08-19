package com.agent.scope.framework.config;

import com.agent.scope.framework.config.infrastructure.TtlAgentStateStore;
import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
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
 * 使用 {@link RedisDistributedStore} 替代默认的 JsonFileAgentStateStore，实现：
 * - 跨节点会话恢复：任意副本都能恢复任意用户的完整上下文
 * - 零停机滚动发布：旧 pod 退出前自动保存，新 pod 接到流量时自动从存储还原
 * - 按 (userId, sessionId) 自动分区，支持多租户隔离
 * </p>
 * <p>
 * <b>关键改造（记忆分布式化）</b>：改用 {@link RedisDistributedStore#fromJedis} 创建
 * 分布式存储后端，它同时提供三套能力：
 * <ul>
 *   <li>{@code agentStateStore()} —— AgentState 状态存储（替代原 RedisAgentStateStore）</li>
 *   <li>{@code baseStore()} —— BaseStore 文件存储后端（供 RemoteFilesystemSpec 持久化 MEMORY.md 等）</li>
 *   <li>{@code sandboxSnapshotSpec()} —— 沙箱快照后端</li>
 * </ul>
 * 这样 AgentState（在 Redis）与工作区文件 MEMORY.md/memory/*.md（也在 Redis）共享同一份分布式后端，
 * 多副本下记忆与状态不再割裂。
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
     * Jedis 客户端 Bean（线程安全，内置连接池）。
     * <p>
     * 从 Spring Data Redis 的 LettuceConnectionFactory 提取连接参数，
     * 构建 JedisPooled 客户端，供 RedisDistributedStore 和其他需要 Jedis 的组件复用。
     * 暴露为 Bean 避免重复创建连接池。
     * </p>
     *
     * @param redisTemplate Spring Data Redis 模板
     * @return JedisPooled 客户端
     */
    @Bean(destroyMethod = "close")
    public JedisPooled jedisPooled(RedisTemplate<String, Object> redisTemplate) {
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

        JedisPooled jedis = new JedisPooled(redisUri);
        log.info("[StateStoreConfig] JedisPooled 客户端创建完成: host={}, port={}, database={}", host, port, database);
        return jedis;
    }

    /**
     * Redis 分布式存储后端 Bean。
     * <p>
     * 通过 {@link RedisDistributedStore#fromJedis} 创建，统一提供 AgentStateStore + BaseStore + 沙箱快照。
     * HarnessAgent 通过 {@code .distributedStore(...)} 装配后，状态存储与文件系统共享同一份 Redis 后端。
     * </p>
     *
     * @param jedis Jedis 客户端
     * @return RedisDistributedStore 分布式存储后端
     */
    @Bean
    public RedisDistributedStore redisDistributedStore(JedisPooled jedis) {
        RedisDistributedStore distributedStore = RedisDistributedStore.fromJedis(jedis);
        log.info("[StateStoreConfig] RedisDistributedStore 创建完成（同时提供 AgentStateStore + BaseStore）");
        return distributedStore;
    }

    /**
     * AgentStateStore Bean（从 RedisDistributedStore 获取）。
     * <p>
     * 供 HarnessAgent.Builder.stateStore() 装配，保持向后兼容。
     * 实际存储后端与 {@link #redisDistributedStore} 共享同一份 Redis。
     * </p>
     *
     * @param distributedStore 分布式存储后端
     * @return AgentStateStore 实例
     */
    @Bean
    public AgentStateStore agentStateStore(RedisDistributedStore distributedStore, JedisPooled jedis) {
        AgentStateStore rawStore = distributedStore.agentStateStore();
        // 用 TTL 装饰器包装：AgentState 写入 Redis 后自动设置 TTL，
        // 7 天未活跃的会话自动清理，避免内存无限增长
        int ttlDays = properties.getStateStore().getStateTtlDays();
        if (ttlDays > 0) {
            long ttlSeconds = ttlDays * 24L * 3600L;
            AgentStateStore ttlStore = new TtlAgentStateStore(rawStore, jedis, ttlSeconds);
            log.info("[StateStoreConfig] AgentStateStore 已包装 TTL 装饰器: ttl={}天", ttlDays);
            return ttlStore;
        }
        log.info("[StateStoreConfig] AgentStateStore TTL 已禁用（stateTtlDays=0），永久驻留");
        return rawStore;
    }

    /**
     * BaseStore Bean（从 RedisDistributedStore 获取）。
     * <p>
     * 供 RemoteFilesystemSpec 装配，使 MEMORY.md / memory/*.md 等工作区文件持久化到 Redis，
     * 多副本下共享同一份长期记忆。
     * </p>
     *
     * @param distributedStore 分布式存储后端
     * @return BaseStore 文件存储后端
     */
    @Bean
    public BaseStore baseStore(RedisDistributedStore distributedStore) {
        BaseStore store = distributedStore.baseStore();
        log.info("[StateStoreConfig] BaseStore 从 RedisDistributedStore 获取完成（用于 RemoteFilesystemSpec）");
        return store;
    }
}
