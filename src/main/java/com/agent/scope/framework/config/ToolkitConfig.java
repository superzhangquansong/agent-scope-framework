package com.agent.scope.framework.config;

import com.agent.scope.framework.tool.AbstractTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AgentScope 2.0 GA 核心共享 Bean 装配类。
 * <p>
 * 提供所有 48 个特性配置类依赖的共享基础设施 Bean：
 * - DashScopeChatModel：通义千问大语言模型实例
 * - Toolkit：工具容器，注册所有业务工具
 * - workspacePath：工作区目录路径
 * - middlewareList：中间件链（五阶段洋葱模型）
 * <p>
 * 这些 Bean 被 AgentConfig、HarnessAgentConfig 等核心配置类引用，
 * 避免循环依赖，确保装配顺序正确。
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.core-beans", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ToolkitConfig {

    /**
     * 动态注册所有继承 AbstractTool 的 Spring Bean。
     * <p>
     * 通过方法参数注入 List<AbstractTool>，Spring 会自动收集容器中所有 AbstractTool 子类实例。
     * 新增工具只需添加 @Component，无需修改此配置。
     * </p>
     */
    @Bean
    public Toolkit toolkit(List<AbstractTool> tools) {
        log.info("[CoreBeans] 初始化 Toolkit，开始动态注册工具");
        Toolkit toolkit = new Toolkit();

        // 内置工具（如果需要仍可单独注册）
        toolkit.registerTool(new TodoTools());
        log.info("[CoreBeans] 已注册内置工具: TodoTools");

        // 动态注册所有业务工具
        for (AbstractTool tool : tools) {
            toolkit.registerTool(tool);
            log.info("[CoreBeans] 已注册业务工具: {}", tool.getClass().getSimpleName());
        }

        log.info("[CoreBeans] Toolkit 初始化完成，共注册 {} 个业务工具", tools.size());
        return toolkit;
    }
}