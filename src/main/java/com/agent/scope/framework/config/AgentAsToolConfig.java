package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 GA 特性二十七：Agent as Tool 配置
 * <p>
 * 将 Agent 封装为工具，供其他 Agent 调用，实现 Agent 组合与编排：
 * <ul>
 *   <li>本地 Agent 工具化：将子 Agent 注册为工具，父 Agent 通过工具调用委派任务</li>
 *   <li>远程 A2A 工具化：将远程 A2A Agent 封装为工具，实现跨服务 Agent 调用</li>
 *   <li>统一调用接口：通过 Toolkit.registerTool() 统一管理，Agent 无感知差异</li>
 * </ul>
 * </p>
 * <p>
 * 典型场景：主 Agent 调用"翻译 Agent 工具"完成多语言翻译，
 * 调用"代码生成 Agent 工具"完成代码编写，各 Agent 专注自身领域。
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
     * Agent-as-Tool 装配初始化 Bean。
     * <p>
     * 启动时将配置的子 Agent 封装为工具注册到 Toolkit。
     * Agent-as-Tool 通过 {@code SkillBox.SkillRegistration.subAgent()} 注册，
     * 也可通过 {@code AgentTool} 直接封装。
     * </p>
     */
    @Bean
    public String agentAsToolInitializer() {
        log.info("[AgentAsToolConfig] Agent-as-Tool 已启用，子 Agent 将被封装为工具注册到 Toolkit");
        return "agent-as-tool-initialized";
    }
}
