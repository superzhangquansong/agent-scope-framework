package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * AgentScope 2.0 GA 特性二十一/三十二：沙箱（Sandbox）+ 沙箱快照与恢复配置
 * <p>
 * 安全执行用户上传的 Python/Shell 脚本（如 Code Interpreter），支持快照与恢复：
 * <ul>
 *   <li>沙箱执行：隔离环境运行不可信代码，防止宿主机污染</li>
 *   <li>快照持久化：进程重启后可恢复长任务状态</li>
 *   <li>快照后端：本地磁盘（默认）/ OSS / Redis（按需切换）</li>
 * </ul>
 * </p>
 * <p>
 * 快照规范通过 {@link SandboxSnapshotSpec} 接口抽象：
 * <ul>
 *   <li>{@link LocalSnapshotSpec}：本地磁盘快照，文件存于 {basePath}/{snapshotId}.tar</li>
 *   <li>{@link NoopSnapshotSpec}：空快照，不持久化（开发调试用）</li>
 *   <li>{@code OssSnapshotSpec}：阿里云 OSS 远程快照（需 OSS 扩展包）</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.sandbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SandboxConfig {

    private final AgentScopeProperties properties;

    /**
     * 沙箱快照规范 Bean。
     * <p>
     * 默认装配 {@link LocalSnapshotSpec}，快照存储于工作区下的 {@code snapshots/} 子目录。
     * 当 {@code scope.agentscope.sandbox.snapshot-type=oss} 时切换为 OSS 远程快照。
     * 当 {@code scope.agentscope.sandbox.snapshot-type=noop} 时禁用快照（开发调试用）。
     * </p>
     *
     * @param agentWorkspacePath 工作区路径（由 WorkspaceConfig 注入）
     * @return 沙箱快照规范
     */
    @Bean
    public Optional<SandboxSnapshotSpec> sandboxSnapshotSpec(Optional<Path> agentWorkspacePath) {
        String snapshotType = properties.getAdvanced().getSandboxSnapshotType();
        Path basePath = agentWorkspacePath.orElse(Paths.get("/tmp/agentscope-workspace"))
                .resolve("snapshots");

        SandboxSnapshotSpec spec;
        switch (snapshotType.toLowerCase()) {
            case "noop" -> {
                spec = new NoopSnapshotSpec();
                log.info("[SandboxConfig] 装配 NoopSnapshotSpec（不持久化快照）");
            }
            case "oss" -> {
                // OSS 快照需 OssSnapshotSpec 扩展包，此处降级为本地快照并告警
                log.warn("[SandboxConfig] OSS 快照需 agentscope-extensions-oss 依赖，降级为本地快照");
                spec = new LocalSnapshotSpec(basePath);
                log.info("[SandboxConfig] 装配 LocalSnapshotSpec（降级）: basePath={}", basePath.toAbsolutePath());
            }
            default -> {
                spec = new LocalSnapshotSpec(basePath);
                log.info("[SandboxConfig] 装配 LocalSnapshotSpec: basePath={}", basePath.toAbsolutePath());
            }
        }
        return Optional.of(spec);
    }
}
