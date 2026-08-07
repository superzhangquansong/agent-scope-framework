package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import io.agentscope.core.tool.subagent.SubAgentTool;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.agent.scope.framework.constant.BusinessConst.AGENT_TOOL_NAME_PREFIX;
import static com.agent.scope.framework.constant.BusinessConst.SUBAGENT_DEFAULT_MAX_ITERS;
import static com.agent.scope.framework.constant.BusinessConst.SUBAGENT_KNOWLEDGE_SYS_PROMPT;
import static com.agent.scope.framework.constant.BusinessConst.SUBAGENT_VISION_SYS_PROMPT;

/**
 * AgentScope 2.0 GA 特性二十七：Agent as Tool 配置
 * <p>
 * 将子 Agent 封装为工具，供其他 Agent 调用，实现 Agent 组合与编排：
 * <ul>
 *   <li>本地 Agent 工具化：将子 Agent（vision/knowledge 等）注册为工具，父 Agent 通过工具调用委派任务</li>
 *   <li>统一调用接口：通过 {@link Toolkit#registerAgentTool(io.agentscope.core.tool.AgentTool)} 统一管理，Agent 无感知差异</li>
 *   <li>独立会话上下文：每个子 Agent 工具内部维护独立会话，多轮对话通过 session_id 续接</li>
 * </ul>
 * </p>
 * <p>
 * 实现原理：使用 {@link SubAgentTool} 包装 {@link SubAgentProvider}，
 * SubAgentTool 实现 {@link io.agentscope.core.tool.AgentTool} 接口，
 * 将子 Agent 的 reply 能力暴露为 message 入参的工具。
 * 父 Agent 通过工具调用把任务委派给子 Agent，子 Agent 完成推理后返回 ToolResultBlock。
 * </p>
 * <p>
 * 典型场景：主 Agent 调用 "vision" 工具完成图像识别，
 * 调用 "knowledge" 工具完成知识库查询，各 Agent 专注自身领域。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "agent-as-tool-enabled", havingValue = "true")
public class AgentAsToolConfig {

    private final AgentScopeProperties properties;

    private final Toolkit toolkit;

    /**
     * 主 DashScope 聊天模型（由 ModelConfig / CoreBeansConfig 注入）。
     * <p>子 Agent 复用主模型配置，避免为每个子 Agent 单独装配模型。</p>
     */
    private final DashScopeChatModel dashScopeModel;

    /**
     * 子 Agent 名称 → 系统提示词映射。
     * <p>预置常见子 Agent（vision/knowledge）的领域提示词；
     * 未匹配的子 Agent 使用通用提示词。</p>
     */
    private static final Map<String, String> SUBAGENT_SYS_PROMPTS = Map.of(
            "vision", SUBAGENT_VISION_SYS_PROMPT,
            "knowledge", SUBAGENT_KNOWLEDGE_SYS_PROMPT
    );

    /**
     * 通用子 Agent 系统提示词（未在 {@link #SUBAGENT_SYS_PROMPTS} 中匹配时使用）。
     */
    private static final String GENERIC_SUBAGENT_SYS_PROMPT = "你是一个专业子 Agent，专注于完成父 Agent 委派的任务。请基于用户消息给出准确、简洁的回答。";

    /**
     * Agent-as-Tool 装配初始化 Bean。
     * <p>
     * 启动时遍历 {@code scope.agentscope.subagents} 配置，
     * 为每个子 Agent 构建独立的 {@link ReActAgent}，包装为 {@link SubAgentTool}
     * 注册到 {@link Toolkit}。
     * </p>
     * <p>
     * 注册后的工具名格式为 {@code agent_<子Agent名称>}（如 agent_vision、agent_knowledge），
     * 父 Agent 可像调用普通工具一样通过 LLM 工具调用机制委派任务。
     * </p>
     *
     * @return 注册结果标识
     */
    @Bean
    public String agentAsToolInitializer() {
        List<AgentScopeProperties.SubagentConfig> subagents = properties.getSubagents();
        if (subagents == null || subagents.isEmpty()) {
            log.warn("[AgentAsToolConfig] 未配置子 Agent，Agent-as-Tool 跳过注册");
            return "agent-as-tool-empty";
        }

        int successCount = 0;
        List<String> registeredNames = new ArrayList<>();
        for (AgentScopeProperties.SubagentConfig cfg : subagents) {
            if (cfg.getName() == null || cfg.getName().isBlank()) {
                log.warn("[AgentAsToolConfig] 跳过未命名的子 Agent 配置");
                continue;
            }
            try {
                String toolName = registerSubAgentAsTool(cfg);
                registeredNames.add(toolName);
                successCount++;
            } catch (Exception e) {
                log.error("[AgentAsToolConfig] 注册子 Agent 工具失败: name={}, error={}",
                        cfg.getName(), e.getMessage(), e);
                throw new BusinessException(ErrorCode.TOOL_REGISTER_FAILED,
                        "注册子 Agent 工具失败: " + cfg.getName(), e);
            }
        }

        log.info("[AgentAsToolConfig] Agent-as-Tool 装配完成，已注册 {} 个子 Agent 工具: {}",
                successCount, registeredNames);
        return "agent-as-tool-registered-" + successCount;
    }

    /**
     * 将单个子 Agent 包装为工具并注册到 Toolkit。
     * <p>
     * 流程：
     * <ol>
     *   <li>构建 {@link ReActAgent}：复用主模型、独立 maxIters、按名称匹配领域系统提示词</li>
     *   <li>构建 {@link SubAgentProvider}：每次调用提供子 Agent 实例（单例复用）</li>
     *   <li>构建 {@link SubAgentConfig}：工具名、描述、不转发事件</li>
     *   <li>创建 {@link SubAgentTool} 并通过 {@link Toolkit#registerAgentTool} 注册</li>
     * </ol>
     * </p>
     *
     * @param cfg 子 Agent 配置（名称 + 描述）
     * @return 注册后的工具名（agent_&lt;name&gt;）
     */
    private String registerSubAgentAsTool(AgentScopeProperties.SubagentConfig cfg) {
        String subAgentName = cfg.getName();
        String toolName = AGENT_TOOL_NAME_PREFIX + subAgentName;
        String description = cfg.getDescription() != null && !cfg.getDescription().isBlank()
                ? cfg.getDescription()
                : "子 Agent 工具：" + subAgentName;

        // 1. 构建子 Agent 的 ReActAgent（复用主模型与 Toolkit）
        String sysPrompt = SUBAGENT_SYS_PROMPTS.getOrDefault(subAgentName, GENERIC_SUBAGENT_SYS_PROMPT);
        ReActAgent subAgent = ReActAgent.builder()
                .name(subAgentName)
                .description(description)
                .sysPrompt(sysPrompt)
                .model(dashScopeModel)
                .toolkit(toolkit)
                .maxIters(SUBAGENT_DEFAULT_MAX_ITERS)
                .build();

        // 2. SubAgentProvider：返回子 Agent 实例（单例复用，状态由 SubAgentTool 内部通过 session 隔离）
        SubAgentProvider<ReActAgent> provider = () -> subAgent;

        // 3. SubAgentConfig：工具名与描述（forwardEvents=false 避免子 Agent 事件污染父 Agent SSE 流）
        SubAgentConfig subConfig = SubAgentConfig.builder()
                .toolName(toolName)
                .description(description)
                .forwardEvents(false)
                .build();

        // 4. 创建 SubAgentTool 并注册
        SubAgentTool subAgentTool = new SubAgentTool(provider, subConfig);
        toolkit.registerAgentTool(subAgentTool);

        log.info("[AgentAsToolConfig] 注册子 Agent 工具: toolName={}, sysPromptSource={}",
                toolName, SUBAGENT_SYS_PROMPTS.containsKey(subAgentName) ? "preset" : "generic");
        return toolName;
    }
}
