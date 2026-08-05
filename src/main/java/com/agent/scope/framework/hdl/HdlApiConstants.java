package com.agent.scope.framework.hdl;

/**
 * HDL 云端 API 端点常量。
 *
 * <p>从 hdl-ai-miniprogram 小程序中提取的真实端点，直连 gateway.hdlcontrol.com。
 * 网关按路径前缀路由到不同微服务：</p>
 * <ul>
 *   <li>/basis-footstone/* → 认证服务（登录/Token刷新）</li>
 *   <li>/home-wisdom/* → 智能家居服务（房屋/设备/控制）</li>
 *   <li>/crm-wisdom/* → CRM 商城服务（产品/购物车）</li>
 * </ul>
 *
 * <p>本文件只保留 Java 代码中实际被引用的端点和字段常量。
 * 未使用的常量已清理（原约60个预定义但未引用的常量已删除）。</p>
 *
 * @author zqs
 * @since 1.0.0
 */
public final class HdlApiConstants {

    private HdlApiConstants() {
    }

    // ===== 认证服务 basis-footstone =====
    /** 用户登录（密码模式） */
    public static final String LOGIN = "/basis-footstone/user/oauth/login";
    /**
     * Token 刷新端点。
     *
     * <p>HDL 网关登录接口同时支持两种 grantType：</p>
     * <ul>
     *   <li>{@code password}：账号密码登录（首次登录）</li>
     *   <li>{@code refresh_token}：刷新令牌（无感续期）</li>
     * </ul>
     * <p>两者共用同一端点 {@code /basis-footstone/user/oauth/login}，通过 grantType 字段区分模式。
     * 此常量与 {@link #LOGIN} 指向同一 URL，单独定义是为了语义清晰、便于在 NO_AUTH_PATHS 中显式声明。</p>
     */
    public static final String REFRESH_TOKEN = "/basis-footstone/user/oauth/login";

    // ===== OAuth 授权类型 =====
    /** OAuth 授权类型：密码模式（账号密码登录） */
    public static final String GRANT_TYPE_PASSWORD = "password";
    /** OAuth 授权类型：刷新令牌模式（使用 refreshToken 无感续期 accessToken） */
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    // ===== 智能家居服务 home-wisdom =====
    /** 房屋列表 */
    public static final String HOME_LIST = "/home-wisdom/app/home/list";
    /** 设备列表 */
    public static final String DEVICE_LIST = "/home-wisdom/app/device/list";
    /** 设备控制 */
    public static final String DEVICE_CONTROL = "/home-wisdom/app/device/control";
    /** 设备详情 */
    public static final String DEVICE_INFO = "/home-wisdom/app/device/info";
    /** 场景列表 */
    public static final String SCENE_LIST = "/home-wisdom/app/scene/list";
    /** 场景详情 */
    public static final String SCENE_INFO = "/home-wisdom/app/scene/info";
    /** 添加场景 */
    public static final String SCENE_ADD = "/home-wisdom/app/scene/add";
    /** 编辑场景 */
    public static final String SCENE_UPDATE = "/home-wisdom/app/scene/update";
    /** 删除场景 */
    public static final String SCENE_DELETE = "/home-wisdom/app/scene/delete";
    /** 执行场景 */
    public static final String SCENE_EXECUTE = "/home-wisdom/app/scene/execute";

    // ===== 储能服务 home-wisdom（powerStation / inverter） =====
    /** 电站详情 */
    public static final String POWER_STATION_INFO = "/home-wisdom/app/powerStation/info";
    /** 电站列表（分页，用于故障全量扫描） */
    public static final String POWER_STATION_LIST = "/home-wisdom/app/powerStation/page";
    /** 电池使用报告 */
    public static final String POWER_STATION_BATTERY_REPORT = "/home-wisdom/app/powerStation/statistics/battery/report";
    /** 省钱分析报告 */
    public static final String POWER_STATION_SAVINGS_REPORT = "/home-wisdom/app/powerStation/statistics/savings/report";
    /** 逆变器实时数据（含 batterySoc、batteryPowerNow 等实时字段） */
    public static final String DEVICE_INVERTER_ALL_INFO = "/home-wisdom/app/device/inverter/allInfo";
    /** 备电SOC VS 充放电（含 backupSocs 数组） */
    public static final String POWER_STATION_BACKUP_SOC_ELECTRICITY = "/home-wisdom/app/powerStation/statistics/battery/backupSocElectricity";

    // ===== CRM 商城服务 crm-wisdom =====
    /** 产品详情（含 SKU 品号 + 配件） */
    public static final String PRODUCT_MALL_INFO = "/crm-wisdom/distributors/product/getMallProductInfo";
    /** 产品列表搜索（按产品名查询，返回 productId 列表） */
    public static final String PRODUCT_MALL_LIST = "/crm-wisdom/distributors/product/mallList";
    /** 加入购物车 */
    public static final String CART_ADD = "/crm-wisdom/shoppingCarts/add";
    /** 购物车列表 */
    public static final String CART_LIST = "/crm-wisdom/shoppingCarts/list";

    /** 大数字段（避免 JSON 精度丢失，序列化为字符串） */
    public static final String[] BIG_INT_FIELDS = {"deviceId", "gatewayId", "homeId", "skuId", "productId"};

    // ===== HDL API 请求/响应字段名常量（仅保留 Java 代码中实际引用的） =====
    /** 登录-登录名 */
    public static final String FIELD_LOGIN_NAME = "loginName";
    /** 登录-密码 */
    public static final String FIELD_LOGIN_PWD = "loginPwd";
    /** 登录-授权类型 */
    public static final String FIELD_GRANT_TYPE = "grantType";
    /** Token 过期时间字段 */
    public static final String FIELD_EXPIRES_IN = "expiresIn";

    /** 房屋 ID 字段 */
    public static final String FIELD_HOME_ID = "homeId";
    /** 房屋名称字段 */
    public static final String FIELD_HOME_NAME = "homeName";
    /** 房屋类型字段 */
    public static final String FIELD_HOME_TYPE = "homeType";
    /** 自动生成标识字段 */
    public static final String FIELD_AUTO_GENERATE = "autoGenerate";
    /** 设备数量字段 */
    public static final String FIELD_DEVICE_COUNT = "deviceCount";
    /** 远程控制标识字段 */
    public static final String FIELD_IS_REMOTE_CONTROL = "isRemoteControl";

    /** 设备 ID 字段（单数，用于请求） */
    public static final String FIELD_DEVICE_ID = "deviceId";
    /** 设备 IDs 字段（复数，用于批量查询请求） */
    public static final String FIELD_DEVICE_IDS = "deviceIds";
    /** 网关 ID 字段 */
    public static final String FIELD_GATEWAY_ID = "gatewayId";
    /** 设备 spk 字段 */
    public static final String FIELD_SPK = "spk";
    /** 搜索类型字段 */
    public static final String FIELD_SEARCH_TYPE = "searchType";
    /** 设备在线状态字段 */
    public static final String FIELD_ONLINE = "online";
    /** 设备名称字段 */
    public static final String FIELD_NAME = "name";
    /** 设备 SID 字段（设备在总线中的标识，用于场景控制） */
    public static final String FIELD_SCENE_SID = "sid";
}
