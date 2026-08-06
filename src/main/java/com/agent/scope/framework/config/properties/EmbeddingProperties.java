package com.agent.scope.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 向量嵌入模型配置属性。
 * <p>
 * 对应 application.yml（Nacos）中 {@code scope.embedding} 配置段。
 * 用于配置文本向量化模型连接参数，支撑 RAG 知识库的文档嵌入与语义检索。
 * </p>
 * <p>
 * 示例配置：
 * <pre>{@code
 * scope:
 *   embedding:
 *     provider: dashscope
 *     model: text-embedding-v2
 *     api-key: sk-xxxx
 *     dimension: 1536
 *     batch-size: 25
 * }</pre>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "scope.agentscope.embedding")
public class EmbeddingProperties {

    /** 嵌入模型提供方（dashscope / ollama / openai） */
    private String provider = "dashscope";

    /** 模型名称（如 text-embedding-v2） */
    private String model = "text-embedding-v2";

    /** API 密钥（从配置注入） */
    private String apiKey;

    /** 服务基址（可选，默认使用提供方默认地址） */
    private String baseUrl;

    /** 向量维度（需与 Qdrant 集合配置一致，默认 1536） */
    private int dimension = 1536;

    /** 批量嵌入最大条数（默认 25） */
    private int batchSize = 25;

    /** 请求超时时间（秒，默认 30） */
    private int timeoutSeconds = 30;
}
