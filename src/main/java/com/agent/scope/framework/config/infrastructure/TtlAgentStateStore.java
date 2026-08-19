package com.agent.scope.framework.config.infrastructure;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link AgentStateStore} TTL 装饰器。
 *
 * <p>包装底层 {@link AgentStateStore}（如 RedisAgentStateStore），在每次 {@code save} 后
 * 用 Redis SCAN 命令扫描匹配 (userId, sessionId) 的 key 并设置 TTL，
 * 使 AgentState 在 Redis 中自动过期清理，避免永久驻留导致内存膨胀。</p>
 *
 * <h3>背景</h3>
 * <p>官方 RedisAgentStateStore 写入 Redis 的 key 无 TTL（永久驻留）。
 * 一个活跃用户的 AgentState 可能 50-200KB，1000 个活跃用户 = 50-200MB。
 * 加 TTL 7 天后，7 天未活跃的会话自动从 Redis 清理，释放内存。</p>
 *
 * <h3>key 匹配策略</h3>
 * <p>不依赖 RedisAgentStateStore 内部的 key 格式（keyPrefix + slotId），
 * 而是用 SCAN + 通配符模式 {@code *{userId}*{sessionId}*} 扫描匹配 key。
 * userId（登录名）+ sessionId（UUID）组合足够唯一，不会误匹配其他用户的 key。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
public class TtlAgentStateStore implements AgentStateStore {

    /**
     * 底层被包装的状态存储
     */
    private final AgentStateStore delegate;

    /**
     * Redis 客户端（用于 SCAN + expire）
     */
    private final JedisPooled jedis;

    /**
     * TTL 过期时间（秒），默认 7 天
     */
    private final long ttlSeconds;

    /**
     * 构造 TTL 装饰器。
     *
     * @param delegate   底层 AgentStateStore（如 RedisAgentStateStore）
     * @param jedis      Redis 客户端
     * @param ttlSeconds TTL 秒数（如 7 天 = 604800）
     */
    public TtlAgentStateStore(AgentStateStore delegate, JedisPooled jedis, long ttlSeconds) {
        this.delegate = delegate;
        this.jedis = jedis;
        this.ttlSeconds = ttlSeconds;
    }

    // ==================== save 方法：委托 + 刷新 TTL ====================

    @Override
    public void save(String userId, String sessionId, String stateKey, State state) {
        delegate.save(userId, sessionId, stateKey, state);
        refreshTtl(userId, sessionId);
    }

    @Override
    public void save(String userId, String sessionId, String stateKey,
                     List<? extends State> states) {
        delegate.save(userId, sessionId, stateKey, states);
        refreshTtl(userId, sessionId);
    }

    // ==================== 其他方法：直接委托 ====================

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId,
                                             String stateKey, Class<T> type) {
        return delegate.get(userId, sessionId, stateKey, type);
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId,
                                             String stateKey, Class<T> type) {
        return delegate.getList(userId, sessionId, stateKey, type);
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return delegate.exists(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId) {
        delegate.delete(userId, sessionId);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return delegate.listSessionIds(userId);
    }

    @Override
    public void close() {
        delegate.close();
    }

    // ==================== TTL 刷新逻辑 ====================

    /**
     * 用 Redis SCAN 命令扫描匹配 (userId, sessionId) 的 key 并设置 TTL。
     * <p>
     * 匹配模式 {@code *{userId}*{sessionId}*}：不依赖底层 key 格式，
     * userId + sessionId 组合足够唯一，不会误匹配其他用户。
     * </p>
     * <p>
     * 使用 SCAN 而非 KEYS 命令，避免阻塞 Redis（O(N) 非阻塞迭代）。
     * </p>
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     */
    private void refreshTtl(String userId, String sessionId) {
        try {
            // 构建匹配模式：包含 userId 和 sessionId 的 key
            String pattern = "*" + userId + "*" + sessionId + "*";
            ScanParams params = new ScanParams().match(pattern).count(100);

            // Jedis scan 游标从 "0" 开始
            String cursor = "0";
            int refreshedCount = 0;
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                for (String key : result.getResult()) {
                    jedis.expire(key, ttlSeconds);
                    refreshedCount++;
                }
                cursor = result.getCursor();
            } while (!"0".equals(cursor)); // 游标为 "0" 表示迭代结束

            if (refreshedCount > 0) {
                log.debug("[TtlAgentStateStore] 刷新 TTL: userId={}, sessionId={}, keys={}, ttl={}s",
                        userId, sessionId, refreshedCount, ttlSeconds);
            }
        } catch (Exception e) {
            // TTL 刷新失败不影响正常流程（AgentState 已写入，只是不会自动过期）
            log.warn("[TtlAgentStateStore] TTL 刷新失败（不影响数据写入）: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage());
        }
    }
}