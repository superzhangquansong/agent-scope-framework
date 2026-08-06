package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope 2.0 GA 特性四十四：提示词模板管理配置
 * <p>
 * 支持从 Nacos 配置中心动态加载系统提示词模板，运营人员可在线调整 Prompt
 * 而无需重启服务。配置变更后通过 @RefreshScope 自动刷新。
 * </p>
 * <p>
 * 配置格式（Nacos application-config.yml）：
 * <pre>
 * scope:
 *   agentscope:
 *     prompt-templates:
 *       default-prompt: |
 *         你是智能生活助手...
 *       vision: |
 *         你是视觉理解专家...
 * </pre>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.prompt-templates", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PromptTemplateConfig {

    private final AgentScopeProperties properties;

    /**
     * 提示词模板持有器 Bean
     * <p>
     * 持有所有命名提示词模板，支持运行时按名称获取。
     * 配合 @RefreshScope 实现 Nacos 配置变更后自动刷新。
     * </p>
     *
     * @return PromptTemplateHolder 模板持有器
     */
    @Bean
    public PromptTemplateHolder promptTemplateHolder() {
        PromptTemplateHolder holder = new PromptTemplateHolder();
        String defaultPrompt = properties.getPromptTemplates().getDefaultPrompt();
        if (defaultPrompt != null && !defaultPrompt.isBlank()) {
            holder.addTemplate("default", defaultPrompt);
            log.info("[PromptTemplateConfig] 已加载默认提示词模板，长度={}", defaultPrompt.length());
        } else {
            log.info("[PromptTemplateConfig] 未配置自定义提示词模板，将使用 HarnessAgentConfig 中的默认模板");
        }
        return holder;
    }

    /**
     * 提示词模板持有器
     * <p>
     * 线程安全的提示词模板容器，支持运行时动态增删模板。
     * </p>
     */
    @Slf4j
    @Component
    @RefreshScope
    public static class PromptTemplateHolder {

        /** 模板存储（线程安全） */
        private final Map<String, String> templates = new ConcurrentHashMap<>();

        /**
         * 添加提示词模板
         *
         * @param name     模板名称
         * @param template 模板内容
         */
        public void addTemplate(String name, String template) {
            templates.put(name, template);
            log.debug("[PromptTemplateHolder] 添加模板: name={}, length={}", name, template.length());
        }

        /**
         * 获取提示词模板
         *
         * @param name 模板名称
         * @return 模板内容，不存在返回 null
         */
        public String getTemplate(String name) {
            return templates.get(name);
        }

        /**
         * 获取默认提示词模板
         *
         * @return 默认模板内容，不存在返回 null
         */
        public String getDefaultTemplate() {
            return templates.get("default");
        }

        /**
         * 检查模板是否存在
         *
         * @param name 模板名称
         * @return true=存在
         */
        public boolean hasTemplate(String name) {
            return templates.containsKey(name);
        }
    }
}
