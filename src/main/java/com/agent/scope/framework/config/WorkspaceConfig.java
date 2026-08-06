package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AgentScope 2.0 GA 特性十八：工作区（Workspace）配置
 * <p>
 * Agent 执行时拥有独立临时工作目录，支持跨 Agent 共享。
 * 工作区目录包含：
 * - AGENTS.md：人格 + 行为约定（静态资产）
 * - MEMORY.md：长期记忆（跨 session 累积）
 * - knowledge/：领域知识文件
 * - skills/：技能文件
 * - subagents/：子 Agent 声明
 * - plans/：计划文件
 * - agents/<agentId>/：运行时状态
 * </p>
 * <p>
 * <b>关键约束</b>：项目运行期间严禁生成 .agentscope 本地文件夹。
 * 工作区路径通过配置项 scope.agentscope.workspace.path 指定，
 * 默认使用系统临时目录 /tmp/agentscope-workspace。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.workspace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkspaceConfig {

    private final AgentScopeProperties properties;

    /**
     * 工作区路径 Bean
     * <p>
     * 提供工作区目录路径供 HarnessAgent.Builder.workspace() 使用。
     * 路径通过 scope.agentscope.workspace.path 配置项指定，
     * 默认值为系统临时目录下的 agentscope-workspace。
     * </p>
     * <p>
     * 严禁使用项目根目录下的 .agentscope 文件夹。
     * </p>
     *
     * @return 工作区 Path 对象
     */
    @Bean("agentWorkspacePath")
    public Path agentWorkspacePath() {
        String workspacePath = properties.getWorkspace().getPath();
        Path path = Paths.get(workspacePath);
        log.info("[WorkspaceConfig] 工作区路径配置: path={}", path.toAbsolutePath());

        // 确保不是 .agentscope 目录
        if (path.toString().contains(".agentscope")) {
            log.warn("[WorkspaceConfig] 工作区路径包含 .agentscope，已自动重定向到系统临时目录");
            path = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-workspace");
            log.info("[WorkspaceConfig] 重定向后路径: path={}", path.toAbsolutePath());
        }

        return path;
    }
}
