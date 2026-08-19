package com.agent.scope.framework.hdl;

/**
 * HDL 云端 API 端点常量。
 *
 * <p>直连 gateway.hdlcontrol.com，网关按路径前缀路由到不同微服务：</p>
 * <ul>
 *   <li>/basis-footstone/* → 认证服务（登录/Token刷新）</li>
 *   <li>/home-wisdom/* → 智能家居服务（房屋/设备/控制）</li>
 *   <li>/crm-wisdom/* → CRM 商城服务（产品/购物车）</li>
 * </ul>
 *
 * @author zqs
 * @since 2.0.0
 */
public final class HdlApiConstants {

    private HdlApiConstants() {
    }

    // ===== 认证服务 basis-footstone =====
    public static final String LOGIN = "/basis-footstone/user/oauth/login";
    public static final String REFRESH_TOKEN = "/basis-footstone/user/oauth/login";

    // ===== OAuth 授权类型 =====
    public static final String GRANT_TYPE_PASSWORD = "password";
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    // ===== 智能家居服务 home-wisdom =====
    public static final String HOME_LIST = "/home-wisdom/app/home/list";
    public static final String DEVICE_LIST = "/home-wisdom/app/device/list";
    public static final String DEVICE_CONTROL = "/home-wisdom/app/device/control";
    public static final String DEVICE_INFO = "/home-wisdom/app/device/info";
    public static final String SCENE_LIST = "/home-wisdom/app/scene/list";
    public static final String SCENE_INFO = "/home-wisdom/app/scene/info";
    public static final String SCENE_ADD = "/home-wisdom/app/scene/add";
    public static final String SCENE_UPDATE = "/home-wisdom/app/scene/update";
    public static final String SCENE_DELETE = "/home-wisdom/app/scene/delete";
    public static final String SCENE_EXECUTE = "/home-wisdom/app/scene/execute";

    // ===== 储能服务 home-wisdom =====
    public static final String POWER_STATION_INFO = "/home-wisdom/app/powerStation/info";
    public static final String POWER_STATION_LIST = "/home-wisdom/app/powerStation/page";
    public static final String DEVICE_INVERTER_ALL_INFO = "/home-wisdom/app/device/inverter/allInfo";

    // ===== CRM 商城服务 crm-wisdom =====
    public static final String PRODUCT_MALL_INFO = "/crm-wisdom/distributors/product/getMallProductInfo";
    public static final String PRODUCT_MALL_LIST = "/crm-wisdom/distributors/product/mallList";
    public static final String CART_ADD = "/crm-wisdom/shoppingCarts/add";
    public static final String CART_LIST = "/crm-wisdom/shoppingCarts/list";

    /** 大数字段（避免 JSON 精度丢失，序列化为字符串） */
    public static final String[] BIG_INT_FIELDS = {"deviceId", "gatewayId", "homeId", "skuId", "productId"};

    // ===== 请求/响应字段名常量 =====
    public static final String FIELD_LOGIN_NAME = "loginName";
    public static final String FIELD_LOGIN_PWD = "loginPwd";
    public static final String FIELD_GRANT_TYPE = "grantType";
    public static final String FIELD_EXPIRES_IN = "expiresIn";
    /** 刷新令牌有效期字段名（HDL 登录/刷新接口返回） */
    public static final String FIELD_REFRESH_EXPIRES_IN = "refreshExpiresIn";
    public static final String FIELD_HOME_ID = "homeId";
    public static final String FIELD_HOME_NAME = "homeName";
    public static final String FIELD_HOME_TYPE = "homeType";
    public static final String FIELD_AUTO_GENERATE = "autoGenerate";
    public static final String FIELD_DEVICE_COUNT = "deviceCount";
    public static final String FIELD_IS_REMOTE_CONTROL = "isRemoteControl";
    public static final String FIELD_DEVICE_ID = "deviceId";
    public static final String FIELD_DEVICE_IDS = "deviceIds";
    public static final String FIELD_GATEWAY_ID = "gatewayId";
    public static final String FIELD_SPK = "spk";
    public static final String FIELD_SEARCH_TYPE = "searchType";
    public static final String FIELD_ONLINE = "online";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_SCENE_SID = "sid";
}
