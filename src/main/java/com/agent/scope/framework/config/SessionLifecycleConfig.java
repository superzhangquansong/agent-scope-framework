package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.state.AgentStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * AgentScope 2.0 GA 特性三十/三十一：会话生命周期管理 + 多租户隔离配置
 * <p>
 * 会话生命周期管理：
 * <ul>
 *   <li>创建：首次 call() 时自动创建 AgentState</li>
 *   <li>恢复：相同 (userId, sessionId) 自动从 StateStore 恢复</li>
 *   <li>持久化：call() 结束后自动写入 StateStore</li>
 *   <li>销毁：通过 StateStore.delete(userId, sessionId) 显式销毁</li>
 *   <li>超时：基于 Redis TTL 自动过期（需 Redis 配置 TTL）</li>
 * </ul>
 * </p>
 * <p>
 * 多租户隔离层级：
 * <ul>
 *   <li>session 级：同一用户不同会话完全隔离</li>
 *   <li>user 级：不同用户完全隔离</li>
 *   <li>agent 级：不同 Agent 实例隔离（通过 agentId 区分）</li>
 *   <li>org 级：组织级隔离（通过 orgId 前缀，按需扩展）</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.session", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SessionLifecycleConfig {

    private final AgentScopeProperties properties;

    private final Optional<AgentStateStore> agentStateStore;

    /**
     * 会话生命周期管理器 Bean。
     * <p>
     * 提供会话的显式管理操作（创建/销毁/超时/列表），封装 StateStore 的底层操作。
     * 多租户隔离通过 (userId, sessionId) 二元组自动实现，
     * org 级隔离通过在 userId 前添加 orgId 前缀扩展。
     * </p>
     *
     * @return 会话生命周期管理器
     */
    @Bean
    public SessionLifecycleManager sessionLifecycleManager() {
        SessionLifecycleManager manager = new SessionLifecycleManager(agentStateStore.orElse(null));
        log.info("[SessionLifecycleConfig] 会话生命周期管理器已装配: stateStore={}",
                agentStateStore.isPresent() ? "present" : "absent");
        return manager;
    }

    /**
     * 会话生命周期管理器。
     * <p>
     * 封装 AgentStateStore 的会话管理操作，提供业务友好的 API。
     * </p>
     */
    @Slf4j
    public static class SessionLifecycleManager {

        private final AgentStateStore stateStore;

        public SessionLifecycleManager(AgentStateStore stateStore) {
            this.stateStore = stateStore;
        }

        /**
         * 销毁指定会话。
         * <p>
         * 从 StateStore 中删除指定 (userId, sessionId) 的所有状态，
         * 释放存储空间。常用于用户主动结束会话或超时清理。
         * </p>
         *
         * @param userId    用户 ID
         * @param sessionId 会话 ID
         */
        public void destroySession(String userId, String sessionId) {
            if (stateStore == null) {
                log.warn("[SessionLifecycle] StateStore 未启用，无法销毁会话");
                return;
            }
            try {
                stateStore.delete(userId, sessionId);
                log.info("[SessionLifecycle] 会话已销毁: userId={}, sessionId={}", userId, sessionId);
            } catch (Exception e) {
                log.error("[SessionLifecycle] 销毁会话失败: userId={}, sessionId={}, error={}",
                        userId, sessionId, e.getMessage());
            }
        }

        /**
         * 列出指定用户的所有会话 ID。
         *
         * @param userId 用户 ID
         * @return 会话 ID 集合
         */
        public java.util.Set<String> listSessions(String userId) {
            if (stateStore == null) {
                return java.util.Collections.emptySet();
            }
            try {
                return stateStore.listSessionIds(userId);
            } catch (Exception e) {
                log.error("[SessionLifecycle] 列出会话失败: userId={}, error={}", userId, e.getMessage());
                return java.util.Collections.emptySet();
            }
        }

        /**
         * 检查会话是否存在。
         *
         * @param userId    用户 ID
         * @param sessionId 会话 ID
         * @return true=存在
         */
        public boolean sessionExists(String userId, String sessionId) {
            if (stateStore == null) {
                return false;
            }
            try {
                return stateStore.exists(userId, sessionId);
            } catch (Exception e) {
                log.error("[SessionLifecycle] 检查会话存在性失败: userId={}, sessionId={}, error={}",
                        userId, sessionId, e.getMessage());
                return false;
            }
        }
    }
}
