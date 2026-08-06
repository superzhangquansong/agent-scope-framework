package com.agent.scope.framework.service;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 分布式限流服务（特性 43 增强）。
 *
 * <p>基于 Redis + Lua 脚本实现滑动窗口限流，支持多实例共享限流计数器。
 * 相比 Resilience4j 内存限流，Redis 限流在分布式部署下能精确控制全局 QPS。</p>
 *
 * <p><b>核心算法</b>：滑动窗口计数法。Lua 脚本保证"读取计数→判断→递增→设置过期"
 * 四步操作的原子性，避免并发下的竞态条件。</p>
 *
 * <p><b>限流维度</b>：</p>
 * <ul>
 *   <li>{@code model-call}：模型调用限流（防止 LLM API 过载）</li>
 *   <li>{@code hdl-api}：HDL 云端 API 限流（保护下游服务）</li>
 *   <li>{@code user-session}：用户会话级限流（防止单用户刷接口）</li>
 * </ul>
 *
 * <p><b>所有参数均通过 Nacos 热加载</b>，修改限流规则后 30 秒内生效。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimiterService {

    /**
     * 滑动窗口限流 Lua 脚本。
     *
     * <p>逻辑：
     * <ol>
     *   <li>读取当前 Key 的计数值</li>
     *   <li>若计数 >= 限流阈值，返回 0（拒绝）</li>
     *   <li>否则 INCR 递增计数，首次设置过期时间，返回 1（允许）</li>
     * </ol>
     * </p>
     */
    private static final String RATE_LIMIT_LUA_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if current and tonumber(current) >= tonumber(ARGV[1]) then
                return 0
            end
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            return 1
            """;

    private final StringRedisTemplate stringRedisTemplate;

    private final AgentScopeProperties properties;

    /**
     * Redis Lua 脚本对象（初始化一次，复用执行）
     */
    private DefaultRedisScript<Long> rateLimitScript;

    /**
     * 限流规则缓存：ruleName → (limit, windowSeconds)
     */
    private Map<String, AgentScopeProperties.RateLimitRule> ruleMap = new HashMap<>();

    /**
     * 初始化 Lua 脚本和限流规则缓存。
     */
    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptText(RATE_LIMIT_LUA_SCRIPT);
        rateLimitScript.setResultType(Long.class);

        // 从 Nacos 配置加载限流规则到缓存
        refreshRules();

        log.info("[RedisRateLimiter] 初始化完成: enabled={}, rules={}",
                properties.getRedisRateLimit().isEnabled(),
                ruleMap.keySet());
    }

    /**
     * 从 Nacos 配置刷新限流规则缓存。
     *
     * <p>当 Nacos 配置变更触发 @RefreshScope 重建时，本方法重新加载规则。
     * 支持运行时动态调整限流阈值，无需重启。</p>
     */
    public void refreshRules() {
        AgentScopeProperties.RedisRateLimit config = properties.getRedisRateLimit();
        ruleMap.clear();
        if (config.getRules() != null) {
            for (AgentScopeProperties.RateLimitRule rule : config.getRules()) {
                if (rule.getName() != null && !rule.getName().isBlank()) {
                    ruleMap.put(rule.getName(), rule);
                    log.info("[RedisRateLimiter] 加载限流规则: name={}, limit={}, window={}s",
                            rule.getName(), rule.getLimit(), rule.getWindowSeconds());
                }
            }
        }
    }

    /**
     * 尝试获取限流许可（按规则名）。
     *
     * <p>使用 Redis + Lua 脚本原子操作，在分布式环境下精确控制全局 QPS。
     * 当限流未启用或规则不存在时，默认放行。</p>
     *
     * @param ruleName 限流规则名称（如 model-call / hdl-api / user-session）
     * @param identity 限流标识（如 userId、sessionId、IP 等，用于按维度隔离）
     * @return true=允许通过，false=被限流
     */
    public boolean tryAcquire(String ruleName, String identity) {
        AgentScopeProperties.RedisRateLimit config = properties.getRedisRateLimit();

        // 限流未启用，直接放行
        if (!config.isEnabled()) {
            return true;
        }

        // 查找规则，不存在则使用默认值
        AgentScopeProperties.RateLimitRule rule = ruleMap.get(ruleName);
        int limit = config.getDefaultLimit();
        int windowSeconds = config.getDefaultWindowSeconds();
        if (rule != null) {
            limit = rule.getLimit();
            windowSeconds = rule.getWindowSeconds();
        }

        // 构建 Redis Key：rate_limit:{ruleName}:{identity}
        String redisKey = config.getKeyPrefix() + ruleName + ":" + identity;

        try {
            // 执行 Lua 脚本：返回 1=允许，0=拒绝
            Long result = stringRedisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(limit),
                    String.valueOf(windowSeconds));

            boolean allowed = result != null && result == 1L;
            if (!allowed) {
                log.warn("[RedisRateLimiter] 请求被限流: rule={}, identity={}, limit={}, window={}s",
                        ruleName, identity, limit, windowSeconds);
            }
            return allowed;
        } catch (Exception e) {
            // Redis 异常时降级放行，避免限流服务故障导致业务不可用
            log.error("[RedisRateLimiter] Redis 执行异常，降级放行: rule={}, identity={}, error={}",
                    ruleName, identity, e.getMessage());
            return true;
        }
    }

    /**
     * 尝试获取模型调用限流许可。
     *
     * @param sessionId 会话 ID（限流标识）
     * @return true=允许通过，false=被限流
     */
    public boolean tryAcquireModelCall(String sessionId) {
        return tryAcquire("model-call", sessionId);
    }

    /**
     * 尝试获取 HDL API 限流许可。
     *
     * @param userId 用户 ID（限流标识）
     * @return true=允许通过，false=被限流
     */
    public boolean tryAcquireHdlApi(String userId) {
        return tryAcquire("hdl-api", userId);
    }

    /**
     * 尝试获取用户会话限流许可。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return true=允许通过，false=被限流
     */
    public boolean tryAcquireUserSession(String userId, String sessionId) {
        return tryAcquire("user-session", userId + ":" + sessionId);
    }
}
