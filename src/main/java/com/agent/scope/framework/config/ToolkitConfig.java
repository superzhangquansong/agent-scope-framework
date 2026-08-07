package com.agent.scope.framework.config;

import com.agent.scope.framework.tool.AbstractTool;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 2.0 GA 核心共享 Bean 装配类。
 * <p>
 * 提供所有 48 个特性配置类依赖的共享基础设施 Bean：
 * - Toolkit：工具容器，注册所有业务工具
 * </p>
 * <p>
 * <b>装配策略</b>：通过 Spring 注入 {@code List<AbstractTool>} 精准获取业务工具 Bean，
 * 避免使用 {@code applicationContext.getBeansOfType(Object.class)} 暴力扫描——后者会
 * 触发所有 Bean（含 SandboxConfig.kubernetesClient 等重 Bean）的提前初始化，
 * 导致循环依赖与启动失败。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.toolkit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ToolkitConfig {

    /**
     * 装配 Toolkit Bean。
     * <p>
     * 通过构造器注入 {@code List<AbstractTool>}，Spring 仅初始化 {@link AbstractTool}
     * 类型的 Bean（DeviceTool / HomeTool / ProductTool 等），不会触发其他重 Bean
     * （如 KubernetesClient）的提前创建，避免循环依赖与启动失败。
     * </p>
     * <p>
     * 内置工具（TodoTools）与记忆工具（MemorySearchTool 等）通过
     * {@link BuiltinToolsConfig} / {@link MemoryToolsConfig} 主动调用
     * {@link Toolkit#registerTool(Object)} 注册，与本 Bean 解耦。
     * </p>
     *
     * @param businessTools 业务工具 Bean 列表（Spring 按类型精准注入）
     * @return Toolkit 实例
     */
    @Bean
    public Toolkit toolkit(List<AbstractTool> businessTools) {
        Toolkit toolkit = new Toolkit();
        List<String> registeredBeanNames = new ArrayList<>();

        // 遍历所有业务工具 Bean（仅 AbstractTool 子类），注册到 Toolkit
        for (AbstractTool toolBean : businessTools) {
            String beanName = toolBean.getClass().getSimpleName();
            try {
                toolkit.registerTool(toolBean);
                registeredBeanNames.add(beanName);
            } catch (Exception e) {
                log.warn("[ToolkitConfig] 注册 Tool Bean 失败: beanName={}, err={}",
                        beanName, e.getMessage());
            }
        }

        log.info("[ToolkitConfig] Toolkit 装配完成，已注册 {} 个业务 Tool Bean，工具列表: {}",
                registeredBeanNames.size(), toolkit.getToolNames());
        return toolkit;
    }
}
