package com.agent.scope.framework.config;

import com.agent.scope.framework.config.infrastructure.MinioBaseStore;
import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AgentScope 2.0 GA 特性三十五：分布式工作区文件系统配置
 * <p>
 * 三种模式（按优先级从高到低）：
 * <ul>
 *   <li><b>minio</b>（生产首选）：{@link MinioBaseStore} + {@link RemoteFilesystemSpec}，
 *       按 {@link IsolationScope#USER} 隔离，工作区文件持久化到 MinIO 对象存储。
 *       按 TB 计费，远比 Redis 内存便宜，适合 MEMORY.md / memory/*.md 等长期累积文件。
 *       通过 {@code scope.agentscope.minio.enabled=true} 开启。</li>
 *   <li><b>redis</b>（默认）：{@link RemoteFilesystemSpec} + Redis {@link BaseStore}，
 *       与 {@link StateStoreConfig} 共享同一份 RedisDistributedStore 后端。
 *       适合快速部署、数据量不大的场景。</li>
 *   <li><b>local</b>（开发降级）：{@link LocalFilesystemSpec}，单机本地磁盘存储。
 *       仅适合单进程开发调试，多副本部署时各 Pod 独立工作区、记忆不共享。</li>
 * </ul>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FilesystemConfig {

    private final AgentScopeProperties properties;

    /**
     * MinIO 分布式文件系统模式（生产首选）。
     * <p>
     * 使用 {@link MinioBaseStore} 实现 BaseStore 接口，工作区文件（MEMORY.md /
     * memory/*.md / sessions/*.log.jsonl 等）持久化到 MinIO 对象存储。
     * 按 {@link IsolationScope#USER} 隔离命名空间，使同一用户跨会话、跨副本共享长期记忆。
     * </p>
     * <p>
     * 标注 @Primary，当 minio.enabled=true 时优先于 redis 模式的 RemoteFilesystemSpec。
     * </p>
     *
     * @return RemoteFilesystemSpec 实例（MinIO 后端）
     */
    @Bean
    @ConditionalOnProperty(prefix = "scope.agentscope.minio", name = "enabled", havingValue = "true")
    @Primary
    public RemoteFilesystemSpec minioFilesystemSpec() {
        AgentScopeProperties.Minio minioCfg = properties.getMinio();
        MinioBaseStore minioStore = new MinioBaseStore(
                minioCfg.getEndpoint(),
                minioCfg.getAccessKey(),
                minioCfg.getSecretKey(),
                minioCfg.getBucket(),
                minioCfg.getConnectTimeoutMs(),
                minioCfg.getReadTimeoutMs());
        RemoteFilesystemSpec spec = new RemoteFilesystemSpec(minioStore)
                .isolationScope(IsolationScope.USER);
        log.info("[FilesystemConfig] 创建 MinioBaseStore + RemoteFilesystemSpec: endpoint={}, bucket={}, IsolationScope.USER",
                minioCfg.getEndpoint(), minioCfg.getBucket());
        return spec;
    }

    /**
     * Redis 分布式文件系统模式（默认）。
     * <p>
     * 使用 {@link RemoteFilesystemSpec} + Redis {@link BaseStore}，按 {@link IsolationScope#USER}
     * 隔离命名空间，使同一用户跨会话、跨副本共享同一份长期记忆（MEMORY.md）。
     * </p>
     * <p>
     * BaseStore 由 {@link StateStoreConfig#baseStore} 提供，与 AgentStateStore 共享同一份 RedisDistributedStore。
     * 当 minio.enabled=true 时被 @Primary 的 minioFilesystemSpec 覆盖，此 Bean 仍创建但不被装配。
     * </p>
     *
     * @param baseStore Redis 文件存储后端
     * @return RemoteFilesystemSpec 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "scope.agentscope.filesystem", name = "type", havingValue = "redis", matchIfMissing = true)
    public RemoteFilesystemSpec redisFilesystemSpec(BaseStore baseStore) {
        RemoteFilesystemSpec spec = new RemoteFilesystemSpec(baseStore)
                .isolationScope(IsolationScope.USER);
        log.info("[FilesystemConfig] 创建 Redis BaseStore + RemoteFilesystemSpec（IsolationScope.USER）");
        return spec;
    }

    /**
     * 本地文件系统模式（开发降级）。
     * <p>
     * 单机开发场景使用，工作区文件直接读写本地磁盘。
     * 生产环境必须使用 minio 或 redis 模式，否则多副本记忆不共享。
     * </p>
     *
     * @return LocalFilesystemSpec 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "scope.agentscope.filesystem", name = "type", havingValue = "local")
    public LocalFilesystemSpec localFilesystemSpec() {
        log.info("[FilesystemConfig] 创建 LocalFilesystemSpec（本地模式，仅开发用）");
        return new LocalFilesystemSpec();
    }

}
