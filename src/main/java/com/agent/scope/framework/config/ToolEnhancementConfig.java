package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope 2.0 GA 特性四十一/四十二：工具执行超时控制 + 工具结果缓存配置
 * <p>
 * 工具执行超时控制：
 * - 为每个 @Tool 方法配置独立超时，防止卡死 Agent 主循环
 * - 超时时间通过 scope.agentscope.tool.timeout-ms 配置，默认 30 秒
 * </p>
 * <p>
 * 工具结果缓存：
 * - 对高频查询（如设备列表、产品价格）支持 Redis 缓存
 * - 缓存 TTL 通过 scope.agentscope.tool.cache-ttl-seconds 配置，默认 300 秒
 * - 缓存 key 按工具名 + 参数哈希自动生成
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.tool", name = "enhanced", havingValue = "true", matchIfMissing = true)
public class ToolEnhancementConfig {

    private final AgentScopeProperties properties;

    /**
     * 工具执行超时时间
     * <p>
     * 全局默认超时时间，适用于所有 @Tool 方法。
     * 单个工具可通过 @Tool(timeout=xxx) 注解覆盖此默认值。
     * </p>
     *
     * @return 超时时间（Duration）
     */
    @Bean("toolExecutionTimeout")
    public Duration toolExecutionTimeout() {
        long timeoutMs = properties.getTool().getTimeoutMs();
        Duration timeout = Duration.ofMillis(timeoutMs);
        log.info("[ToolEnhancementConfig] 工具执行超时: {}ms", timeoutMs);
        return timeout;
    }

    /**
     * 工具结果缓存服务
     * <p>
     * 基于 Redis 的工具结果缓存，对只读工具（readOnly=true）的调用结果自动缓存。
     * 缓存 key 格式：tool_cache:{toolName}:{paramsHash}
     * 缓存 TTL 通过配置项指定，默认 300 秒。
     * </p>
     *
     * @param redisTemplate Redis 模板
     * @return ToolResultCache 缓存服务
     */
    @Bean
    public ToolResultCache toolResultCache(
            RedisTemplate<String, Object> redisTemplate) {
        long cacheTtlSeconds = properties.getTool().getCacheTtlSeconds();
        ToolResultCache cache = new ToolResultCache(redisTemplate, Duration.ofSeconds(cacheTtlSeconds));
        log.info("[ToolEnhancementConfig] 工具结果缓存已启用: ttl={}s", cacheTtlSeconds);
        return cache;
    }

    /**
     * 工具结果缓存服务
     * <p>
     * 提供 get/put/delete 操作，缓存 key 按工具名 + 参数哈希自动生成。
     * 仅对只读工具启用缓存，写操作工具不缓存。
     * </p>
     */
    @Slf4j
    public static class ToolResultCache {

        private final RedisTemplate<String, Object> redisTemplate;
        private final Duration ttl;
        private static final String CACHE_KEY_PREFIX = "tool_cache:";

        /**
         * 构造函数
         *
         * @param redisTemplate Redis 模板
         * @param ttl           缓存 TTL
         */
        public ToolResultCache(RedisTemplate<String, Object> redisTemplate, Duration ttl) {
            this.redisTemplate = redisTemplate;
            this.ttl = ttl;
        }

        /**
         * 获取缓存结果
         *
         * @param toolName 工具名称
         * @param params   工具参数
         * @return 缓存结果，不存在返回 null
         */
        @SuppressWarnings("unchecked")
        public Object get(String toolName, Map<String, Object> params) {
            String key = buildCacheKey(toolName, params);
            Object result = redisTemplate.opsForValue().get(key);
            if (result != null) {
                log.debug("[ToolResultCache] 缓存命中: tool={}", toolName);
            }
            return result;
        }

        /**
         * 写入缓存
         *
         * @param toolName 工具名称
         * @param params   工具参数
         * @param result   工具结果
         */
        public void put(String toolName, Map<String, Object> params, Object result) {
            String key = buildCacheKey(toolName, params);
            redisTemplate.opsForValue().set(key, result, ttl);
            log.debug("[ToolResultCache] 缓存写入: tool={}, ttl={}s", toolName, ttl.getSeconds());
        }

        /**
         * 删除缓存
         *
         * @param toolName 工具名称
         * @param params   工具参数
         */
        public void evict(String toolName, Map<String, Object> params) {
            String key = buildCacheKey(toolName, params);
            redisTemplate.delete(key);
            log.debug("[ToolResultCache] 缓存删除: tool={}", toolName);
        }

        /**
         * 构建缓存 key
         * <p>
         * key 格式：tool_cache:{toolName}:{paramsHash}
         * paramsHash 基于工具参数的哈希值，确保相同参数命中相同缓存。
         * </p>
         *
         * @param toolName 工具名称
         * @param params   工具参数
         * @return 缓存 key
         */
        private String buildCacheKey(String toolName, Map<String, Object> params) {
            int paramsHash = params != null ? params.hashCode() : 0;
            return CACHE_KEY_PREFIX + toolName + ":" + paramsHash;
        }
    }
}
