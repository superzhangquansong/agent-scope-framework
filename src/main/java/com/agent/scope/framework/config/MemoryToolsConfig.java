package com.agent.scope.framework.config;

import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
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
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

/**
 * AgentScope 2.0 GA 特性十九：分布式记忆（Memory Tools）配置
 * <p>
 * 启用长期记忆工具集，支持跨会话知识积累与检索：
 * <ul>
 *   <li>{@code memory_search}：关键词搜索长期记忆（MEMORY.md + memory/*.md）</li>
 *   <li>{@code memory_get}：按路径读取特定记忆条目</li>
 *   <li>{@code memory_save}：保存当前对话中的关键事实到长期记忆</li>
 *   <li>{@code session_search}：搜索历史会话内容</li>
 * </ul>
 * </p>
 * <p>
 * <b>分布式改造（MinIO）</b>：记忆工具不再使用 {@code new WorkspaceManager(本地path)} 纯本地模式，
 * 而是复用 {@link HarnessAgent#getWorkspaceManager()} —— 该实例由 HarnessAgent 内部基于
 * {@code RemoteFilesystemSpec}（MinIO BaseStore）正确构建，工作区文件（MEMORY.md / memory/*.md /
 * sessions/*.log.jsonl）持久化到 MinIO 而非本地磁盘，多副本下记忆一致可检索。
 * </p>
 * <p>
 * 依赖注入说明：HarnessAgent Bean 通过 {@link Lazy} 懒加载注入，打破与
 * HarnessAgentConfig（scopeHarnessAgent → Optional&lt;PlanModeManager&gt;）之间的潜在
 * 循环依赖，同时保证 memoryToolsInitializer 初始化时拿到的 WorkspaceManager 已就绪。
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
     * @param toolkit      工具容器（由 ToolkitConfig 注入）
     * @param harnessAgent HarnessAgent 实例（@Lazy 懒加载，用于获取正确的 WorkspaceManager）
     * @return 注册结果标识
     */
    @Bean
    public String memoryToolsInitializer(Toolkit toolkit,
                                         @Lazy Optional<HarnessAgent> harnessAgent) {
        if (harnessAgent.isEmpty()) {
            log.warn("[MemoryToolsConfig] HarnessAgent 未装配，长期记忆工具集跳过注册");
            return "memory-tools-skipped";
        }

        // 复用 HarnessAgent 内部基于 RemoteFilesystemSpec（MinIO）构建的 WorkspaceManager，
        // 使记忆工具读写与 MemoryFlushMiddleware 保持一致（均走 MinIO），
        // 替代原有的 new WorkspaceManager(本地path) 纯本地模式
        WorkspaceManager workspaceManager = harnessAgent.get().getWorkspaceManager();
        int count = 0;

        // 依次注册四个记忆工具到 Toolkit
        count += registerTool(toolkit, new MemorySearchTool(workspaceManager), "memory_search");
        count += registerTool(toolkit, new MemoryGetTool(workspaceManager), "memory_get");
        count += registerTool(toolkit, new MemorySaveTool(workspaceManager), "memory_save");
        count += registerTool(toolkit, new SessionSearchTool(workspaceManager), "session_search");

        log.info("[MemoryToolsConfig] 长期记忆工具集已注册到 Toolkit: count={}, workspaceManager=MinIO-backed",
                count);
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
