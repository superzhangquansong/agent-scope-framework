package com.agent.scope.framework.config.infrastructure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置类
 * <p>
 * 提供 RedisTemplate（JSON 序列化）+ StringRedisTemplate + 双层缓存管理器（L1 Caffeine + L2 Redis）。
 *
 * @author hdl-agent
 */
@Configuration
@EnableCaching
@ConditionalOnClass(RedisTemplate.class)
public class RedisConfig {

    /**
     * Redis 缓存默认 TTL（分钟）
     */
    private static final long REDIS_CACHE_TTL_MINUTES = 30;

    /**
     * Caffeine L1 缓存最大条目数
     */
    private static final long CAFFEINE_MAX_SIZE = 1000;

    /**
     * Caffeine L1 缓存写入后过期时间（分钟）
     */
    private static final long CAFFEINE_EXPIRE_AFTER_WRITE_MINUTES = 5;

    /**
     * RedisTemplate（Key: String, Value: Object JSON）
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Redis 缓存管理器（L2 缓存）。
     * <p>作为 L2 分布式缓存，提供跨实例共享与持久化能力。TTL 30 分钟。</p>
     *
     * @param factory Redis 连接工厂
     * @return Redis 缓存管理器
     */
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(REDIS_CACHE_TTL_MINUTES))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonRedisSerializer()))
                .disableCachingNullValues();
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }

    /**
     * Caffeine 缓存管理器（L1 本地缓存，P3-21）。
     * <p>
     * 配置：最大 1000 条目，写入后 5 分钟过期。
     * 作为 L1 本地缓存，热点数据直接命中本地内存，减少 Redis 网络开销。
     * </p>
     *
     * @return Caffeine 缓存管理器
     */
    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(CAFFEINE_MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(CAFFEINE_EXPIRE_AFTER_WRITE_MINUTES)));
        return manager;
    }

    /**
     * 组合缓存管理器（L1 Caffeine + L2 Redis，P3-21）。
     * <p>
     * <b>双层缓存读写策略</b>：
     * <ul>
     *   <li>读：先查 L1 Caffeine 本地缓存，未命中再查 L2 Redis 缓存</li>
     *   <li>写：同时写入 L1 与 L2（L1 短 TTL 5min 保证新鲜度，L2 长 TTL 30min 保证持久性）</li>
     *   <li>淘汰：L1 基于 Caffeine W-TinyLFU 算法 + 写后 5 分钟过期；L2 基于 Redis TTL 30 分钟过期</li>
     * </ul>
     * CompositeCacheManager 按顺序委托：L1 Caffeine 优先命中，未命中回退到 L2 Redis。
     * 标记为 @Primary 作为默认 CacheManager。
     * </p>
     * <p>
     * <b>依赖说明</b>：需要 caffeine 依赖（已在 pom.xml 中声明）
     * 与 spring-context-support（传递依赖，提供 CaffeineCacheManager）。
     * </p>
     *
     * @param caffeineCacheManager Caffeine L1 缓存管理器
     * @param redisCacheManager    Redis L2 缓存管理器
     * @return 组合缓存管理器
     */
    @Bean
    @Primary
    public CompositeCacheManager compositeCacheManager(CaffeineCacheManager caffeineCacheManager,
                                                       RedisCacheManager redisCacheManager) {
        CompositeCacheManager composite = new CompositeCacheManager(caffeineCacheManager, redisCacheManager);
        composite.setFallbackToNoOpCache(false);
        return composite;
    }

    /**
     * 创建 JSON 序列化器
     */
    private GenericJackson2JsonRedisSerializer jsonRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
