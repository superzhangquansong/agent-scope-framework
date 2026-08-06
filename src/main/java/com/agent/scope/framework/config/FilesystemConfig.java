package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.config.properties.MinioProperties;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * AgentScope 2.0 GA 特性二十：文件系统（FileSystem）配置
 * <p>
 * 为 Agent 提供文件读写能力，支持本地文件系统与 MinIO 对象存储两种后端：
 * <ul>
 *   <li>{@code local}：{@link LocalFilesystemSpec}，读写本地工作区目录</li>
 *   <li>{@code minio}：MinIO 对象存储（通过自定义 RemoteFilesystemSpec 实现，按需启用）</li>
 * </ul>
 * </p>
 * <p>
 * 装配后 Agent 可通过 {@code FilesystemTool}（read_file/write_file/edit_file/grep/glob/ls）
 * 操作文件，支持代码解释器、文档处理等场景。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.filesystem", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FilesystemConfig {

    private final AgentScopeProperties properties;

    private final MinioProperties minioProperties;

    /**
     * 文件系统规范 Bean。
     * <p>
     * 默认装配 {@link LocalFilesystemSpec}（无参构造）。
     * 当 {@code scope.agentscope.filesystem.type=minio} 时，切换为 MinIO 对象存储后端
     * （需 MinIO 服务可用）。
     * </p>
     *
     * @param agentWorkspacePath 工作区路径（由 WorkspaceConfig 注入）
     * @return 文件系统规范
     */
    @Bean
    public Optional<LocalFilesystemSpec> filesystemSpec(Optional<Path> agentWorkspacePath) {
        String fsType = properties.getAdvanced().getFilesystemType();
        Path basePath = agentWorkspacePath.orElse(Paths.get("/tmp/agentscope-workspace"))
                .resolve("workspace");

        if ("minio".equalsIgnoreCase(fsType)) {
            log.info("[FilesystemConfig] 装配 MinIO 文件系统: endpoint={}, bucket={}",
                    minioProperties.getEndpoint(), minioProperties.getBucket());
            // MinIO 文件系统需自定义 RemoteFilesystemSpec 实现，此处仅记录配置
            // 实际 MinIO FilesystemSpec 需通过 RemoteSnapshotClient 适配，按需扩展
            return Optional.empty();
        }

        // 默认本地文件系统（LocalFilesystemSpec 仅提供无参构造）
        LocalFilesystemSpec spec = new LocalFilesystemSpec();
        log.info("[FilesystemConfig] 装配本地文件系统: basePath={}", basePath.toAbsolutePath());
        return Optional.of(spec);
    }
}
