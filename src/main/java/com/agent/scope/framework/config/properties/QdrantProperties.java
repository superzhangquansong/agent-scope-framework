package com.agent.scope.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant 向量数据库配置属性。
 * <p>
 * 对应 application.yml（Nacos）中 {@code scope.qdrant} 配置段。
 * 用于配置 Qdrant 向量数据库连接参数，支撑 RAG 知识库检索与语义搜索。
 * </p>
 * <p>
 * 示例配置：
 * <pre>{@code
 * scope:
 *   qdrant:
 *     host: localhost
 *     port: 6333
 *     collection: agent_knowledge
 *     vector-size: 1536
 *     api-key: your-api-key
 * }</pre>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "scope.agentscope.qdrant")
public class QdrantProperties {

    /** 主机地址（默认 localhost） */
    private String host = "localhost";

    /** 端口号（默认 6333） */
    private int port = 6333;

    /** 集合名称（知识库向量集合） */
    private String collection = "agent_knowledge";

    /** 向量维度（需与 Embedding 模型输出维度一致，默认 1536） */
    private int vectorSize = 1536;

    /** API 密钥（可选，用于鉴权访问） */
    private String apiKey;

    /** 是否使用 HTTPS（默认 false） */
    private boolean useTls = false;

    /** 连接超时时间（毫秒，默认 5000） */
    private int connectTimeoutMs = 5000;

    /** 读取超时时间（毫秒，默认 10000） */
    private int readTimeoutMs = 10000;
}
