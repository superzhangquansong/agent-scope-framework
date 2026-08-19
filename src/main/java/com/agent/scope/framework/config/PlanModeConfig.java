package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.middleware.PlanModeMiddleware;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import io.agentscope.harness.agent.workspace.plan.PlanModeManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * AgentScope 2.0 GA 特性二十三/三十五：计划模式（Plan Mode）+ PlanNotebook 配置
 * <p>
 * 复杂任务先规划（Plan），再拆分执行（Execute），通过 {@link PlanNotebook} 管理。
 * 计划模式采用动态运行时切换：
 * <ul>
 *   <li>{@code plan_enter}：进入只读计划模式，仅允许只读工具</li>
 *   <li>{@code plan_write}：创建/覆盖计划文件（workspace/plans/）</li>
 *   <li>{@code plan_exit}：退出计划模式（需权限审批），恢复全部工具</li>
 * </ul>
 * </p>
 * <p>
 * 状态持久化于 {@link io.agentscope.core.state.PlanModeContextState}，支持分布式场景。
 * {@link PlanModeMiddleware} 在 {@code onActing} 阶段确定性拦截变更工具。
 * </p>
 * <p>
 * <b>分布式改造（MinIO）</b>：计划模式管理器不再使用 {@code new WorkspaceManager(本地path)} 纯本地模式，
 * 而是优先通过 {@link RemoteFilesystemSpec#toFilesystem} 构建基于 MinIO BaseStore 的
 * {@link AbstractFilesystem}，使 {@code plans/} 计划文件持久化到 MinIO，多副本共享。
 * 未装配分布式文件系统时降级为本地磁盘（开发环境）。
 * </p>
 * <p>
 * 依赖注入说明：此处注入 {@link RemoteFilesystemSpec}（由 FilesystemConfig 装配，MinIO/Redis BaseStore），
 * 而非 HarnessAgent —— 避免与 HarnessAgentConfig（scopeHarnessAgent → Optional&lt;PlanModeManager&gt;）
 * 产生循环依赖。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "plan-mode-enabled", havingValue = "true")
public class PlanModeConfig {

    /** 只读工具名前缀集合（计划模式下仅允许这些前缀的工具执行） */
    private static final Set<String> READ_ONLY_PREFIXES = Set.of(
            "read_", "query_", "list_", "get_", "search_"
    );

    private final AgentScopeProperties properties;

    /**
     * 计划模式管理器 Bean。
     * <p>
     * 协调计划模式的状态转换，计划文件持久化于工作区 {@code plans/} 子目录。
     * 优先使用 MinIO 分布式文件系统（多副本共享），降级本地磁盘（开发环境）。
     * </p>
     *
     * @param agentWorkspacePath  工作区路径（由 WorkspaceConfig 注入）
     * @param remoteFilesystemSpec 分布式文件系统规范（由 FilesystemConfig 注入，MinIO/Redis BaseStore）
     * @return 计划模式管理器（可能为空，表示工作区未启用）
     */
    @Bean
    public Optional<PlanModeManager> planModeManager(Optional<Path> agentWorkspacePath,
                                                     Optional<RemoteFilesystemSpec> remoteFilesystemSpec) {
        if (agentWorkspacePath.isEmpty()) {
            log.warn("[PlanModeConfig] 工作区未启用，计划模式无法装配");
            return Optional.empty();
        }

        Path workspacePath = agentWorkspacePath.get();
        WorkspaceManager workspaceManager;
        if (remoteFilesystemSpec.isPresent()) {
            // 分布式模式：toFilesystem 构建基于 MinIO BaseStore 的 AbstractFilesystem，
            // WorkspaceManager 两层读（MinIO 覆盖层 + 本地兜底层），plans/ 持久化到 MinIO
            AbstractFilesystem filesystem = remoteFilesystemSpec.get().toFilesystem(
                    workspacePath, properties.getHarnessAgentName(), null);
            workspaceManager = new WorkspaceManager(workspacePath, filesystem);
            log.info("[PlanModeConfig] 计划模式管理器已装配（分布式文件系统）: planDir={}",
                    workspacePath.resolve("plans").toAbsolutePath());
        } else {
            // 降级：本地磁盘模式（开发环境）
            workspaceManager = new WorkspaceManager(workspacePath);
            log.warn("[PlanModeConfig] 计划模式管理器已装配（本地降级，未装配分布式文件系统）: planDir={}",
                    workspacePath.resolve("plans").toAbsolutePath());
        }

        PlanModeManager manager = new PlanModeManager(workspaceManager, "plans");
        return Optional.of(manager);
    }

    /**
     * 计划模式中间件 Bean。
     * <p>
     * 在 {@code onSystemPrompt} 注入计划模式提示词，在 {@code onActing} 拦截变更工具。
     * 只读工具判定通过 {@code readOnlyResolver} 实现（默认 read_file/ls/grep 等为只读）。
     * </p>
     *
     * @param planModeManager 计划模式管理器
     * @return 计划模式中间件（可能为空）
     */
    @Bean
    public Optional<PlanModeMiddleware> planModeMiddleware(Optional<PlanModeManager> planModeManager) {
        if (planModeManager.isEmpty()) {
            return Optional.empty();
        }
        // 只读工具判定器：工具名以 READ_ONLY_PREFIXES 中任一前缀开头视为只读
        PlanModeMiddleware middleware = new PlanModeMiddleware(planModeManager.get(),
                toolName -> toolName != null
                        && READ_ONLY_PREFIXES.stream().anyMatch(toolName::startsWith));
        log.info("[PlanModeConfig] 计划模式中间件已装配");
        return Optional.of(middleware);
    }
}
