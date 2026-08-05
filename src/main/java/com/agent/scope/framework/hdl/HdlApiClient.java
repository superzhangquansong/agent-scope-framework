package com.agent.scope.framework.hdl;

import com.agent.scope.framework.context.UserSession;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HDL API 客户端
 *
 * <p>完全对标 hdl-ai-miniprogram/skills/home-skill/utils/request.js：
 * <ul>
 *   <li>注入 BaseDTO 字段：appKey、timestamp、sign（MD5 签名）</li>
 *   <li>注入 Authorization: Bearer {accessToken}</li>
 *   <li>大数字段（deviceId/gatewayId/homeId 等）字符串化，避免 JSON 精度丢失</li>
 *   <li>Token 失效自动刷新：命中 auth-error-codes 配置的错误码（10001/10002/10004/10041/10042/10043 等）时触发</li>
 *   <li>防重入：使用 ConcurrentHashMap + computeIfAbsent 保证同会话并发请求只刷新一次</li>
 *   <li>刷新方式：grantType=refresh_token 调用 /basis-footstone/user/oauth/login 无感续期</li>
 *   <li>刷新成功自动重试原请求；刷新失败清除 Token，由上层返回 need_login 事件</li>
 *   <li>isAuthError 综合判定（错误码 + 关键词）</li>
 * </ul>
 *
 * <p>因脱离小程序，Token/房屋/设备状态存于服务端 UserSession，由 SessionManager 管理。</p>
 *
 * @author zqs
 * @since 1.0.0
 */
@Slf4j
@Component
public class HdlApiClient {

    // ===== 连接池配置常量 =====
    /** 连接池最大连接数 */
    private static final int MAX_TOTAL_CONNECTIONS = 100;
    /** 每路由最大连接数 */
    private static final int MAX_CONN_PER_ROUTE = 20;
    /** HTTP 401 状态码 */
    private static final int HTTP_UNAUTHORIZED = 401;
    /** 认证失效错误码 */
    private static final int CODE_AUTH_EXPIRED = 10001;
    /** 网络错误错误码 */
    private static final int CODE_NETWORK_ERROR = -1;

    /** 请求头 Content-Type */
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_CONTENT_TYPE_VALUE = "application/json;charset=UTF-8";
    /** 请求头 Authorization */
    private static final String HEADER_AUTHORIZATION = "Authorization";
    /** Bearer Token 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 请求体字段名 */
    private static final String FIELD_ACCESS_TOKEN = "accessToken";
    private static final String FIELD_REFRESH_TOKEN = "refreshToken";

    private final HdlApiProperties properties;
    private final HdlSignUtil signUtil;
    private final HdlJsonUtil jsonUtil;

    /** Token 刷新防重入：sessionToken → 刷新 Future，同会话并发请求共享同一刷新流程 */
    private final Map<String, CompletableFuture<Boolean>> refreshingFutures = new ConcurrentHashMap<>();

    /** 大数字段集合（字符串化） */
    private static final Set<String> BIG_INT_FIELDS = new HashSet<>(Arrays.asList(HdlApiConstants.BIG_INT_FIELDS));

    /** 认证错误关键词 */
    private static final List<String> AUTH_KEYWORDS = Arrays.asList(
            "token", "认证", "登录", "授权", "unauthorized", "authenticate",
            "login", "expired", "过期", "失效", "禁止访问", "forbidden");

    /** 登录/刷新类接口路径（这类接口本身不需要鉴权，避免循环刷新） */
    private static final Set<String> NO_AUTH_PATHS = new HashSet<>(Arrays.asList(
            HdlApiConstants.LOGIN, HdlApiConstants.REFRESH_TOKEN));

    /** 连接池管理器（应用级别单例，复用 TCP 连接，避免每次请求新建连接） */
    private PoolingHttpClientConnectionManager connectionManager;

    /** 复用的 HttpClient 实例（连接池模式，应用生命周期内单例） */
    private CloseableHttpClient httpClient;

    public HdlApiClient(HdlApiProperties properties, HdlSignUtil signUtil, HdlJsonUtil jsonUtil) {
        this.properties = properties;
        this.signUtil = signUtil;
        this.jsonUtil = jsonUtil;
    }

    /**
     * 初始化连接池和 HttpClient（应用启动时执行一次）
     *
     * <p>使用 PoolingHttpClientConnectionManager 复用 TCP 连接，
     * 替代每次请求 HttpClients.createDefault() 的低效模式，
     * 显著降低高并发下的连接建立开销（高可用/高吞吐优化）。</p>
     */
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

    /**
     * 销毁连接池资源（应用关闭时执行）
     */
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
     * 发起 HDL API 请求
     *
     * @param path     接口路径（如 /home-wisdom/app/device/list）
     * @param data     请求参数
     * @param needSign 是否需要签名
     * @param needAuth 是否需要鉴权（注入 Bearer Token）
     * @param session  用户会话（含 HDL Token）
     * @return HDL 响应
     */
    public HdlResponse post(String path, Map<String, Object> data, boolean needSign, boolean needAuth, UserSession session) {
        if (data == null) {
            data = new LinkedHashMap<>();
        }
        // 需要鉴权但无 Token，直接返回认证错误（不发请求，对标小程序逻辑）
        if (needAuth && (session == null || !session.isLoggedIn())) {
            HdlResponse r = new HdlResponse();
            r.setCode(CODE_AUTH_EXPIRED);
            r.setIsSuccess(false);
            r.setMsg("未登录或登录已过期");
            return r;
        }

        HdlResponse response = doRequest(path, data, needSign, needAuth, session);

        // 检测 Token 失效 → 自动刷新 + 重试（登录/刷新接口本身不触发）
        if (needAuth && !NO_AUTH_PATHS.contains(path) && isTokenExpired(response)) {
            log.info("[HdlApi] 检测到Token失效, path={}, 触发自动刷新", path);
            boolean refreshed = refreshTokenWithReentry(session);
            if (refreshed) {
                log.info("[HdlApi] Token刷新成功，重试原请求: {}", path);
                return doRequest(path, data, needSign, needAuth, session);
            } else {
                log.warn("[HdlApi] Token刷新失败，清除Token，返回需登录: path={}", path);
                if (session != null) {
                    session.clearToken();
                }
                HdlResponse r = new HdlResponse();
                r.setCode(CODE_AUTH_EXPIRED);
                r.setIsSuccess(false);
                r.setMsg("认证已失效，请重新登录");
                return r;
            }
        }
        return response;
    }

    /**
     * 执行单次 HTTP 请求（注入签名 + Token + 大数处理）
     */
    private HdlResponse doRequest(String path, Map<String, Object> data, boolean needSign, boolean needAuth, UserSession session) {
        // 注入 BaseDTO 签名字段
        if (needSign) {
            data.put("appKey", properties.getAppKey());
            data.put("timestamp", System.currentTimeMillis());
            data.put("sign", signUtil.getSign(data, properties.getAppSecret()));
        }

        // 构造请求头
        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_VALUE);
        if (needAuth && session != null && session.isLoggedIn()) {
            headers.put(HEADER_AUTHORIZATION, BEARER_PREFIX + session.getHdlAccessToken());
        }

        // 大数字段字符串化
        Map<String, Object> safeData = convertBigIntFields(data);
        String body = jsonUtil.toJson(safeData);
        String fullUrl = path.startsWith("http") ? path : properties.getBaseUrl() + path;

        log.info("[HdlApi] >>> POST {} body={}", fullUrl, body);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(properties.getConnectTimeoutMs())
                .setSocketTimeout(properties.getReadTimeoutMs())
                .build();

        // 使用连接池复用的 HttpClient（替代每次 createDefault，高并发性能优化）
        HttpPost post = new HttpPost(fullUrl);
        post.setConfig(requestConfig);
        headers.forEach(post::setHeader);
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        try (CloseableHttpResponse resp = httpClient.execute(post)) {
            int statusCode = resp.getStatusLine().getStatusCode();
            String respStr = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            log.info("[HdlApi] <<< {} {} resp={}", statusCode, fullUrl, respStr);

            HdlResponse hdlResponse = jsonUtil.parse(respStr, HdlResponse.class);
            // HTTP 401 也标记为认证失效
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
     * Token 刷新（防重入：同会话并发请求共享同一刷新流程）。
     *
     * <p>使用 {@link ConcurrentHashMap#computeIfAbsent} 保证"检查-创建-放入"为原子操作，
     * 避免原 get + put 两步操作间的竞态条件：当多个并发请求同时检测到 Token 失效时，
     * 仅第一个请求会真正发起刷新，后续请求复用同一 {@link CompletableFuture} 等待结果。</p>
     *
     * <p>刷新流程：</p>
     * <ol>
     *   <li>无 session 或无 refreshToken → 直接返回 false（无法刷新）</li>
     *   <li>原子获取或创建刷新 Future（同 sessionToken 共享）</li>
     *   <li>阻塞等待刷新结果</li>
     *   <li>无论成功/失败，finally 移除 Future（允许后续再次触发刷新）</li>
     * </ol>
     *
     * @param session 用户会话（含 hdlRefreshToken）
     * @return true=刷新成功并已更新 session 中的 Token；false=刷新失败或无法刷新
     */
    public boolean refreshTokenWithReentry(UserSession session) {
        if (session == null || session.getHdlRefreshToken() == null) {
            return false;
        }
        String key = session.getSessionToken();
        // 防重入：computeIfAbsent 原子操作，同 sessionToken 并发请求共享同一刷新 Future
        CompletableFuture<Boolean> future = refreshingFutures.computeIfAbsent(key, k -> {
            log.info("[HdlApi] 发起 Token 刷新流程, sessionToken={}", k);
            return CompletableFuture.supplyAsync(() -> doRefreshToken(session))
                    .exceptionally(ex -> {
                        log.warn("[HdlApiClient] Token 刷新异步任务异常: {}", ex.getMessage());
                        return false;
                    });
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            // 刷新完成（无论成功/失败）后移除 Future，允许后续请求在 Token 再次失效时重新触发刷新
            refreshingFutures.remove(key);
        }
    }

    /**
     * 实际执行 Token 刷新。
     *
     * <p>调用 HDL 登录接口 {@link HdlApiConstants#REFRESH_TOKEN}（即 {@code /basis-footstone/user/oauth/login}），
     * 使用 {@code grantType=refresh_token} 模式，以 refreshToken 换取新的 accessToken + refreshToken。</p>
     *
     * <p>请求体字段（对标 hdl-ai-miniprogram 登录接口 refresh_token 模式）：</p>
     * <ul>
     *   <li>{@code grantType} = "refresh_token" —— 授权类型：刷新令牌模式</li>
     *   <li>{@code refreshToken} —— 登录成功时返回的刷新令牌</li>
     *   <li>{@code appKey} —— 应用标识（签名 + 鉴权）</li>
     *   <li>{@code timestamp} —— 时间戳（防重放）</li>
     *   <li>{@code sign} —— MD5 签名（appSecret 盐值）</li>
     * </ul>
     *
     * <p>响应成功时 data 含 {@code accessToken} 和 {@code refreshToken}，更新到 session 中。</p>
     *
     * @param session 用户会话（含 hdlRefreshToken）
     * @return true=刷新成功并已更新 Token；false=刷新失败
     */
    private boolean doRefreshToken(UserSession session) {
        log.info("[HdlApi] 开始刷新Token, appKey={}", properties.getAppKey());
        Map<String, Object> params = new LinkedHashMap<>();
        // grantType=refresh_token：使用刷新令牌模式换取新 Token（与 password 模式共用同一端点）
        params.put(HdlApiConstants.FIELD_GRANT_TYPE, HdlApiConstants.GRANT_TYPE_REFRESH_TOKEN);
        params.put(FIELD_REFRESH_TOKEN, session.getHdlRefreshToken());
        params.put("appKey", properties.getAppKey());
        params.put("timestamp", System.currentTimeMillis());
        params.put("sign", signUtil.getSign(params, properties.getAppSecret()));

        // 刷新接口本身不需要鉴权（needAuth=false），避免循环刷新
        HdlResponse resp = doRequest(HdlApiConstants.REFRESH_TOKEN, params, false, false, null);
        Map<String, Object> dataMap = resp.getDataAsMap();
        if (resp.success() && dataMap != null) {
            Object accessToken = dataMap.get(FIELD_ACCESS_TOKEN);
            Object refreshToken = dataMap.get(FIELD_REFRESH_TOKEN);
            if (accessToken != null) {
                session.setHdlAccessToken(accessToken.toString());
                // 部分 HDL 实现刷新后不返回新 refreshToken，此时保留旧 refreshToken 继续使用
                if (refreshToken != null) {
                    session.setHdlRefreshToken(refreshToken.toString());
                }
                log.info("[HdlApi] Token刷新成功");
                return true;
            }
        }
        log.error("[HdlApi] Token刷新失败: code={}, msg={}", resp.getCode(), resp.getMsg());
        return false;
    }

    /**
     * 检测响应是否为 Token 失效。
     *
     * <p>对 {@code properties} 和 {@code authErrorCodeList} 双重 null 防御。
     * 虽然 {@link HdlApiProperties#getAuthErrorCodeList()} 内部已返回 {@code Collections.emptyList()}
     * 兜底，但 properties 字段在极端场景（如 Spring 异步刷新）可能短暂为 null，
     * 此处显式校验避免 NPE 影响主流程。</p>
     */
    public boolean isTokenExpired(HdlResponse resp) {
        if (resp == null || properties == null) {
            return false;
        }
        if (resp.getCode() == null) {
            return false;
        }
        List<String> codes = properties.getAuthErrorCodeList();
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        return codes.contains(String.valueOf(resp.getCode()));
    }

    /**
     * 综合判定是否为认证错误（对标 isAuthError，错误码 + 关键词）。
     *
     * <p>与 {@link #isTokenExpired} 保持一致的 null 防御策略。</p>
     */
    public boolean isAuthError(HdlResponse resp) {
        if (resp == null || properties == null) {
            return false;
        }
        // 错误码判定（先校验 code 非空，再校验错误码列表非空，避免 NPE）
        if (resp.getCode() != null) {
            List<String> codes = properties.getAuthErrorCodeList();
            if (codes != null && codes.contains(String.valueOf(resp.getCode()))) {
                return true;
            }
        }
        // 关键词判定
        String msg = resp.getMsg() == null ? "" : resp.getMsg().toLowerCase();
        for (String kw : AUTH_KEYWORDS) {
            if (msg.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 递归将大数字段转为字符串（对标 convertBigIntFields）
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
