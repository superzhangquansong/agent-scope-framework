package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
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
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "plan-mode-enabled", havingValue = "true")
public class PlanModeConfig {

    private final AgentScopeProperties properties;

    /**
     * 计划模式管理器 Bean。
     * <p>
     * 协调计划模式的状态转换，计划文件持久化于工作区 {@code plans/} 子目录。
     * </p>
     *
     * @param agentWorkspacePath 工作区路径
     * @return 计划模式管理器（可能为空，表示工作区未启用）
     */
    @Bean
    public Optional<PlanModeManager> planModeManager(Optional<Path> agentWorkspacePath) {
        if (agentWorkspacePath.isEmpty()) {
            log.warn("[PlanModeConfig] 工作区未启用，计划模式无法装配");
            return Optional.empty();
        }
        Path planDir = agentWorkspacePath.get().resolve("plans");
        WorkspaceManager workspaceManager = new WorkspaceManager(agentWorkspacePath.get());
        PlanModeManager manager = new PlanModeManager(workspaceManager, "plans");
        log.info("[PlanModeConfig] 计划模式管理器已装配: planDir={}", planDir.toAbsolutePath());
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
        // 只读工具判定器：工具名以 read_/query_/list_/get_/search_ 开头视为只读
        PlanModeMiddleware middleware = new PlanModeMiddleware(planModeManager.get(),
                toolName -> toolName != null && (toolName.startsWith("read_")
                        || toolName.startsWith("query_")
                        || toolName.startsWith("list_")
                        || toolName.startsWith("get_")
                        || toolName.startsWith("search_")));
        log.info("[PlanModeConfig] 计划模式中间件已装配");
        return Optional.of(middleware);
    }
}
