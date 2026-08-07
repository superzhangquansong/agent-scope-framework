package com.agent.scope.framework.config;

import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope 2.0 GA 特性四十六：配置版本管理配置
 * <p>
 * 通过 Nacos 配置中心 OpenAPI 实现配置的版本管理与回滚：
 * <ul>
 *   <li>版本追踪：查询配置的历史版本列表</li>
 *   <li>版本详情：获取特定版本的配置内容</li>
 *   <li>版本回滚：回滚到任意历史版本</li>
 *   <li>快照记录：本地记录配置变更快照，便于审计</li>
 * </ul>
 * </p>
 * <p>
 * Nacos 版本管理 OpenAPI：
 * <ul>
 *   <li>{@code GET  /nacos/v1/cs/history?search=accurate&dataId=&group=&pageNo=&pageSize=}：查询历史版本列表</li>
 *   <li>{@code GET  /nacos/v1/cs/history?dataId=&group=&nid=}：获取特定版本内容</li>
 *   <li>{@code POST /nacos/v1/cs/history?dataId=&group=&nid=}：回滚到指定版本</li>
 * </ul>
 * </p>
 * <p>
 * Nacos 连接信息从 Spring Environment 读取（spring.cloud.nacos.config.*），
 * 与应用配置共享同一 Nacos 实例，无需额外配置。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "scope.agentscope.config-version", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConfigVersionConfig {

    /**
     * RestTemplate Bean，用于调用 Nacos OpenAPI。
     *
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate nacosRestTemplate() {
        return new RestTemplate();
    }

    /**
     * 配置版本管理器 Bean。
     * <p>
     * 集成 Nacos 配置中心版本管理能力，通过 OpenAPI 实现版本查询与回滚。
     * </p>
     *
     * @param environment Spring 环境（读取 Nacos 连接信息）
     * @param restTemplate HTTP 客户端（调用 Nacos OpenAPI）
     * @return 配置版本管理器
     */
    @Bean
    public ConfigVersionManager configVersionManager(Environment environment,
                                                     RestTemplate nacosRestTemplate) {
        ConfigVersionManager manager = new ConfigVersionManager(environment, nacosRestTemplate);
        log.info("[ConfigVersionConfig] 配置版本管理器已装配: serverAddr={}, namespace={}, dataId={}",
                manager.getServerAddr(), manager.getNamespace(), BusinessConst.NACOS_CONFIG_DATA_ID);
        return manager;
    }

    /**
     * 配置版本管理器。
     * <p>
     * 封装 Nacos 配置版本管理 OpenAPI，提供业务友好的 API。
     * </p>
     */
    @Slf4j
    public static class ConfigVersionManager {

        private final Environment environment;
        private final RestTemplate restTemplate;
        private final Map<String, String> configSnapshots = new ConcurrentHashMap<>();

        /** Nacos 服务器地址（如 127.0.0.1:30104） */
        private final String serverAddr;
        /** Nacos 命名空间 ID */
        private final String namespace;
        /** Nacos 配置组 */
        private final String group;
        /** Nacos 用户名 */
        private final String username;
        /** Nacos 密码 */
        private final String password;

        public ConfigVersionManager(Environment environment, RestTemplate restTemplate) {
            this.environment = environment;
            this.restTemplate = restTemplate;
            this.serverAddr = environment.getProperty(
                    "spring.cloud.nacos.config.server-addr", "127.0.0.1:8848");
            this.namespace = environment.getProperty(
                    "spring.cloud.nacos.config.namespace", "public");
            this.group = environment.getProperty(
                    "spring.cloud.nacos.config.group", "DEFAULT_GROUP");
            this.username = environment.getProperty(
                    "spring.cloud.nacos.config.username", "");
            this.password = environment.getProperty(
                    "spring.cloud.nacos.config.password", "");
        }

        /**
         * 查询配置历史版本列表。
         *
         * @param pageNo   页码（从 1 开始）
         * @param pageSize 每页条数
         * @return Nacos 返回的历史版本 JSON（包含 totalCount 和 pageItems）
         */
        public String queryHistory(int pageNo, int pageSize) {
            String url = buildHistoryListUrl(pageNo, pageSize);
            log.info("[ConfigVersion] 查询配置历史版本: pageNo={}, pageSize={}", pageNo, pageSize);
            try {
                HttpHeaders headers = buildAuthHeaders();
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                log.info("[ConfigVersion] 查询历史版本成功: statusCode={}", resp.getStatusCode());
                return resp.getBody();
            } catch (Exception e) {
                log.error("[ConfigVersion] 查询历史版本失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.CONFIG_VERSION_QUERY_FAILED,
                        "查询配置历史版本失败: " + e.getMessage(), e);
            }
        }

        /**
         * 获取特定版本的配置内容。
         *
         * @param nid 历史版本 ID（Nacos 内部标识）
         * @return Nacos 返回的版本详情 JSON
         */
        public String getVersion(String nid) {
            String url = buildHistoryDetailUrl(nid);
            log.info("[ConfigVersion] 获取配置版本详情: nid={}", nid);
            try {
                HttpHeaders headers = buildAuthHeaders();
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                log.info("[ConfigVersion] 获取版本详情成功: nid={}, statusCode={}", nid, resp.getStatusCode());
                return resp.getBody();
            } catch (Exception e) {
                log.error("[ConfigVersion] 获取版本详情失败: nid={}, error={}", nid, e.getMessage());
                throw new BusinessException(ErrorCode.CONFIG_VERSION_QUERY_FAILED,
                        "获取配置版本详情失败: nid=" + nid, e);
            }
        }

        /**
         * 回滚到指定历史版本。
         *
         * @param nid 历史版本 ID
         * @return Nacos 返回的回滚结果 JSON
         */
        public String rollback(String nid) {
            String url = buildRollbackUrl(nid);
            log.info("[ConfigVersion] 回滚配置版本: nid={}", nid);
            try {
                HttpHeaders headers = buildAuthHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                log.info("[ConfigVersion] 回滚版本成功: nid={}, statusCode={}", nid, resp.getStatusCode());
                return resp.getBody();
            } catch (Exception e) {
                log.error("[ConfigVersion] 回滚版本失败: nid={}, error={}", nid, e.getMessage());
                throw new BusinessException(ErrorCode.CONFIG_VERSION_ROLLBACK_FAILED,
                        "回滚配置版本失败: nid=" + nid, e);
            }
        }

        /**
         * 记录配置快照（本地审计）。
         *
         * @param configKey   配置键
         * @param configValue 配置值
         */
        public void recordSnapshot(String configKey, String configValue) {
            configSnapshots.put(configKey, configValue);
            log.debug("[ConfigVersion] 配置快照已记录: key={}", configKey);
        }

        /**
         * 获取本地配置快照。
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
            return String.format("profiles=%s, namespace=%s, group=%s, dataId=%s, serverAddr=%s",
                    String.join(",", environment.getActiveProfiles()),
                    namespace, group, BusinessConst.NACOS_CONFIG_DATA_ID, serverAddr);
        }

        /** 获取 Nacos 服务器地址 */
        public String getServerAddr() {
            return serverAddr;
        }

        /** 获取 Nacos 命名空间 */
        public String getNamespace() {
            return namespace;
        }

        // ==================== 内部工具方法 ====================

        /**
         * 构建查询历史版本列表的 URL。
         *
         * @param pageNo   页码
         * @param pageSize 每页条数
         * @return 完整 URL
         */
        private String buildHistoryListUrl(int pageNo, int pageSize) {
            return String.format("http://%s%s?search=accurate&dataId=%s&group=%s&namespaceId=%s&pageNo=%d&pageSize=%d",
                    serverAddr, BusinessConst.NACOS_HISTORY_API_PATH,
                    BusinessConst.NACOS_CONFIG_DATA_ID, group, namespace, pageNo, pageSize);
        }

        /**
         * 构建获取版本详情的 URL。
         *
         * @param nid 历史版本 ID
         * @return 完整 URL
         */
        private String buildHistoryDetailUrl(String nid) {
            return String.format("http://%s%s?dataId=%s&group=%s&namespaceId=%s&nid=%s",
                    serverAddr, BusinessConst.NACOS_HISTORY_API_PATH,
                    BusinessConst.NACOS_CONFIG_DATA_ID, group, namespace, nid);
        }

        /**
         * 构建回滚版本的 URL。
         *
         * @param nid 历史版本 ID
         * @return 完整 URL
         */
        private String buildRollbackUrl(String nid) {
            return String.format("http://%s%s?dataId=%s&group=%s&namespaceId=%s&nid=%s",
                    serverAddr, BusinessConst.NACOS_HISTORY_API_PATH,
                    BusinessConst.NACOS_CONFIG_DATA_ID, group, namespace, nid);
        }

        /**
         * 构建 Nacos 认证请求头。
         * <p>
         * Nacos 2.x 支持 Basic Auth 认证，将 username:password 进行 Base64 编码后放入 Authorization 头。
         * 若未配置用户名密码则返回空请求头（兼容 Nacos 未开启认证的场景）。
         * </p>
         *
         * @return 包含认证信息的 HTTP 请求头
         */
        private HttpHeaders buildAuthHeaders() {
            HttpHeaders headers = new HttpHeaders();
            if (username != null && !username.isBlank()) {
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                headers.set("Authorization", "Basic " + encodedAuth);
            }
            return headers;
        }
    }
}
