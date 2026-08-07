package com.agent.scope.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import jakarta.servlet.DispatcherType;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置类。
 * <p>
 * 基于 API Key 认证模式，通过 {@code X-API-Key} 请求头传递密钥，
 * 由 {@link ApiKeyAuthFilter} 进行校验。适用于内部服务间调用或前端直连的轻量认证场景。
 * </p>
 * <p>
 * 配置项通过 {@code spring.security.api-key.*} 前缀读取：
 * <ul>
 *   <li>{@code spring.security.api-key.enabled} —— 是否启用安全认证（默认 true）</li>
 *   <li>{@code spring.security.api-key.key} —— 预期的 API Key 值</li>
 *   <li>{@code spring.security.api-key.header-name} —— API Key 所在的请求头名称（默认 X-API-Key）</li>
 * </ul>
 * </p>
 * <p>
 * 路径放行策略：
 * <ul>
 *   <li>/actuator/** —— 监控端点（Prometheus 抓取、健康检查）</li>
 *   <li>/swagger-ui/**、/swagger-ui.html、/v3/api-docs/** —— Swagger 接口文档</li>
 *   <li>/api/** —— 所有业务 API 需认证</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "spring.security.api-key", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    /** 预期的 API Key 值（从配置读取） */
    @Value("${spring.security.api-key.key}")
    private String apiKey;

    /** API Key 所在的请求头名称（从配置读取，默认 X-API-Key） */
    @Value("${spring.security.api-key.header-name:X-API-Key}")
    private String headerName;

    /**
     * 配置 Spring Security 过滤器链。
     * <p>
     * 安全策略：
     * <ol>
     *   <li>禁用 CSRF —— REST API 无需 CSRF 防护（无 Cookie 会话）</li>
     *   <li>无状态会话 —— 不创建 HttpSession，每次请求独立认证</li>
     *   <li>CORS 允许所有来源 —— 适配前端跨域调用</li>
     *   <li>路径鉴权 —— 放行监控/文档路径，/api/** 需认证</li>
     *   <li>插入自定义 {@link ApiKeyAuthFilter} —— 在 {@link UsernamePasswordAuthenticationFilter}
     *       之前执行 API Key 校验</li>
     * </ol>
     * </p>
     *
     * @param http HttpSecurity 构建器
     * @return 安全过滤器链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 构造 API Key 认证过滤器（通过构造函数注入 headerName 和 apiKey）
        ApiKeyAuthFilter apiKeyAuthFilter = new ApiKeyAuthFilter(headerName, apiKey);

        http
                // 禁用 CSRF：REST API 使用 Token 认证，无需 CSRF 防护
                .csrf(csrf -> csrf.disable())
                // 无状态会话：不创建/使用 HttpSession，每次请求独立认证
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CORS 配置：允许所有来源跨域访问
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 路径鉴权规则
                .authorizeHttpRequests(auth -> auth
                        // 放行：ASYNC 分发类型（SseEmitter 异步完成时不再二次鉴权，避免 Access Denied）
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // 放行：监控端点
                        .requestMatchers("/actuator/**").permitAll()
                        // 放行：Swagger UI 页面
                        .requestMatchers("/swagger-ui/**").permitAll()
                        // 放行：Swagger UI 入口页
                        .requestMatchers("/swagger-ui.html").permitAll()
                        // 放行：OpenAPI 3 文档 JSON
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        // 需认证：所有 /api/** 业务接口
                        .requestMatchers("/api/**").authenticated()
                        // 其他路径默认放行（非 /api/** 的接口不强制认证）
                        .anyRequest().permitAll()
                )
                // 在 UsernamePasswordAuthenticationFilter 之前插入 API Key 认证过滤器
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("[SecurityConfig] 安全配置初始化完成: headerName={}, apiKey配置={}",
                headerName, apiKey != null && !apiKey.isEmpty() ? "已配置" : "未配置");

        return http.build();
    }

    /**
     * CORS 配置源。
     * <p>
     * 允许所有来源（*）、所有 HTTP 方法和所有请求头跨域访问。
     * 适用于前后端分离架构，前端可从任意域名发起请求。
     * </p>
     *
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许所有来源
        configuration.setAllowedOriginPatterns(List.of("*"));
        // 允许所有 HTTP 方法
        configuration.setAllowedMethods(List.of("*"));
        // 允许所有请求头
        configuration.setAllowedHeaders(List.of("*"));
        // 允许携带凭证（Cookie）
        configuration.setAllowCredentials(true);
        // 预检请求缓存时间（秒）
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径生效
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
