package com.agent.scope.framework.hdl;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HDL API HTTP 客户端。
 *
 * <p>使用 Apache HttpClient 连接池复用 TCP 连接，封装 HDL API 签名注入、
 * Bearer Token 鉴权、大数字段字符串化等横切关注点。</p>
 *
 * <p><b>适配说明</b>：本类从 hdl-agent 项目迁移而来，将原来的 UserSession 参数
 * 简化为直接传入 accessToken 字符串，适配当前项目的 SessionContext 架构。
 * Token 刷新逻辑已移除（当前项目 Token 由网关层管理）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class HdlApiClient {

    // ===== 连接池配置常量 =====
    private static final int MAX_TOTAL_CONNECTIONS = 100;
    private static final int MAX_CONN_PER_ROUTE = 20;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int CODE_AUTH_EXPIRED = 10001;
    private static final int CODE_NETWORK_ERROR = -1;

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_CONTENT_TYPE_VALUE = "application/json;charset=UTF-8";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 大数字段集合（字符串化，避免 JSON 精度丢失） */
    private static final Set<String> BIG_INT_FIELDS = new HashSet<>(Arrays.asList(HdlApiConstants.BIG_INT_FIELDS));

    /** 登录/刷新类接口路径（这类接口本身不需要鉴权） */
    private static final Set<String> NO_AUTH_PATHS = new HashSet<>(Arrays.asList(
            HdlApiConstants.LOGIN, HdlApiConstants.REFRESH_TOKEN));

    private final HdlApiProperties properties;
    private final HdlSignUtil signUtil;

    private PoolingHttpClientConnectionManager connectionManager;
    private CloseableHttpClient httpClient;

    public HdlApiClient(HdlApiProperties properties, HdlSignUtil signUtil) {
        this.properties = properties;
        this.signUtil = signUtil;
    }

    @PostConstruct
    public void init() {
        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_CONN_PER_ROUTE);
        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .build();
        log.info("[HdlApi] 连接池初始化完成: maxTotal={}, maxPerRoute={}",
                MAX_TOTAL_CONNECTIONS, MAX_CONN_PER_ROUTE);
    }

    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                log.warn("[HdlApi] 关闭 HttpClient 异常: {}", e.getMessage());
            }
        }
        if (connectionManager != null) {
            connectionManager.close();
            log.info("[HdlApi] 连接池已关闭");
        }
    }

    /**
     * 发起 HDL API 请求（需鉴权）。
     *
     * @param path        接口路径（如 /home-wisdom/app/device/list）
     * @param data        请求参数
     * @param needSign    是否需要签名
     * @param accessToken HDL 访问令牌（从 SessionContext.getAccessToken() 获取）
     * @return HDL 响应
     */
    public HdlResponse post(String path, Map<String, Object> data, boolean needSign, String accessToken) {
        return post(path, data, needSign, true, accessToken);
    }

    /**
     * 发起 HDL API 请求。
     *
     * @param path        接口路径
     * @param data        请求参数
     * @param needSign    是否需要签名
     * @param needAuth    是否需要鉴权（注入 Bearer Token）
     * @param accessToken HDL 访问令牌（needAuth=true 时必填）
     * @return HDL 响应
     */
    public HdlResponse post(String path, Map<String, Object> data, boolean needSign,
                            boolean needAuth, String accessToken) {
        if (data == null) {
            data = new LinkedHashMap<>();
        }
        // 需要鉴权但无 Token，直接返回认证错误
        if (needAuth && (accessToken == null || accessToken.isEmpty())) {
            HdlResponse r = new HdlResponse();
            r.setCode(CODE_AUTH_EXPIRED);
            r.setIsSuccess(false);
            r.setMsg("未登录或登录已过期");
            return r;
        }
        return doRequest(path, data, needSign, needAuth, accessToken);
    }

    /**
     * 执行单次 HTTP 请求（注入签名 + Token + 大数处理）。
     */
    private HdlResponse doRequest(String path, Map<String, Object> data, boolean needSign,
                                  boolean needAuth, String accessToken) {
        // 注入 BaseDTO 签名字段
        if (needSign) {
            data.put("appKey", properties.getAppKey());
            data.put("timestamp", System.currentTimeMillis());
            data.put("sign", signUtil.getSign(data, properties.getAppSecret()));
        }

        // 构造请求头
        String fullUrl = path.startsWith("http") ? path : properties.getBaseUrl() + path;

        // 大数字段字符串化
        Map<String, Object> safeData = convertBigIntFields(data);
        String body = JSON.toJSONString(safeData);

        log.info("[HdlApi] >>> POST {} body={}", fullUrl, body);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(properties.getConnectTimeoutMs())
                .setSocketTimeout(properties.getReadTimeoutMs())
                .build();

        HttpPost post = new HttpPost(fullUrl);
        post.setConfig(requestConfig);
        post.setHeader(HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_VALUE);
        if (needAuth && accessToken != null && !accessToken.isEmpty()) {
            post.setHeader(HEADER_AUTHORIZATION, BEARER_PREFIX + accessToken);
        }
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        try (CloseableHttpResponse resp = httpClient.execute(post)) {
            int statusCode = resp.getStatusLine().getStatusCode();
            String respStr = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            log.info("[HdlApi] <<< {} {} resp={}", statusCode, fullUrl, respStr);

            HdlResponse hdlResponse = JSON.parseObject(respStr, HdlResponse.class);
            if (hdlResponse == null) {
                hdlResponse = new HdlResponse();
                hdlResponse.setIsSuccess(false);
                hdlResponse.setCode(CODE_NETWORK_ERROR);
                hdlResponse.setMsg("响应解析失败: " + respStr);
            }
            // HTTP 401 标记为认证失效
            if (statusCode == HTTP_UNAUTHORIZED) {
                hdlResponse.setCode(CODE_AUTH_EXPIRED);
            }
            return hdlResponse;
        } catch (Exception e) {
            log.error("[HdlApi] 请求异常: {} {}", fullUrl, e.getMessage());
            HdlResponse r = new HdlResponse();
            r.setIsSuccess(false);
            r.setCode(CODE_NETWORK_ERROR);
            r.setMsg("网络错误: " + e.getMessage());
            return r;
        }
    }

    /**
     * 检测响应是否为 Token 失效。
     */
    public boolean isTokenExpired(HdlResponse resp) {
        if (resp == null || resp.getCode() == null) {
            return false;
        }
        List<String> codes = properties.getAuthErrorCodeList();
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        return codes.contains(String.valueOf(resp.getCode()));
    }

    /**
     * 递归将大数字段转为字符串（避免 JSON 精度丢失）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertBigIntFields(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (BIG_INT_FIELDS.contains(key) && value instanceof Number) {
                result.put(key, String.valueOf(value));
            } else if (value instanceof Map<?, ?> m) {
                result.put(key, convertBigIntFields((Map<String, Object>) m));
            } else if (value instanceof List<?> list) {
                result.put(key, convertBigIntFieldsInList(list));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> convertBigIntFieldsInList(List<?> list) {
        List<Object> result = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                result.add(convertBigIntFields((Map<String, Object>) m));
            } else if (item instanceof List<?> l) {
                result.add(convertBigIntFieldsInList(l));
            } else {
                result.add(item);
            }
        }
        return result;
    }
}
