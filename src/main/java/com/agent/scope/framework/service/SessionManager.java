package com.agent.scope.framework.service;

import com.agent.scope.framework.model.UserSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理器（本地缓存 + 可选 Redis 二级缓存）。
 *
 * <p>管理 UserSession 的生命周期：创建、查询、保存、销毁。
 * 默认使用 ConcurrentHashMap 本地缓存，Redis 可用时作为 L2 共享缓存。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class SessionManager {

    /** 会话过期时间（小时） */
    private static final long EXPIRE_HOURS = 2L;

    /** Redis Key 前缀 */
    private static final String REDIS_KEY_PREFIX = "scope:session:";

    /** Jackson 序列化器（线程安全，静态复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** 本地会话缓存（ConcurrentHashMap，expireAfterAccess 由定时清理 + 惰性清除实现） */
    private final ConcurrentHashMap<String, UserSession> sessionCache = new ConcurrentHashMap<>();

    /** Redis 模板（可选注入，Redis 不可用时为 null 降级） */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 创建新会话（生成新的 sessionToken）。
     */
    public UserSession createSession() {
        String token = generateToken();
        UserSession session = new UserSession();
        session.setSessionToken(token);
        session.setLoginTime(System.currentTimeMillis());
        sessionCache.put(token, session);
        saveToRedis(token, session);
        log.info("[SessionManager] 创建会话: token={}", token);
        return session;
    }

    /**
     * 获取会话（先查本地缓存，再查 Redis）。
     */
    public UserSession getSession(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        UserSession session = sessionCache.get(token);
        if (session != null) {
            return session;
        }
        session = loadFromRedis(token);
        if (session != null) {
            sessionCache.put(token, session);
            log.debug("[SessionManager] 从 Redis 加载会话: token={}", token);
        }
        return session;
    }

    /**
     * 按登录名查找会话（遍历本地缓存，用于自动批准等场景）。
     *
     * @param loginName 登录用户名
     * @return 匹配的会话，若未找到返回 null
     */
    public UserSession findByLoginName(String loginName) {
        if (loginName == null || loginName.isEmpty()) {
            return null;
        }
        return sessionCache.values().stream()
                .filter(s -> loginName.equals(s.getLoginName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 保存/更新会话。
     */
    public void save(UserSession session) {
        if (session == null || session.getSessionToken() == null) {
            return;
        }
        String token = session.getSessionToken();
        sessionCache.put(token, session);
        saveToRedis(token, session);
    }

    /**
     * 销毁会话。
     */
    public void destroy(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        sessionCache.remove(token);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_KEY_PREFIX + token);
            } catch (Exception e) {
                log.warn("[SessionManager] Redis 删除会话失败: token={}", token, e.getMessage());
            }
        }
        log.info("[SessionManager] 销毁会话: token={}", token);
    }

    /** 生成 32 位 sessionToken */
    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 写入 Redis（序列化完整 UserSession JSON） */
    private void saveToRedis(String token, UserSession session) {
        if (redisTemplate == null) {
            log.warn("[SessionManager] Redis 不可用，跳过持久化: token={}", token);
            return;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(session);
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + token,
                    json, EXPIRE_HOURS, TimeUnit.HOURS);
            log.info("[SessionManager] Redis 写入成功: token={}, loginName={}, jsonLen={}",
                    token, session.getLoginName(), json.length());
        } catch (Exception e) {
            log.warn("[SessionManager] Redis 写入失败: token={}, error={}", token, e.toString());
        }
    }

    /** 从 Redis 读取并反序列化 UserSession */
    private UserSession loadFromRedis(String token) {
        if (redisTemplate == null) {
            log.debug("[SessionManager] Redis 不可用，跳过加载: token={}", token);
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + token);
            if (json == null || json.isBlank()) {
                log.info("[SessionManager] Redis 中无会话数据: token={}", token);
                return null;
            }
            log.info("[SessionManager] Redis 读取原始数据: token={}, jsonLen={}, preview={}",
                    token, json.length(),
                    json.length() > 80 ? json.substring(0, 80) + "..." : json);
            UserSession session = OBJECT_MAPPER.readValue(json, UserSession.class);
            log.info("[SessionManager] Redis 反序列化成功: token={}, loginName={}",
                    token, session.getLoginName());
            return session;
        } catch (Exception e) {
            log.warn("[SessionManager] Redis 读取失败: token={}, error={}", token, e.toString());
            return null;
        }
    }
}
