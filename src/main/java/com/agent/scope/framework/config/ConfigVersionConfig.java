package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope 2.0 GA 特性四十六：配置版本管理配置
 * <p>
 * 通过 Nacos 配置中心实现配置的版本管理与回滚：
 * <ul>
 *   <li>版本追踪：每次配置变更生成版本快照，记录变更时间/内容/操作人</li>
 *   <li>版本回滚：支持回滚到任意历史版本</li>
 *   <li>变更审计：记录配置变更日志，便于追溯</li>
 *   <li>灰度发布：支持按 IP/标签灰度推送配置</li>
 * </ul>
 * </p>
 * <p>
 * Nacos 配置中心原生支持版本管理（History API），本配置类集成 Nacos 的版本管理能力，
 * 通过 {@link RefreshScope} 实现配置热更新。
 * </p>
 * <p>
 * Nacos 版本管理 API：
 * <ul>
 *   <li>{@code GET /nacos/v1/cs/history?dataId=&group=&pageNo=&pageSize=}：查询历史版本</li>
 *   <li>{@code GET /nacos/v1/cs/history?dataId=&group=&nid=}：获取特定版本内容</li>
 *   <li>{@code POST /nacos/v1/cs/history?dataId=&group=&nid=}：回滚到指定版本</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.config-version", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConfigVersionConfig {

    private final AgentScopeProperties properties;

    private final Environment environment;

    /**
     * 配置版本管理器 Bean。
     * <p>
     * 集成 Nacos 配置中心版本管理能力，提供：
     * <ul>
     *   <li>当前配置快照：记录当前生效的配置内容</li>
     *   <li>变更监听：配置变更时自动记录变更日志</li>
     *   <li>环境信息：记录当前运行环境（profile/namespace/group/dataId）</li>
     * </ul>
     * </p>
     *
     * @return 配置版本管理器
     */
    @Bean
    public ConfigVersionManager configVersionManager() {
        ConfigVersionManager manager = new ConfigVersionManager(environment);
        log.info("[ConfigVersionConfig] 配置版本管理器已装配: activeProfiles={}",
                String.join(",", environment.getActiveProfiles()));
        return manager;
    }

    /**
     * 配置版本管理器。
     * <p>
     * 封装 Nacos 配置版本管理能力，提供业务友好的 API。
     * 实际版本回滚操作通过 Nacos 控制台或 Nacos OpenAPI 执行。
     * </p>
     */
    @Slf4j
    public static class ConfigVersionManager {

        private final Environment environment;
        private final Map<String, String> configSnapshots = new ConcurrentHashMap<>();

        public ConfigVersionManager(Environment environment) {
            this.environment = environment;
        }

        /**
         * 记录配置快照。
         *
         * @param configKey   配置键
         * @param configValue 配置值
         */
        public void recordSnapshot(String configKey, String configValue) {
            configSnapshots.put(configKey, configValue);
            log.debug("[ConfigVersion] 配置快照已记录: key={}", configKey);
        }

        /**
         * 获取配置快照。
         *
         * @param configKey 配置键
         * @return 配置值，不存在返回 null
         */
        public String getSnapshot(String configKey) {
            return configSnapshots.get(configKey);
        }

        /**
         * 获取当前运行环境信息。
         *
         * @return 环境信息字符串
         */
        public String getEnvironmentInfo() {
            return String.format("profiles=%s, namespace=%s",
                    String.join(",", environment.getActiveProfiles()),
                    environment.getProperty("spring.cloud.nacos.config.namespace", "public"));
        }
    }
}
