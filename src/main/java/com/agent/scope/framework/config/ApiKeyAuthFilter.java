package com.agent.scope.framework.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API Key 认证过滤器。
 * <p>
 * 继承 {@link OncePerRequestFilter}，确保每个请求只执行一次认证逻辑。
 * 从请求头读取 API Key（Header 名通过构造函数注入），与配置的预期 Key 比对：
 * <ul>
 *   <li>匹配 —— 创建 {@link Authentication} 对象放入 {@link SecurityContextHolder}，放行请求</li>
 *   <li>不匹配或缺失 —— 直接返回 401 JSON 响应，不进入后续过滤器链</li>
 * </ul>
 * </p>
 * <p>
 * 放行路径（在 {@link #shouldNotFilter} 中处理）：
 * /actuator/**、/swagger-ui/**、/swagger-ui.html、/v3/api-docs/**
 * </p>
 * <p>
 * 该过滤器由 {@link SecurityConfig} 通过构造函数实例化，非 Spring 容器 Bean，
 * 避免被 Spring Security 默认过滤器链重复注册。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    /** 认证通过后赋予的角色（固定 API 客户端角色） */
    private static final String ROLE_API_CLIENT = "ROLE_API_CLIENT";

    /** 认证通过后的 Principal 名称（固定标识，不暴露 API Key 明文） */
    private static final String PRINCIPAL_API_CLIENT = "api-client";

    /** 401 响应体（统一 JSON 格式，不含 requestId/timestamp 以保持轻量） */
    private static final String UNAUTHORIZED_BODY =
            "{\"code\":401,\"message\":\"Unauthorized: API Key missing or invalid\"}";

    /** API Key 请求头名称（通过构造函数注入） */
    private final String headerName;

    /** 预期的 API Key 值（通过构造函数注入，用于比对校验） */
    private final String expectedKey;

    /**
     * 构造函数，注入请求头名称和预期 API Key。
     *
     * @param headerName API Key 所在的请求头名称（如 X-API-Key）
     * @param expectedKey 预期的 API Key 值
     */
    public ApiKeyAuthFilter(String headerName, String expectedKey) {
        this.headerName = headerName;
        this.expectedKey = expectedKey;
    }

    /**
     * 判断当前请求是否跳过认证过滤。
     * <p>
     * 放行监控端点、Swagger 文档等无需认证的路径，避免这些路径因缺少 API Key 被拦截。
     * </p>
     *
     * @param request HTTP 请求
     * @return true 表示跳过本过滤器，false 表示需要执行认证
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && (
                path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs/")
        );
    }

    /**
     * 核心认证逻辑：读取请求头中的 API Key，与预期值比对。
     * <p>
     * 匹配成功：创建 {@link UsernamePasswordAuthenticationToken} 放入 SecurityContext，
     * 后续过滤器及 Controller 可通过 SecurityContextHolder 获取当前认证信息。
     * </p>
     * <p>
     * 匹配失败/缺失：写入 401 JSON 响应并终止过滤器链，不记录 API Key 明文。
     * </p>
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 从请求头读取 API Key
        String apiKey = request.getHeader(headerName);

        // 校验 API Key 是否存在且与预期值匹配
        if (StringUtils.hasText(apiKey) && apiKey.equals(expectedKey)) {
            // 认证通过：创建 Authentication 对象，Principal 使用固定标识避免暴露 API Key 明文
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    PRINCIPAL_API_CLIENT,
                    null,
                    List.of(new SimpleGrantedAuthority(ROLE_API_CLIENT))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 放行到后续过滤器
            filterChain.doFilter(request, response);
        } else {
            // 认证失败：记录日志（不记录 API Key 明文，仅记录请求路径和失败原因）
            log.warn("[ApiKeyAuthFilter] 认证失败: path={}, reason={}",
                    request.getRequestURI(),
                    StringUtils.hasText(apiKey) ? "API Key 不匹配" : "API Key 缺失");
            // 写入 401 JSON 响应
            writeUnauthorizedResponse(response);
        }
    }

    /**
     * 写入 401 未授权 JSON 响应。
     * <p>统一格式：{"code":401,"message":"Unauthorized: API Key missing or invalid"}</p>
     *
     * @param response HTTP 响应
     * @throws IOException 写入响应体时可能抛出
     */
    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(UNAUTHORIZED_BODY);
        response.getWriter().flush();
    }
}
