package com.agent.scope.framework.hdl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * HDL API 配置属性。
 *
 * <p>对应 Nacos 配置中 hdl.ai.assistant.hdl-api 配置段。
 * 所有配置项均从 Nacos 注入，支持热重载。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "scope.agentscope.hdl-api")
public class HdlApiProperties {

    /** 网关基址（如 https://gateway.hdlcontrol.com） */
    private String baseUrl;

    /** 应用 Key */
    private String appKey;

    /** 应用密钥 */
    private String appSecret;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 30000;

    /** Token 失效错误码集合（逗号分隔） */
    private String authErrorCodes;

    /**
     * 获取认证错误码列表。
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
