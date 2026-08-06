package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.DashScopeProperties;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zqs
 * @title: ModelConfig
 * @projectName agent-scope-framework
 * @description:
 * @date 2026/8/5 15:39
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.core-beans", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModelConfig {

    /**
     * DashScope 模型配置属性
     */
    private final DashScopeProperties dashScopeProperties;

    @Bean
    public DashScopeChatModel dashScopeChatModel() {
        log.info("[CoreBeans] 初始化 DashScopeChatModel，model={}, stream={}, temperature={}",
                dashScopeProperties.getModel(),
                dashScopeProperties.isStreamEnabled(),
                dashScopeProperties.getTemperature());

        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(dashScopeProperties.getApiKey())
                .modelName(dashScopeProperties.getModel())
                .stream(dashScopeProperties.isStreamEnabled())
                .formatter(new DashScopeChatFormatter());

        log.info("[CoreBeans] DashScopeChatModel 初始化完成，provider=dashscope");
        return builder.build();
    }
}