package com.agent.scope.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * HDL API 配置属性
 *
 * <p>对应 application.yml 中 hdl.ai.assistant.hdl-api 配置段。
 * 所有配置项（含 appKey/appSecret/authErrorCodes）均从 application.yml 注入，
 * 不在 Java 代码中硬编码凭据，避免安全隐患。</p>
 *
 * @author zqs
 * @since 1.0.0
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "scope.agentscope.hdl-api")
public class HdlApiProperties {

    /** 网关基址（从 application.yml 注入） */
    private String baseUrl;

    /** 应用 Key（从 application.yml 注入，不在代码中硬编码凭据） */
    private String appKey;

    /** 应用密钥（从 application.yml 注入，不在代码中硬编码凭据） */
    private String appSecret;

    /** 连接超时（毫秒，从 application.yml 注入） */
    private int connectTimeoutMs;

    /** 读取超时（毫秒，从 application.yml 注入） */
    private int readTimeoutMs;

    /** Token 失效错误码集合（逗号分隔，从 application.yml 注入） */
    private String authErrorCodes;

    /**
     * 获取认证错误码列表。
     *
     * <p>从 application.yml 配置的 authErrorCodes 字符串解析为 List，
     * 配置缺失时返回空列表（不硬编码错误码）。</p>
     *
     * @return 认证错误码列表
     */
    public List<String> getAuthErrorCodeList() {
        if (authErrorCodes == null || authErrorCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(authErrorCodes.split(","));
    }
}
