package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 GA 特性二：HarnessAgent 入口配置
 * <p>
 * HarnessAgent 是 AgentScope 2.0 GA 推荐的 Agent 入口，打包以下工程能力：
 * - Workspace：Agent 执行时的独立临时工作目录
 * - 长期记忆（Memory）：用户级跨会话记忆
 * - 会话持久化：Agent 运行状态持久化至数据库
 * - 子 Agent（Sub-Agent）：父 Agent 委派任务给子 Agent 并行执行
 * - 沙箱（Sandbox）：安全执行用户上传的 Python/Shell 脚本
 * <p>
 * 通过 Middleware 和 Toolkit 通道扩展 ReActAgent，核心推理循环得以保留，仅做增强。
 * 本配置类负责装配 HarnessAgent 构建器及其单例实例。
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.react-agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReactAgentConfig {

    private final AgentScopeProperties properties;
    /**
     * DashScope 聊天模型（由 CoreBeansConfig 注入）
     */
    private final DashScopeChatModel dashScopeModel;

    /**
     * 工具容器（由 CoreBeansConfig 注入，已注册所有业务工具）
     */
    private final Toolkit toolkit;

    @Bean
    public ReActAgent rootReActAgent() {
        ReActAgent agent = buildRootReActAgent();
        log.info("[AgentScopeConfig] 根 ReActAgent 装配完成: name={}, maxIters={}, maxRetries={}, fallback={}",
                properties.getRootAgentName(), properties.getRootAgentMaxIters(),
                properties.getMaxRetries(), properties.isFallbackModelEnabled());
        return agent;
    }

    private ReActAgent buildRootReActAgent() {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(properties.getRootAgentName())
                .model(dashScopeModel)
                .toolkit(toolkit)
                .maxIters(properties.getRootAgentMaxIters());

        if (properties.getMaxRetries() > 0) {
            builder = builder.maxRetries(properties.getMaxRetries());
        }

        return builder.build();
    }
}