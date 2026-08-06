package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
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
import java.util.ArrayList;
import java.util.List;
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
 * 注意：以上四个工具均需 {@link WorkspaceManager} 作为构造参数，故本配置在工作区
 * 可用时基于工作区路径构建 {@link WorkspaceManager} 并装配工具；工作区未启用时返回空列表。
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

    private final AgentScopeProperties properties;

    /**
     * 长期记忆工具集 Bean。
     * <p>
     * 装配四个记忆工具实例，注册到 Toolkit 后 Agent 可自主调用：
     * <ul>
     *   <li>{@link MemorySearchTool}：语义搜索长期记忆</li>
     *   <li>{@link MemoryGetTool}：按 ID 获取记忆</li>
     *   <li>{@link MemorySaveTool}：保存关键事实</li>
     *   <li>{@link SessionSearchTool}：搜索历史会话</li>
     * </ul>
     * </p>
     *
     * @param agentWorkspacePath 工作区路径（由 WorkspaceConfig 注入）
     * @return 记忆工具列表（工作区未启用时为空）
     */
    @Bean
    public List<Object> memoryTools(Optional<Path> agentWorkspacePath) {
        if (agentWorkspacePath.isEmpty()) {
            log.warn("[MemoryToolsConfig] 工作区未启用，长期记忆工具集跳过装配");
            return new ArrayList<>();
        }

        WorkspaceManager workspaceManager = new WorkspaceManager(agentWorkspacePath.get());
        List<Object> tools = new ArrayList<>(4);
        tools.add(new MemorySearchTool(workspaceManager));
        tools.add(new MemoryGetTool(workspaceManager));
        tools.add(new MemorySaveTool(workspaceManager));
        tools.add(new SessionSearchTool(workspaceManager));
        log.info("[MemoryToolsConfig] 长期记忆工具集已装配: count={} (memory_search/memory_get/memory_save/session_search)",
                tools.size());
        return tools;
    }
}
