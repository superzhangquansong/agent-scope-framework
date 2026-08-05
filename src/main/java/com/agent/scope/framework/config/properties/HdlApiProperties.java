package com.agent.scope.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 云端 API 配置属性。
 * <p>
 * 对应 application.yml（Nacos）中 {@code scope.hdl-api} 配置段。
 * 所有配置项（含 appKey/appSecret/authErrorCodes）均从配置中心注入，
 * 不在 Java 代码中硬编码凭据，避免安全隐患。
 * </p>
 * <p>
 * 示例配置：
 * <pre>{@code
 * scope:
 *   hdl-api:
 *     base-url: https://gateway.example.com
 *     app-key: your-app-key
 *     app-secret: your-app-secret
 *     connect-timeout-ms: 8000
 *     read-timeout-ms: 15000
 *     auth-error-codes: 401,10001,40001,403
 * }</pre>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "scope.hdl-api")
public class HdlApiProperties {

    /** 网关基址（从配置注入，如 https://gateway.example.com） */
    private String baseUrl;

    /** 应用 Key（从配置注入，不在代码中硬编码凭据） */
    private String appKey;

    /** 应用密钥（从配置注入，不在代码中硬编码凭据） */
    private String appSecret;

    /** 连接超时（毫秒，默认 8000） */
    private int connectTimeoutMs = 8000;

    /** 读取超时（毫秒，默认 15000） */
    private int readTimeoutMs = 15000;

    /** Token 失效错误码集合（逗号分隔，从配置注入） */
    private String authErrorCodes;

    /**
     * 获取认证错误码列表。
     * <p>
     * 从 {@link #authErrorCodes} 字符串按逗号拆分为 List，
     * 配置缺失时返回空列表（不硬编码错误码）。
     * </p>
     *
     * @return 认证错误码列表，永不为 null
     */
    public List<String> getAuthErrorCodeList() {
        if (authErrorCodes == null || authErrorCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(authErrorCodes.split(","));
    }
}
