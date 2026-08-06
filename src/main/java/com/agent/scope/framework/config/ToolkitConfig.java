package com.agent.scope.framework.config;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
@ConditionalOnProperty(prefix = "scope.agentscope.toolkit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ToolkitConfig {

    private final ApplicationContext applicationContext;

    @Bean
    public Toolkit toolkit() {
        Toolkit toolkit = new Toolkit();
        List<String> registeredBeanNames = new ArrayList<>();

        // 从 Spring 容器获取所有 Bean
        Map<String, Object> allBeans = applicationContext.getBeansOfType(Object.class);

        // 遍历检查每个 Bean 是否含 @Tool 注解方法
        for (Map.Entry<String, Object> entry : allBeans.entrySet()) {
            Object bean = entry.getValue();
            String beanName = entry.getKey();

            // 跳过 Spring 框架内部 Bean（名称以 org.springframework 开头）
            if (bean.getClass().getName().startsWith("org.springframework")) {
                continue;
            }

            // 检查 Bean 类及其祖先类是否含 @Tool 注解方法
            if (hasToolAnnotation(bean.getClass())) {
                try {
                    toolkit.registerTool(bean);
                    registeredBeanNames.add(beanName);
                } catch (Exception e) {
                    log.warn("[ToolkitConfig] 注册 Tool Bean 失败: beanName={}, err={}",
                            beanName, e.getMessage());
                }
            }
        }

        log.info("[ToolkitConfig] Toolkit 装配完成，已注册 {} 个 Tool Bean，工具列表: {}",
                registeredBeanNames.size(), toolkit.getToolNames());
        return toolkit;
    }

    private boolean hasToolAnnotation(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }
}