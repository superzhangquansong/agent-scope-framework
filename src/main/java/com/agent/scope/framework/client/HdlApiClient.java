package com.agent.scope.framework.client;

import com.agent.scope.framework.config.properties.HdlApiProperties;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 云端 API 客户端（基于 Java 内置 HttpClient）。
 * <p>
 * 封装云端网关 API 的调用逻辑，包含：
 * <ul>
 *   <li>认证服务：登录、Token 刷新</li>
 *   <li>智能家居服务：房屋列表、设备列表、设备控制、设备详情、场景管理</li>
 *   <li>商城服务：产品搜索、产品详情、购物车</li>
 *   <li>储能服务：电站信息、逆变器数据</li>
 * </ul>
 * </p>
 * <p>
 * <b>签名机制</b>：MD5(appKey + timestamp + appSecret)，注入到每个请求的 sign 字段。
 * </p>
 * <p>
 * <b>鉴权机制</b>：Authorization: Bearer {accessToken}，由调用方传入 accessToken。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Component
public class HdlApiClient {

    /**
     * 请求头 Content-Type
     */
    private static final String HEADER_CONTENT_TYPE = "application/json;charset=UTF-8";

    /**
     * Bearer Token 前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    // ===== API 端点常量 =====

    /**
     * 用户登录
     */
    private static final String PATH_LOGIN = "/basis-footstone/user/oauth/login";

    /**
     * 房屋列表
     */
    private static final String PATH_HOME_LIST = "/home-wisdom/app/home/list";

    /**
     * 设备列表
     */
    private static final String PATH_DEVICE_LIST = "/home-wisdom/app/device/list";

    /**
     * 设备控制
     */
    private static final String PATH_DEVICE_CONTROL = "/home-wisdom/app/device/control";

    /**
     * 设备详情
     */
    private static final String PATH_DEVICE_INFO = "/home-wisdom/app/device/info";

    /**
     * 场景列表
     */
    private static final String PATH_SCENE_LIST = "/home-wisdom/app/scene/list";

    /**
     * 执行场景
     */
    private static final String PATH_SCENE_EXECUTE = "/home-wisdom/app/scene/execute";

    /**
     * 添加场景
     */
    private static final String PATH_SCENE_ADD = "/home-wisdom/app/scene/add";

    /**
     * 产品搜索
     */
    private static final String PATH_PRODUCT_SEARCH = "/crm-wisdom/distributors/product/mallList";

    /**
     * 产品详情
     */
    private static final String PATH_PRODUCT_DETAIL = "/crm-wisdom/distributors/product/getMallProductInfo";

    /**
     * 加入购物车
     */
    private static final String PATH_CART_ADD = "/crm-wisdom/shoppingCarts/add";

    /**
     * 购物车列表
     */
    private static final String PATH_CART_LIST = "/crm-wisdom/shoppingCarts/list";

    /**
     * 配置属性
     */
    private final HdlApiProperties properties;

    /**
     * Java 内置 HttpClient（线程安全，支持 HTTP/2）
     */
    private HttpClient httpClient;

    /**
     * 构造方法。
     *
     * @param properties 云端 API 配置属性
     */
    public HdlApiClient(HdlApiProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化 Java HttpClient。
     */
    @PostConstruct
    public void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        log.info("[ApiClient] HttpClient 初始化完成: baseUrl={}, connectTimeout={}ms, readTimeout={}ms",
                properties.getBaseUrl(), properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    /**
     * 销毁资源。
     */
    @PreDestroy
    public void destroy() {
        log.info("[ApiClient] HttpClient 资源已释放");
    }

    // ==================== 认证服务 ====================

    /**
     * 用户登录。
     *
     * @param loginName 登录名
     * @param loginPwd  密码
     * @return 登录响应 Map（含 accessToken、refreshToken、userId），失败返回 null
     */
    public Map<String, Object> login(String loginName, String loginPwd) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grantType", "password");
        params.put("loginName", loginName);
        params.put("loginPwd", loginPwd);
        injectSignParams(params);

        Map<String, Object> resp = doPost(PATH_LOGIN, params, null);
        if (resp == null) {
            log.warn("[ApiClient] 登录请求失败: loginName={}", loginName);
            return null;
        }

        // HDL API 响应格式：{ isSuccess: true, data: { accessToken, refreshToken, ... } }
        // 兼容处理：先检查顶层是否有 accessToken，没有则从 data 字段提取
        Map<String, Object> result = convertToMap(resp.get("data"));
        if (result == null || result.get("accessToken") == null) {
            // 顶层可能直接包含 accessToken（部分接口不包裹 data）
            result = resp;
        }

        log.info("[ApiClient] 登录结果: loginName={}, hasToken={}", loginName, result.get("accessToken") != null);
        return result;
    }

    // ==================== 智能家居服务 ====================

    /**
     * 获取房屋列表。
     *
     * @param accessToken 访问令牌
     * @return 房屋列表，失败返回空列表
     */
    public List<Map<String, Object>> getHomeList(String accessToken) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeType", "ALL");
        params.put("autoGenerate", false);
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_HOME_LIST, params, accessToken);
        return convertToList(data);
    }

    /**
     * 获取设备列表。
     *
     * @param accessToken 访问令牌
     * @param homeId      房屋 ID
     * @return 设备列表，失败返回空列表
     */
    public List<Map<String, Object>> getDeviceList(String accessToken, String homeId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeId", homeId);
        params.put("searchType", "ALL");
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_DEVICE_LIST, params, accessToken);
        // HDL 设备列表响应格式：{ data: { list: [...], total: N } }
        // 需要提取 data.list 字段
        if (data instanceof Map<?, ?> dataMap) {
            Object list = dataMap.get("list");
            return convertToList(list);
        }
        return convertToList(data);
    }

    /**
     * 控制设备（单设备，兼容旧调用）。
     * <p>
     * 内部转换为 actions 列表后调用 {@link #batchControlDevice}。
     * </p>
     *
     * @param accessToken 访问令牌
     * @param deviceId    设备 ID
     * @param homeId      房屋 ID
     * @param gatewayId   网关 ID
     * @param spk         设备功能类型（如 light.rgb、light.dimming）
     * @param attributes  控制属性列表（List&lt;Map&lt;String, Object&gt;&gt;，每项含 key 和 value）
     * @return true=控制成功，false=控制失败
     */
    public boolean controlDevice(String accessToken, String deviceId, String homeId,
                                 String gatewayId, String spk, Object attributes) {
        // 构建单设备 action
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("deviceId", deviceId);
        action.put("spk", spk);
        action.put("attributes", attributes);

        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(action);

        return batchControlDevice(accessToken, homeId, gatewayId, actions);
    }

    /**
     * 批量控制设备。
     * <p>
     * HDL 设备控制 API 要求（参考 AppDeviceControlDTO）：
     * <ul>
     *   <li>必填参数：homeId、gatewayId、actions</li>
     *   <li>actions 为 List&lt;Action&gt;，每个 Action 包含 deviceId、spk、attributes</li>
     *   <li>attributes 为 List&lt;StatusBean&gt;，每项含 key 和 value</li>
     *   <li>复杂类型（actions）序列化为 JSON 字符串后参与签名计算</li>
     * </ul>
     * </p>
     *
     * @param accessToken 访问令牌
     * @param homeId      房屋 ID
     * @param gatewayId   网关 ID
     * @param actions     控制动作列表，每个动作含 deviceId、spk、attributes
     * @return true=全部控制成功，false=至少一台失败
     */
    public boolean batchControlDevice(String accessToken, String homeId,
                                      String gatewayId, List<Map<String, Object>> actions) {
        Map<String, Object> result = batchControlDeviceWithDetail(accessToken, homeId, gatewayId, actions);
        return Boolean.TRUE.equals(result.get("success"));
    }

    /**
     * 批量控制设备（带详细错误信息）。
     * <p>
     * 与 {@link #batchControlDevice} 相同，但返回包含 success 和 message 的 Map，
     * 便于调用方获取失败原因（如"网关不在线"）。
     * </p>
     *
     * @param accessToken 访问令牌
     * @param homeId      房屋 ID
     * @param gatewayId   网关 ID
     * @param actions     控制动作列表，每个动作含 deviceId、spk、attributes
     * @return Map{success: Boolean, message: String}
     */
    public Map<String, Object> batchControlDeviceWithDetail(String accessToken, String homeId,
                                                            String gatewayId, List<Map<String, Object>> actions) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeId", homeId);
        params.put("gatewayId", gatewayId);
        params.put("actions", actions);
        injectSignParams(params);

        log.info("[ApiClient] 批量设备控制: homeId={}, gatewayId={}, actionCount={}",
                homeId, gatewayId, actions != null ? actions.size() : 0);

        Map<String, Object> resp = doPost(PATH_DEVICE_CONTROL, params, accessToken);
        if (resp == null) {
            return Map.of("success", false, "message", "请求云端API失败");
        }

        // 检查业务状态码
        Object isSuccess = resp.get("isSuccess");
        String message = String.valueOf(resp.getOrDefault("message", ""));
        if (isSuccess != null && !Boolean.TRUE.equals(isSuccess)) {
            log.warn("[ApiClient] 设备控制业务失败: msg={}", message);
            return Map.of("success", false, "message", message);
        }

        Object data = resp.get("data");
        Boolean success = convertToBoolean(data);
        return Map.of("success", success != null && success, "message", message.isEmpty() ? "控制成功" : message);
    }

    /**
     * 获取设备详情。
     *
     * @param accessToken 访问令牌
     * @param deviceId    设备 ID
     * @return 设备详情 Map，失败返回 null
     */
    public Map<String, Object> getDeviceDetail(String accessToken, String deviceId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deviceId", deviceId);
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_DEVICE_INFO, params, accessToken);
        return convertToMap(data);
    }

    // ==================== 场景管理 ====================

    /**
     * 获取场景列表。
     *
     * @param accessToken 访问令牌
     * @param homeId      房屋 ID
     * @return 场景列表，失败返回空列表
     */
    public List<Map<String, Object>> getSceneList(String accessToken, String homeId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeId", homeId);
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_SCENE_LIST, params, accessToken);
        return convertToList(data);
    }

    /**
     * 执行场景。
     *
     * @param accessToken 访问令牌
     * @param homeId      房屋 ID
     * @param sid         场景 ID
     * @return true=执行成功，false=执行失败
     */
    public boolean executeScene(String accessToken, String homeId, String sid) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeId", homeId);
        params.put("sid", sid);
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_SCENE_EXECUTE, params, accessToken);
        Boolean success = convertToBoolean(data);
        return success != null && success;
    }

    /**
     * 添加场景。
     *
     * @param accessToken 访问令牌
     * @param homeId      房屋 ID
     * @param name        场景名称
     * @param actions     场景动作（JSON 字符串或对象）
     * @return true=创建成功，false=创建失败
     */
    public boolean addScene(String accessToken, String homeId, String name, Object actions) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("homeId", homeId);
        params.put("name", name);
        params.put("actions", actions);
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_SCENE_ADD, params, accessToken);
        Boolean success = convertToBoolean(data);
        return success != null && success;
    }

    // ==================== 商城服务 ====================

    /**
     * 搜索产品。
     *
     * @param accessToken 访问令牌
     * @param keyword     搜索关键词
     * @return 搜索结果 Map（含 list、total），失败返回空 Map
     */
    public Map<String, Object> searchProduct(String accessToken, String keyword) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", keyword);
        params.put("page", 1);
        params.put("size", 20);
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_PRODUCT_SEARCH, params, accessToken);
        return convertToMap(data);
    }

    /**
     * 获取产品详情。
     *
     * @param accessToken 访问令牌
     * @param productId   产品 ID
     * @param skuId       SKU ID（可选）
     * @return 产品详情 Map，失败返回 null
     */
    public Map<String, Object> getProductDetail(String accessToken, String productId, String skuId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", productId);
        if (skuId != null && !skuId.isBlank()) {
            params.put("skuId", skuId);
        }
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_PRODUCT_DETAIL, params, accessToken);
        return convertToMap(data);
    }

    /**
     * 加入购物车。
     *
     * @param accessToken 访问令牌
     * @param productId   产品 ID
     * @param skuId       SKU ID
     * @param quantity    购买数量
     * @return true=添加成功，false=添加失败
     */
    public boolean addToCart(String accessToken, String productId, String skuId, int quantity) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", productId);
        params.put("skuId", skuId);
        params.put("quantity", quantity);
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_CART_ADD, params, accessToken);
        Boolean success = convertToBoolean(data);
        return success != null && success;
    }

    /**
     * 获取购物车列表。
     *
     * @param accessToken 访问令牌
     * @return 购物车列表 Map，失败返回空 Map
     */
    public Map<String, Object> getCartList(String accessToken) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", 1);
        params.put("size", 50);
        injectSignParams(params);

        Object data = doPostAndGetData(PATH_CART_LIST, params, accessToken);
        return convertToMap(data);
    }

    // ==================== 通用 API 调用 ====================

    /**
     * 通用 POST 请求（供储能等扩展服务使用）。
     *
     * @param accessToken 访问令牌
     * @param path        接口路径
     * @param params      请求参数
     * @return 响应数据 Map，失败返回 null
     */
    public Map<String, Object> postApi(String accessToken, String path, Map<String, Object> params) {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        injectSignParams(params);
        Object data = doPostAndGetData(path, params, accessToken);
        return convertToMap(data);
    }

    // ==================== 内部方法 ====================

    /**
     * 注入签名参数（appKey、timestamp、sign）。
     * <p>
     * HDL 签名算法（严格对齐 RestUtil.getSign + RestUtil.isSignValue）：
     * <ol>
     *   <li>时间戳使用秒级（System.currentTimeMillis() / 1000）</li>
     *   <li>遍历所有参数，仅简单类型（String/Number/Boolean）参与签名</li>
     *   <li>复杂类型（Map/List/数组）不参与签名（RestUtil.isSignValue 返回 false）</li>
     *   <li>按 key 字典序排序（排除 sign 字段本身）</li>
     *   <li>拼接为 key1=value1&key2=value2&...&keyN=valueN</li>
     *   <li>末尾追加 appSecret</li>
     *   <li>MD5 哈希后转小写</li>
     * </ol>
     * </p>
     *
     * @param params 请求参数 Map（原地修改，会追加 appKey、timestamp、sign）
     */
    private void injectSignParams(Map<String, Object> params) {
        String appKey = properties.getAppKey();
        String appSecret = properties.getAppSecret();
        // 时间戳使用毫秒级（对齐 hdl-agent 的 HdlApiClient.doRequest 实现）
        long timestamp = System.currentTimeMillis();

        params.put("appKey", appKey);
        params.put("timestamp", timestamp);

        // 按字典序排序参数 key（排除 sign 字段）
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        StringBuilder signSource = new StringBuilder();
        for (String key : sortedKeys) {
            if ("sign".equals(key)) {
                continue;
            }
            Object value = params.get(key);
            if (value == null) {
                continue;
            }
            // 仅简单类型参与签名（对齐 RestUtil.isSignValue 逻辑）
            // 复杂类型（Map/List/数组）不参与签名计算
            if (!isSignValue(value)) {
                continue;
            }
            String strValue = String.valueOf(value);
            if (strValue == null || strValue.isEmpty()) {
                continue;
            }
            if (signSource.length() > 0) {
                signSource.append("&");
            }
            signSource.append(key).append("=").append(strValue);
        }
        // 末尾追加 appSecret
        signSource.append(appSecret);

        // MD5 哈希转小写
        String sign = DigestUtils.md5Hex(signSource.toString().getBytes(StandardCharsets.UTF_8)).toLowerCase();

        // 调试日志：输出签名源串，便于排查签名不一致问题
        log.debug("[ApiClient] 签名源串: {}", signSource);
        log.debug("[ApiClient] 签名结果: sign={}", sign);

        params.put("sign", sign);
    }

    /**
     * 判断值是否为可参与签名的简单类型。
     * <p>
     * 严格对齐 HDL 框架 RestUtil.isSignValue() 的逻辑：
     * 仅 String/Number/Boolean 及其包装类型返回 true，
     * Map/List/数组等复杂类型返回 false（不参与签名计算）。
     * </p>
     *
     * @param value 参数值
     * @return true=可参与签名，false=不参与签名
     */
    private boolean isSignValue(Object value) {
        if (value == null) {
            return false;
        }
        // Number 类型（Integer/Long/Double/Float/Short/Byte/BigDecimal/BigInteger）
        if (value instanceof Number) {
            return true;
        }
        // Boolean 类型
        if (value instanceof Boolean) {
            return true;
        }
        // String 类型（空字符串不参与签名）
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        // Character 类型
        if (value instanceof Character) {
            return true;
        }
        // 其他类型（Map/List/数组/自定义对象等）不参与签名
        return false;
    }

    /**
     * 执行 POST 请求并返回完整响应 Map。
     * <p>
     * 使用 Java 内置 HttpClient 发送 POST 请求，支持连接超时与读取超时。
     * </p>
     *
     * @param path        接口路径
     * @param params      请求参数
     * @param accessToken 访问令牌（为 null 时不添加鉴权头）
     * @return 响应 Map，请求失败返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> doPost(String path, Map<String, Object> params, String accessToken) {
        String fullUrl = properties.getBaseUrl() + path;
        String jsonBody = toJson(params);

        // 构建 HttpRequest
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Content-Type", HEADER_CONTENT_TYPE);

        // 注入鉴权头
        if (accessToken != null && !accessToken.isBlank()) {
            requestBuilder.header("Authorization", BEARER_PREFIX + accessToken);
        }

        // 设置 POST body
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String respStr = response.body() != null ? response.body() : "";
            int code = response.statusCode();
            log.info("[ApiClient] >>> POST {} body={}", fullUrl, jsonBody);
            log.info("[ApiClient] <<< {} {} resp={}", code, fullUrl, truncate(respStr, 500));

            Map<String, Object> respMap = parseJson(respStr, Map.class);
            if (respMap == null) {
                log.error("[ApiClient] 响应解析失败: url={}, resp={}", fullUrl, respStr);
                return null;
            }
            return respMap;
        } catch (Exception e) {
            log.error("[ApiClient] 请求异常: url={}, error={}", fullUrl, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 执行 POST 请求并提取 data 字段。
     *
     * @param path        接口路径
     * @param params      请求参数
     * @param accessToken 访问令牌
     * @return data 字段值，失败返回 null
     */
    private Object doPostAndGetData(String path, Map<String, Object> params, String accessToken) {
        Map<String, Object> resp = doPost(path, params, accessToken);
        if (resp == null) {
            return null;
        }
        // 检查业务状态码
        Object isSuccess = resp.get("isSuccess");
        if (isSuccess != null && !Boolean.TRUE.equals(isSuccess)) {
            log.warn("[ApiClient] 业务失败: path={}, msg={}", path, resp.get("message"));
            return null;
        }
        return resp.get("data");
    }

    /**
     * 对象转 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串，失败返回 "{}"
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        try {
            return JSONObject.toJSONString(obj);
        } catch (Exception e) {
            log.error("[ApiClient] 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * JSON 字符串解析为指定类型。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型
     * @return 解析结果，失败返回 null
     */
    private <T> T parseJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            log.error("[ApiClient] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 data 转换为 List<Map>。
     *
     * @param data 原始数据
     * @return List<Map>，null 返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertToList(Object data) {
        if (data == null) {
            return Collections.emptyList();
        }
        if (data instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = new LinkedHashMap<>(m.size());
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        map.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(map);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * 将 data 转换为 Map。
     *
     * @param data 原始数据
     * @return Map，null 或非 Map 返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>(m.size());
            for (Map.Entry<?, ?> e : m.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return null;
    }

    /**
     * 将 data 转换为 Boolean。
     *
     * @param data 原始数据
     * @return Boolean，null 或非 Boolean 返回 null
     */
    private Boolean convertToBoolean(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Boolean b) {
            return b;
        }
        if (data instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }

    /**
     * 截断超长字符串。
     *
     * @param str    原始字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLen ? str.substring(0, maxLen) + "...(truncated)" : str;
    }
}