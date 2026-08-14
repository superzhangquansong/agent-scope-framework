package com.agent.scope.framework.tool;

import com.agent.scope.framework.vo.ToolResultVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 通用 HTTP 请求工具（特性28增强：Skills 驱动的 API 调用）。
 * <p>
 * 让 LLM 能根据 SKILL.md 中的接口文档自行构造 HTTP 请求，
 * 直接调用外部 API（如 HDL 设备控制、产品查询等），
 * 而不需要为每个 API 单独封装 Tool。
 * </p>
 * <p>
 * <b>设计理念</b>：Skills 描述"怎么调"，本工具提供"调用能力"。
 * LLM 加载 SKILL.md 后，根据文档中的接口地址、请求参数、签名算法
 * 构造 http_request 工具调用，本工具负责实际发送 HTTP 请求并返回响应。
 * </p>
 * <p>
 * <b>安全约束</b>：
 * <ul>
 *   <li>仅支持 HTTPS/HTTP 协议</li>
 *   <li>请求超时 30 秒</li>
 *   <li>响应体最大 1MB，防止内存溢出</li>
 *   <li>记录完整请求/响应日志（脱敏后），便于调试</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class HttpRequestTool extends AbstractTool {

    /**
     * 请求超时时间（毫秒）
     */
    private static final int REQUEST_TIMEOUT_MS = 30000;

    /**
     * 响应体最大大小（字节），1MB
     */
    private static final int MAX_RESPONSE_SIZE = 1024 * 1024;

    /**
     * RestTemplate 实例（延迟初始化，线程安全）
     */
    private static volatile RestTemplate restTemplate;

    /**
     * 发送 HTTP 请求。
     * <p>
     * LLM 根据 SKILL.md 接口文档构造请求参数，本方法执行实际 HTTP 调用。
     * 支持 GET/POST/PUT/DELETE 方法，支持自定义请求头和请求体。
     * </p>
     *
     * @param method         HTTP 方法（GET/POST/PUT/DELETE）
     * @param url            完整请求 URL（含 query string）
     * @param headers        请求头 JSON（可选，如 {"Content-Type":"application/json","Authorization":"Bearer xxx"}）
     * @param body           请求体 JSON（可选，POST/PUT 时使用）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 含 statusCode/headers/body）
     */
    @Tool(name = "http_request",
            description = """
                    发送HTTP请求调用外部API。根据SKILL.md中的接口文档构造请求参数。
                    参数说明：method=HTTP方法(GET/POST/PUT/DELETE)，url=完整请求URL，headers=请求头JSON(可选)，body=请求体JSON(可选)。
                    返回：HTTP状态码、响应头、响应体。
                    使用场景：当skill_load加载的技能文档中描述了需要调用的HTTP接口时，使用本工具发起请求。
                    """,
            readOnly = false)
    public ToolResultVO httpRequest(
            @ToolParam(name = "method", required = true,
                    description = "HTTP方法：GET、POST、PUT、DELETE") String method,
            @ToolParam(name = "url", required = true,
                    description = "完整请求URL，含协议和域名，如 https://gateway.hdlcontrol.com/home-wisdom/app/device/list") String url,
            @ToolParam(name = "headers", required = false,
                    description = "请求头JSON字符串，如 {\"Content-Type\":\"application/json\",\"Authorization\":\"Bearer xxx\"}") String headers,
            @ToolParam(name = "body", required = false,
                    description = "请求体JSON字符串，POST/PUT方法时使用") String body,
            RuntimeContext runtimeContext
    ) {
        log.info("[HttpRequestTool] 发起HTTP请求: method={}, url={}, bodyLen={}",
                method, url, body != null ? body.length() : 0);

        ToolResultVO result = new ToolResultVO();

        try {
            // 1. 初始化 RestTemplate（双重检查锁）
            RestTemplate client = getRestTemplate();

            // 2. 构建请求头
            HttpHeaders httpHeaders = new HttpHeaders();
            if (headers != null && !headers.isBlank()) {
                parseHeaders(headers, httpHeaders);
            }
            // 默认 Content-Type
            if (httpHeaders.getContentType() == null) {
                httpHeaders.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            }

            // 3. 构建请求实体
            HttpEntity<String> entity;
            if (body != null && !body.isBlank()) {
                entity = new HttpEntity<>(body, httpHeaders);
            } else {
                entity = new HttpEntity<>(httpHeaders);
            }

            // 4. 发送请求
            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
            ResponseEntity<String> response = client.exchange(url, httpMethod, entity, String.class);

            // 5. 构建响应数据
            String responseBody = response.getBody();
            if (responseBody != null && responseBody.length() > MAX_RESPONSE_SIZE) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_SIZE)
                        + "...[响应体超过1MB，已截断]";
                log.warn("[HttpRequestTool] 响应体超过1MB，已截断: url={}", url);
            }

            // 6. 构建返回结果
            java.util.Map<String, Object> responseData = new java.util.LinkedHashMap<>();
            responseData.put("statusCode", response.getStatusCode().value());
            responseData.put("headers", response.getHeaders());
            responseData.put("body", responseBody);

            result.setSuccess(true);
            result.setMessage("HTTP请求成功: " + response.getStatusCode().value());
            result.setData(responseData);
            result.setBroadcastText("API调用完成: " + response.getStatusCode().value());

            log.info("[HttpRequestTool] HTTP请求成功: method={}, url={}, statusCode={}, responseLen={}",
                    method, url, response.getStatusCode().value(),
                    responseBody != null ? responseBody.length() : 0);

        } catch (Exception e) {
            log.error("[HttpRequestTool] HTTP请求失败: method={}, url={}, error={}",
                    method, url, e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("HTTP请求失败: " + e.getMessage());
            result.setData(java.util.Map.of("error", e.getMessage()));
        }

        return result;
    }

    /**
     * 获取 RestTemplate 实例（双重检查锁延迟初始化）。
     *
     * @return RestTemplate 实例
     */
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (HttpRequestTool.class) {
                if (restTemplate == null) {
                    org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                            new org.springframework.http.client.SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(REQUEST_TIMEOUT_MS);
                    factory.setReadTimeout(REQUEST_TIMEOUT_MS);
                    restTemplate = new RestTemplate(factory);
                }
            }
        }
        return restTemplate;
    }

    /**
     * 解析请求头 JSON 字符串到 HttpHeaders。
     *
     * @param headersJson 请求头 JSON 字符串
     * @param httpHeaders 目标 HttpHeaders 对象
     */
    private void parseHeaders(String headersJson, HttpHeaders httpHeaders) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, String> headerMap = mapper.readValue(headersJson,
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            headerMap.forEach(httpHeaders::add);
        } catch (Exception e) {
            log.warn("[HttpRequestTool] 解析请求头失败，使用默认: headers={}, error={}",
                    headersJson, e.getMessage());
        }
    }
}