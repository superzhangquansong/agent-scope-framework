package com.agent.scope.framework.config;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

/**
 * AgentScope 2.0 GA 特性二十九：内置工具注册配置
 * <p>
 * 注册 AgentScope 框架内置的通用工具到 Toolkit，补充业务工具之外的通用能力：
 * <ul>
 *   <li>{@link TodoTools}：待办列表管理（todo_write），支持任务拆分与进度跟踪</li>
 * </ul>
 * </p>
 * <p>
 * 注意：以下工具因构造依赖较重，暂不在本配置中注册（如需启用请单独装配）：
 * <ul>
 *   <li>{@code FilesystemTool}：需 {@code AbstractFilesystem} 参数</li>
 *   <li>{@code AgentSpawnTool}：需 {@code DefaultAgentManager}/{@code TaskRepository} 参数</li>
 *   <li>{@code TaskTool}：需 {@code TaskRepository} 参数</li>
 * </ul>
 * </p>
 * <p>
 * 这些工具与业务工具（DeviceTool/ProductTool 等）共同注册到 Toolkit，
 * Agent 通过统一的工具调用接口使用。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.builtin-tools", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BuiltinToolsConfig {

    private final Toolkit toolkit;

    /**
     * 内置工具注册初始化 Bean。
     * <p>
     * 启动时将框架内置工具注册到 Toolkit，与业务工具统一管理。
     * 注册过程通过反射检查工具类是否含 @Tool 注解方法，与 ToolkitConfig 逻辑一致。
     * </p>
     *
     * @return 注册结果标识
     */
    @Bean
    public String builtinToolsInitializer() {
        int count = 0;

        // 1. TodoTools：待办列表管理（任务拆分与进度跟踪）
        if (registerToolBean(new TodoTools())) {
            count++;
        }

        log.info("[BuiltinToolsConfig] 内置工具注册完成: count={} (TodoTools)", count);
        return "builtin-tools-registered-" + count;
    }

    /**
     * 注册工具 Bean 到 Toolkit。
     *
     * @param toolBean 工具实例
     * @return true=注册成功
     */
    private boolean registerToolBean(Object toolBean) {
        if (!hasToolAnnotation(toolBean.getClass())) {
            log.debug("[BuiltinToolsConfig] 跳过无 @Tool 注解的工具: {}", toolBean.getClass().getSimpleName());
            return false;
        }
        try {
            toolkit.registerTool(toolBean);
            log.info("[BuiltinToolsConfig] 注册内置工具: {}", toolBean.getClass().getSimpleName());
            return true;
        } catch (Exception e) {
            log.warn("[BuiltinToolsConfig] 注册工具失败: {}, error={}",
                    toolBean.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查类是否含 @Tool 注解方法。
     */
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
