package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.config.properties.DashScopeProperties;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DashScope 聊天模型配置。
 * <p>
 * 创建 {@link DashScopeChatModel} Bean，注入通义千问 API 凭证和模型参数。
 * </p>
 * <p>
 * <b>性能优化要点</b>：
 * <ul>
 *   <li>显式调用 {@code enableThinking(false)} 关闭思考模式，避免 qwen-plus 输出冗长推理文本
 *       （未关闭时 LLM 会输出 200+ tokens 的"我需要..."思考文本，按 40 tps 速度耗时 5+ 秒）</li>
 *   <li>通过 {@code defaultOptions(GenerateOptions)} 传递 temperature、parallelToolCalls 等参数，
 *       确保配置文件中的参数实际生效（之前 ModelConfig 未传递这些参数，导致使用 API 默认值）</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.core-beans", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModelConfig {

    /**
     * AgentScope 完整配置（包含 DashScope 子配置，含 enableThinking、parallelToolCalls 等字段）
     */
    private final AgentScopeProperties agentScopeProperties;

    /**
     * DashScope 模型配置属性（兼容旧配置，主要用于 apiKey 和 model）
     */
    private final DashScopeProperties dashScopeProperties;

    @Bean
    public DashScopeChatModel dashScopeChatModel() {
        // 优先使用 AgentScopeProperties.DashScope（字段完整），回退到 DashScopeProperties
        AgentScopeProperties.DashScope ds = agentScopeProperties.getDashscope();

        // 模型名称优先取 AgentScopeProperties.modelName，其次取 DashScopeProperties.model
        String modelName = agentScopeProperties.getModelName() != null
                ? agentScopeProperties.getModelName()
                : dashScopeProperties.getModel();

        log.info("[CoreBeans] 初始化 DashScopeChatModel，model={}, stream={}, temperature={}, enableThinking={}, parallelToolCalls={}",
                modelName,
                ds.isStream(),
                ds.getTemperature(),
                ds.isEnableThinking(),
                ds.isParallelToolCalls());

        // 构建 GenerateOptions，传递 temperature、parallelToolCalls 等参数
        // 这些参数通过 defaultOptions 传递给 DashScopeChatModel，每次请求时自动合并
        GenerateOptions defaultOptions = GenerateOptions.builder()
                .temperature(ds.getTemperature())
                .parallelToolCalls(ds.isParallelToolCalls())
                .build();

        // 注意：不设置 baseUrl，使用 DashScopeHttpClient 默认值 "https://dashscope.aliyuncs.com"
        // Nacos 中的 base-url 配置（含 /compatible-mode/v1）是 OpenAI 兼容模式用的，
        // 但 DashScopeChatModel 使用原生 DashScope 协议（baseUrl + /api/v1/services/aigc/...），
        // 设置 compatible-mode/v1 会导致路径重复（404 错误）
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(ds.getApiKey() != null && !ds.getApiKey().isBlank()
                        ? ds.getApiKey()
                        : dashScopeProperties.getApiKey())
                .modelName(modelName)
                .stream(ds.isStream())
                .enableThinking(ds.isEnableThinking())
                .defaultOptions(defaultOptions)
                .formatter(new DashScopeChatFormatter());

        log.info("[CoreBeans] DashScopeChatModel 初始化完成，provider=dashscope, enableThinking={}", ds.isEnableThinking());
        return builder.build();
    }
}
