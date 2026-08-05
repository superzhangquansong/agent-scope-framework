package com.agent.scope.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通义千问 DashScope 模型配置属性。
 * <p>
 * 对应 application.yml（Nacos）中 {@code scope.agentscope.dashscope} 配置段。
 * 用于配置阿里云通义千问大语言模型的连接参数，支持多模型切换。
 * </p>
 * <p>
 * 示例配置：
 * <pre>{@code
 * scope:
 *   agentscope:
 *     dashscope:
 *       api-key: sk-xxxx
 *       model: qwen-plus
 *       base-url: https://dashscope.aliyuncs.com
 *       temperature: 0.7
 *       max-tokens: 4096
 * }</pre>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "scope.agentscope.dashscope")
public class DashScopeProperties {

    /** API 密钥（从配置注入） */
    private String apiKey;

    /** 模型名称（如 qwen-plus、qwen-max、qwen-turbo） */
    private String model = "qwen-plus";

    /** 服务基址（默认阿里云 DashScope 网关） */
    private String baseUrl = "https://dashscope.aliyuncs.com";

    /** 采样温度（0~2，值越大随机性越强，默认 0.7） */
    private double temperature = 0.7;

    /** 单次生成最大 Token 数（默认 4096） */
    private int maxTokens = 4096;

    /** 是否启用流式输出（默认 true，配合 SSE 推送） */
    private boolean streamEnabled = true;
}
