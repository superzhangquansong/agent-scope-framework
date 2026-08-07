package com.agent.scope.framework.config;

import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.tool.MemoryGetTool;
import io.agentscope.harness.agent.tool.MemorySaveTool;
import io.agentscope.harness.agent.tool.MemorySearchTool;
import io.agentscope.harness.agent.tool.SessionSearchTool;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Optional;

/**
 * AgentScope 2.0 GA 特性十九：分布式记忆（Memory Tools）配置
 * <p>
 * 启用长期记忆工具集，支持跨会话知识积累与检索：
 * <ul>
 *   <li>{@code memory_search}：语义搜索长期记忆（向量化检索）</li>
 *   <li>{@code memory_get}：按 ID 获取特定记忆条目</li>
 *   <li>{@code memory_save}：保存当前对话中的关键事实到长期记忆</li>
 *   <li>{@code session_search}：搜索历史会话内容</li>
 * </ul>
 * </p>
 * <p>
 * 记忆数据持久化于工作区 {@code MEMORY.md} 与磁盘流水账，跨会话累积。
 * 与上下文压缩（Compaction）配合：压缩前先把新事实写入流水账，避免压缩丢失关键信息。
 * </p>
 * <p>
 * 本配置在工作区可用时构建 {@link WorkspaceManager} 并将四个记忆工具
 * 直接注册到 {@link Toolkit}，使 Agent 可自主调用。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "memory-tools-enabled", havingValue = "true")
public class MemoryToolsConfig {

    /**
     * 记忆工具注册初始化 Bean。
     * <p>
     * 启动时将四个长期记忆工具注册到 Toolkit，与业务工具统一管理。
     * 工作区未启用时跳过注册并打印警告。
     * </p>
     *
     * @param toolkit             工具容器（由 ToolkitConfig 注入）
     * @param agentWorkspacePath  工作区路径（由 WorkspaceConfig 注入）
     * @return 注册结果标识
     */
    @Bean
    public String memoryToolsInitializer(Toolkit toolkit,
                                         Optional<Path> agentWorkspacePath) {
        if (agentWorkspacePath.isEmpty()) {
            log.warn("[MemoryToolsConfig] 工作区未启用，长期记忆工具集跳过注册");
            return "memory-tools-skipped";
        }

        WorkspaceManager workspaceManager = new WorkspaceManager(agentWorkspacePath.get());
        int count = 0;

        // 依次注册四个记忆工具到 Toolkit
        count += registerTool(toolkit, new MemorySearchTool(workspaceManager), "memory_search");
        count += registerTool(toolkit, new MemoryGetTool(workspaceManager), "memory_get");
        count += registerTool(toolkit, new MemorySaveTool(workspaceManager), "memory_save");
        count += registerTool(toolkit, new SessionSearchTool(workspaceManager), "session_search");

        log.info("[MemoryToolsConfig] 长期记忆工具集已注册到 Toolkit: count={}", count);
        return "memory-tools-registered-" + count;
    }

    /**
     * 注册单个工具到 Toolkit，失败时抛出 {@link BusinessException}。
     *
     * @param toolkit  工具容器
     * @param tool     工具实例
     * @param toolName 工具名称（用于日志）
     * @return 1=注册成功，0=注册失败
     */
    private int registerTool(Toolkit toolkit, Object tool, String toolName) {
        try {
            toolkit.registerTool(tool);
            log.info("[MemoryToolsConfig] 注册记忆工具: {}", toolName);
            return 1;
        } catch (Exception e) {
            log.error("[MemoryToolsConfig] 注册记忆工具失败: {}, error={}", toolName, e.getMessage());
            throw new BusinessException(ErrorCode.TOOL_REGISTER_FAILED,
                    "注册记忆工具失败: " + toolName, e);
        }
    }
}
