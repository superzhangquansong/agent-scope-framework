package com.agent.scope.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ollama 本地模型配置属性。
 * <p>
 * 对应 application.yml（Nacos）中 {@code scope.agentscope.ollama} 配置段。
 * 用于配置本地部署的 Ollama 大语言模型连接参数，
 * 适用于内网环境或数据隐私敏感场景。
 * </p>
 * <p>
 * 示例配置：
 * <pre>{@code
 * scope:
 *   agentscope:
 *     ollama:
 *       base-url: http://localhost:11434
 *       model: qwen2.5:14b
 *       temperature: 0.7
 *       num-predict: 4096
 * }</pre>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "scope.agentscope.ollama")
public class OllamaProperties {

    /** 服务基址（默认本地 11434 端口） */
    private String baseUrl = "http://localhost:11434";

    /** 模型名称（如 qwen2.5:14b、llama3:8b） */
    private String model = "qwen2.5:1.5b";

    /** 采样温度（0~1，值越大随机性越强，默认 0.7） */
    private double temperature = 0.7;

    /** 单次生成最大 Token 数（默认 4096） */
    private int numPredict = 4096;

    /** 请求超时时间（秒，默认 120） */
    private int timeoutSeconds = 120;

    /** 是否启用（默认 false，内网部署时启用） */
    private boolean enabled = false;
}
