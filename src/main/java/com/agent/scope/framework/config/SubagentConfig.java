package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AgentScope 2.0 GA 特性二十二：子 Agent（Sub-Agent）配置
 * <p>
 * 父 Agent 委派任务给子 Agent 并行执行，最后汇总结果。子 Agent 声明支持三种来源：
 * <ul>
 *   <li>定义工作区：从 workspace/subagents/{name}/AGENTS.md 加载声明</li>
 *   <li>内联体：通过 {@code inlineAgentsBody} 直接注入声明文本</li>
 *   <li>远程 HTTP：通过 {@code url} 指定远程 A2A 服务端地址</li>
 * </ul>
 * </p>
 * <p>
 * 子 Agent 工作区模式：
 * <ul>
 *   <li>{@link WorkspaceMode#ISOLATED}：独立工作区，与父 Agent 隔离</li>
 *   <li>{@link WorkspaceMode#SHARED}：共享父 Agent 工作区</li>
 * </ul>
 * </p>
 * <p>
 * 配置示例（Nacos application-config.yml）：
 * <pre>
 * scope:
 *   agentscope:
 *     subagents:
 *       - name: vision
 *         description: 视觉理解子 Agent，处理图像识别与描述
 *       - name: knowledge
 *         description: 知识库查询子 Agent，处理技术参数查询
 * </pre>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.subagent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SubagentConfig {

    private final AgentScopeProperties properties;

    /**
     * 子 Agent 声明列表 Bean。
     * <p>
     * 从 {@code scope.agentscope.subagents} 配置项加载子 Agent 声明，
     * 装配为 {@link SubagentDeclaration} 列表供 HarnessAgent.Builder 使用。
     * 配置变更后通过 @RefreshScope 自动重建。
     * </p>
     *
     * @return 子 Agent 声明列表（可能为空，表示无子 Agent）
     */
    @Bean
    public Optional<List<SubagentDeclaration>> subagentDeclarations() {
        List<AgentScopeProperties.SubagentConfig> configs = properties.getSubagents();
        if (configs == null || configs.isEmpty()) {
            log.info("[SubagentConfig] 未配置子 Agent，跳过装配");
            return Optional.empty();
        }

        List<SubagentDeclaration> declarations = new ArrayList<>(configs.size());
        for (AgentScopeProperties.SubagentConfig cfg : configs) {
            if (cfg.getName() == null || cfg.getName().isBlank()) {
                log.warn("[SubagentConfig] 跳过未命名的子 Agent 配置");
                continue;
            }
            SubagentDeclaration declaration = SubagentDeclaration.builder()
                    .name(cfg.getName())
                    .description(cfg.getDescription() != null ? cfg.getDescription() : "")
                    .workspaceMode(WorkspaceMode.ISOLATED)
                    .mode(SubagentDeclaration.Mode.SUBAGENT)
                    .persistSession(true)
                    .inheritParentPermissions(true)
                    .build();
            declarations.add(declaration);
            log.info("[SubagentConfig] 注册子 Agent: name={}, description={}",
                    cfg.getName(), cfg.getDescription());
        }

        log.info("[SubagentConfig] 子 Agent 声明装配完成，共 {} 个", declarations.size());
        return Optional.of(declarations);
    }
}
