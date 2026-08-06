package com.agent.scope.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置属性。
 * <p>
 * 对应 application.yml（Nacos）中 {@code scope.minio} 配置段。
 * 用于配置 MinIO 对象存储连接参数，支撑文件上传下载、户型图存储等场景。
 * </p>
 * <p>
 * 示例配置：
 * <pre>{@code
 * scope:
 *   minio:
 *     endpoint: http://localhost:9000
 *     access-key: minioadmin
 *     secret-key: minioadmin
 *     bucket: agent-files
 * }</pre>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "scope.agentscope.minio")
public class MinioProperties {

    /** 服务端点（含协议与端口，如 http://localhost:9000） */
    private String endpoint = "http://localhost:9000";

    /** 访问密钥 */
    private String accessKey = "minioadmin";

    /** 秘密密钥 */
    private String secretKey = "minioadmin";

    /** 默认存储桶名称 */
    private String bucket = "agent-files";

    /** 是否启用 HTTPS（默认 false） */
    private boolean secure = false;

    /** 连接超时时间（毫秒，默认 10000） */
    private int connectTimeoutMs = 10000;

    /** 读取超时时间（毫秒，默认 30000） */
    private int readTimeoutMs = 30000;
}
