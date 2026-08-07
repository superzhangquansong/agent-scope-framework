package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.extensions.sandbox.kubernetes.KubernetesFilesystemSpec;
import io.agentscope.harness.agent.sandbox.snapshot.LocalSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * AgentScope 2.0 GA 特性二十一/三十二：沙箱（Sandbox）+ 沙箱快照与恢复配置
 * <p>
 * 基于 K3s 集群（避免 Docker daemon）安全执行用户上传的 Python/Shell 脚本（如 Code Interpreter），
 * 支持快照与恢复：
 * <ul>
 *   <li>沙箱执行：在独立 K8s Pod 内运行不可信代码，防止宿主机污染</li>
 *   <li>快照持久化：进程重启后可恢复长任务状态</li>
 *   <li>快照后端：本地磁盘（默认）/ OSS / Redis（按需切换）</li>
 * </ul>
 * </p>
 * <p>
 * <b>优雅降级策略</b>：当本地无 kubeconfig 或 K8s 集群不可达时，{@link KubernetesClient}
 * 创建失败不抛异常，{@link #kubernetesFilesystemSpec} 返回空 {@link Optional}，
 * HarnessAgentConfig 自动回退到本地文件系统（{@link LocalFilesystemSpec}）。
 * 这保证开发环境（无 K8s 集群）也能正常启动。
 * </p>
 * <p>
 * <b>实现说明</b>：{@link KubernetesClient} 不单独注册为 Bean（避免 null bean 注入失败），
 * 其创建逻辑内联到 {@link #kubernetesFilesystemSpec} 中，失败时整包降级。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "sandbox-enabled", havingValue = "true")
public class SandboxConfig {

    /**
     * 快照类型 → 工厂函数映射表（策略模式 + 注册表，消除 switch 分支）。
     * <p>新增快照类型只需在此 Map 添加映射项，无需修改 {@link #sandboxSnapshotSpec} 方法。</p>
     */
    private static final Map<String, Function<Path, SandboxSnapshotSpec>> SNAPSHOT_FACTORIES = Map.of(
            "noop", path -> new NoopSnapshotSpec(),
            "oss", path -> new LocalSnapshotSpec(path),
            "local", path -> new LocalSnapshotSpec(path)
    );

    /** 默认快照工厂（类型未匹配时回退） */
    private static final Function<Path, SandboxSnapshotSpec> DEFAULT_FACTORY = SNAPSHOT_FACTORIES.get("local");

    private final AgentScopeProperties properties;

    /**
     * 沙箱快照规范 Bean。
     * <p>
     * 默认装配 {@link LocalSnapshotSpec}，快照存储于工作区下的 {@code snapshots/} 子目录。
     * 当 {@code scope.agentscope.advanced.sandbox-snapshot-type=oss} 时切换为 OSS 远程快照。
     * 当 {@code scope.agentscope.advanced.sandbox-snapshot-type=noop} 时禁用快照（开发调试用）。
     * </p>
     * <p>
     * 该 Bean 通过 {@link KubernetesFilesystemSpec#snapshotSpec(SandboxSnapshotSpec)} 装配到沙箱文件系统，
     * 支持进程重启后从快照恢复长任务状态。
     * </p>
     *
     * @param agentWorkspacePath 工作区路径（由 WorkspaceConfig 注入）
     * @return 沙箱快照规范
     */
    @Bean
    public SandboxSnapshotSpec sandboxSnapshotSpec(Optional<Path> agentWorkspacePath) {
        String snapshotType = properties.getAdvanced().getSandboxSnapshotType().toLowerCase();
        Path basePath = agentWorkspacePath.orElse(Paths.get("/tmp/agentscope-workspace"))
                .resolve("snapshots");

        // 策略模式：按类型从映射表查找工厂函数，默认回退到本地快照
        Function<Path, SandboxSnapshotSpec> factory = SNAPSHOT_FACTORIES.getOrDefault(snapshotType, DEFAULT_FACTORY);
        SandboxSnapshotSpec spec = factory.apply(basePath);

        if ("oss".equals(snapshotType)) {
            log.warn("[SandboxConfig] OSS 快照需 agentscope-extensions-oss 依赖，降级为本地快照");
        }
        log.info("[SandboxConfig] 装配快照规范 {}: basePath={}", spec.getClass().getSimpleName(), basePath.toAbsolutePath());

        return spec;
    }

    /**
     * Kubernetes 沙箱文件系统规范 Bean（特性21/32）。
     * <p>
     * 装配 {@link KubernetesFilesystemSpec}，将不可信代码（Shell/Python）隔离在 K3s Pod 内执行。
     * </p>
     * <p>
     * <b>优雅降级</b>：当本地无 kubeconfig 或 fabric8 依赖缺失时，{@link KubernetesClientBuilder#build()}
     * 抛 {@link NoClassDefFoundError} 或其他异常，此时不抛出，而是返回空 {@link Optional}，
     * HarnessAgentConfig 自动回退到 {@link LocalFilesystemSpec}，保证开发环境可启动。
     * </p>
     * <p>
     * {@link KubernetesClient} 不单独注册为 Bean，避免 null bean 注入失败（Spring 不允许 @Bean 返回 null）。
     * 创建逻辑内联在此方法中，与 {@link KubernetesFilesystemSpec} 生命周期绑定。
     * </p>
     *
     * @param sandboxSnapshotSpec 沙箱快照规范（由 {@link #sandboxSnapshotSpec} 注入）
     * @return 包装在 Optional 中的 KubernetesFilesystemSpec；K8s 不可用时返回空 Optional
     */
    @Bean
    public Optional<KubernetesFilesystemSpec> kubernetesFilesystemSpec(
            SandboxSnapshotSpec sandboxSnapshotSpec) {
        // 尝试创建 KubernetesClient，失败则优雅降级
        KubernetesClient kubernetesClient;
        try {
            kubernetesClient = new KubernetesClientBuilder().build();
            log.info("[SandboxConfig] 已创建 KubernetesClient: namespace={}", kubernetesClient.getNamespace());
        } catch (Throwable e) {
            // 捕获 Throwable 兜住 NoClassDefFoundError（fabric8 依赖缺失时抛出），
            // 也兜住 IllegalStateException（无 kubeconfig 时抛出）
            log.warn("[SandboxConfig] KubernetesClient 创建失败，沙箱降级为本地文件系统: {}",
                    e.getMessage());
            return Optional.empty();
        }

        try {
            AgentScopeProperties.Advanced advanced = properties.getAdvanced();

            KubernetesFilesystemSpec spec = new KubernetesFilesystemSpec()
                    .kubernetesClient(kubernetesClient)
                    .namespace(advanced.getSandboxNamespace())
                    .image(advanced.getSandboxImage())
                    .workspaceRoot(advanced.getSandboxWorkspaceRoot())
                    .containerName(advanced.getSandboxContainerName())
                    .serviceAccount(advanced.getSandboxServiceAccount())
                    .cpuRequest(advanced.getSandboxCpuRequest())
                    .memoryRequest(advanced.getSandboxMemoryRequest())
                    .snapshotSpec(sandboxSnapshotSpec);

            log.info("[SandboxConfig] 装配 KubernetesFilesystemSpec: namespace={}, image={}, workspaceRoot={}, containerName={}, snapshot={}",
                    advanced.getSandboxNamespace(),
                    advanced.getSandboxImage(),
                    advanced.getSandboxWorkspaceRoot(),
                    advanced.getSandboxContainerName(),
                    sandboxSnapshotSpec.getClass().getSimpleName());

            return Optional.of(spec);
        } catch (Exception e) {
            log.warn("[SandboxConfig] 装配 KubernetesFilesystemSpec 失败，降级为本地文件系统: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }
}
