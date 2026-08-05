package com.agent.scope.framework.hdl;

import com.agent.scope.framework.context.UserSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * HDL 智能家居服务
 *
 * <p>对标小程序 home-skill 的 apis/* 全部接口，封装房屋/设备/控制能力，
 * 并实现 ensureHome 流程干涉逻辑（单房屋自动选择、多房屋需用户选择）。</p>
 *
 * @author zqs
 * @since 1.0.0
 */
@Slf4j
@Service
public class HdlIotService {

    // ===== 登录/Token 字段名常量 =====
    /** accessToken 字段名 */
    private static final String FIELD_ACCESS_TOKEN = "accessToken";
    /** refreshToken 字段名 */
    private static final String FIELD_REFRESH_TOKEN = "refreshToken";

    // ===== 房屋/设备查询类型常量 =====
    /** 查询全部类型（homeType/searchType=ALL） */
    private static final String SEARCH_TYPE_ALL = "ALL";

    // ===== 分页列表字段名常量 =====
    /** 列表字段名：list */
    private static final String FIELD_LIST = "list";
    /** 列表字段名：records */
    private static final String FIELD_RECORDS = "records";
    /** 列表字段名：homes */
    private static final String FIELD_HOMES = "homes";
    /** 列表字段名：devices */
    private static final String FIELD_DEVICES = "devices";
    /** 设备属性字段名 */
    private static final String FIELD_ATTRIBUTES = "attributes";

    // ===== 设备总线标识候选字段名 =====
    // HDL 设备列表 API 可能用不同字段名返回设备总线标识，依次尝试所有候选字段
    /** 设备 UID 字段名（HDL 设备列表可能用此字段返回设备总线标识） */
    private static final String FIELD_UID = "uid";
    /** 设备 UID 字段名（备选字段名） */
    private static final String FIELD_DEVICE_UID = "deviceUid";
    /** 设备 UID 候选字段名（HDL 不同接口可能使用不同字段名返回设备总线标识） */
    private static final String FIELD_DEV_UID = "devUid";
    /** 设备序列号候选字段名 */
    private static final String FIELD_DEVICE_SN = "deviceSn";
    /** 设备序列号候选字段名（简写） */
    private static final String FIELD_SN = "sn";
    /** 设备序列号候选字段名 */
    private static final String FIELD_SERIAL_NO = "serialNo";
    /** 设备 MAC 地址候选字段名 */
    private static final String FIELD_MAC = "mac";
    /** 设备地址候选字段名 */
    private static final String FIELD_ADDR = "addr";
    /** 设备地址候选字段名（全称） */
    private static final String FIELD_DEVICE_ADDR = "deviceAddr";
    /** 设备 NIC 标识候选字段名 */
    private static final String FIELD_NIC = "nic";
    /** 设备串号候选字段名（HDL 总线设备常用） */
    private static final String FIELD_DEV_SN = "devSn";
    /** 布尔值 true 字符串形式 */
    private static final String BOOL_TRUE_STR = "true";
    /** 布尔值 true 数字编码 */
    private static final String BOOL_TRUE_CODE = "1";

    private final HdlApiClient apiClient;
    private final HdlJsonUtil jsonUtil;

    public HdlIotService(HdlApiClient apiClient, HdlJsonUtil jsonUtil) {
        this.apiClient = apiClient;
        this.jsonUtil = jsonUtil;
    }

    /**
     * 用户登录（对标 login.js）
     *
     * @return 登录结果：isSuccess / expiresIn / msg
     */
    public LoginResult login(String loginName, String loginPwd, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_LOGIN_NAME, loginName);
        data.put(HdlApiConstants.FIELD_LOGIN_PWD, loginPwd);
        data.put(HdlApiConstants.FIELD_GRANT_TYPE, HdlApiConstants.GRANT_TYPE_PASSWORD);

        HdlResponse resp = apiClient.post(HdlApiConstants.LOGIN, data, true, false, session);
        LoginResult result = new LoginResult();
        Map<String, Object> dataMap = resp.getDataAsMap();
        if (resp.success() && dataMap != null) {
            Object accessToken = dataMap.get(FIELD_ACCESS_TOKEN);
            Object refreshToken = dataMap.get(FIELD_REFRESH_TOKEN);
            if (accessToken != null) {
                session.setHdlAccessToken(accessToken.toString());
                if (refreshToken != null) {
                    session.setHdlRefreshToken(refreshToken.toString());
                }
                session.setLoginName(loginName);
                result.setSuccess(true);
                result.setExpiresIn(toLong(dataMap.get(HdlApiConstants.FIELD_EXPIRES_IN)));
                log.info("[IotService] 登录成功, loginName={}", loginName);
                return result;
            }
        }
        result.setSuccess(false);
        result.setMsg(resp.getMsg() != null ? resp.getMsg() : "用户名或密码不正确");
        return result;
    }

    /**
     * 查询房屋列表（对标 queryHomeList.js）
     *
     * @param session 用户会话
     * @return 房屋列表（可能为空列表，表示查询失败或无房屋）
     */
    public List<HdlHome> queryHomeList(UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_TYPE, SEARCH_TYPE_ALL);
        data.put(HdlApiConstants.FIELD_AUTO_GENERATE, false);

        HdlResponse resp = apiClient.post(HdlApiConstants.HOME_LIST, data, true, true, session);
        if (resp.success() && resp.getData() != null) {
            List<HdlHome> homes = parseHomes(resp.getData());
            session.setHomeCache(homes);
            session.setHomeCacheTime(System.currentTimeMillis());
            // 不自动选择房屋：无论单房屋还是多房屋，都由用户在弹窗中手动选择
            // 前端通过 /api/auth/status 返回的 needSelectHome=true 弹出 HomeSelectModal
            log.info("[IotService] 查询房屋列表: {} 个, 等待用户手动选择", homes.size());
            return homes;
        }
        log.warn("[IotService] 查询房屋失败: {}", resp.getMsg());
        return new ArrayList<>();
    }

    /**
     * 选择房屋（对标 selectHome.js）
     *
     * @param homeName 房屋名称
     * @param session  用户会话
     * @return true 表示选择成功，false 表示未找到匹配房屋
     */
    public boolean selectHome(String homeName, UserSession session) {
        if (session.getHomeCache() == null) {
            return false;
        }
        for (HdlHome home : session.getHomeCache()) {
            if (Objects.equals(homeName, home.getHomeName())) {
                session.setCurrentHomeId(home.getHomeId());
                session.setCurrentHomeName(home.getHomeName());
                session.setDeviceCache(null); // 切换房屋清除设备缓存
                log.info("[IotService] 切换房屋: {}", homeName);
                return true;
            }
        }
        return false;
    }

    /**
     * 获取设备列表（对标 getDeviceList.js）
     *
     * @param spk     空间前缀（可为 null）
     * @param session 用户会话
     * @return 设备列表（可能为空列表）
     */
    public List<HdlDevice> getDeviceList(String spk, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_ID, session.getCurrentHomeId());
        data.put(HdlApiConstants.FIELD_SEARCH_TYPE, SEARCH_TYPE_ALL);
        if (spk != null && !spk.isEmpty()) {
            data.put(HdlApiConstants.FIELD_SPK, spk);
        }

        HdlResponse resp = apiClient.post(HdlApiConstants.DEVICE_LIST, data, true, true, session);
        if (resp.success() && resp.getData() != null) {
            List<HdlDevice> devices = parseDevices(resp.getData());
            session.setDeviceCache(devices);
            session.setDeviceCacheTime(System.currentTimeMillis());
            log.info("[IotService] 查询设备列表: {} 个", devices.size());
            return devices;
        }
        log.warn("[IotService] 查询设备失败: {}", resp.getMsg());
        return new ArrayList<>();
    }

    /**
     * 获取设备详情（对标 getDeviceInfo.js）
     *
     * @param deviceIds 设备 ID 列表
     * @param session   用户会话
     * @return 设备列表（可能为空列表）
     */
    public List<HdlDevice> getDeviceInfo(List<String> deviceIds, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_ID, session.getCurrentHomeId());
        data.put(HdlApiConstants.FIELD_DEVICE_IDS, deviceIds);

        HdlResponse resp = apiClient.post(HdlApiConstants.DEVICE_INFO, data, true, true, session);
        if (resp.success() && resp.getData() != null) {
            return parseDevices(resp.getData());
        }
        return new ArrayList<>();
    }

    // ===== 设备控制 / 场景 / 产品 / 购物车（直连 REST API 使用） =====

    /**
     * 获取设备详情（原始响应）。
     *
     * <p>与 {@link #getDeviceInfo(List, UserSession)} 不同，本方法直接返回 HDL 原始响应，
     * 不经过 toDevice 解析，保留 data 字段原始结构供前端使用。</p>
     *
     * @param deviceIds 设备 ID 列表
     * @param session   用户会话
     * @return HDL 原始响应
     */
    public HdlResponse getDeviceDetail(List<String> deviceIds, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_ID, session.getCurrentHomeId());
        data.put(HdlApiConstants.FIELD_DEVICE_IDS, deviceIds);
        return apiClient.post(HdlApiConstants.DEVICE_INFO, data, true, true, session);
    }

    /**
     * 设备控制（对标 controlDevice.js）。
     *
     * <p>构建 HDL 控制请求体 {homeId, gatewayId, actions:[{deviceId, spk, attributes}]}，
     * 调用 /home-wisdom/app/device/control。</p>
     *
     * @param deviceId   设备 ID（将字符串化，避免大数精度丢失）
     * @param gatewayId  网关 ID
     * @param spk        空间前缀（可为 null，传空串）
     * @param attributes 属性列表 [{key, value}]
     * @param session    用户会话
     * @return HDL 原始响应
     */
    public HdlResponse controlDevice(String deviceId, String gatewayId, String spk,
                                     List<Map<String, Object>> attributes, UserSession session) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put(HdlApiConstants.FIELD_DEVICE_ID, String.valueOf(deviceId));
        action.put(HdlApiConstants.FIELD_SPK, spk == null ? "" : spk);
        action.put(FIELD_ATTRIBUTES, attributes);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_ID, session.getCurrentHomeId());
        data.put(HdlApiConstants.FIELD_GATEWAY_ID, gatewayId);
        data.put("actions", List.of(action));

        return apiClient.post(HdlApiConstants.DEVICE_CONTROL, data, true, true, session);
    }

    /**
     * 批量设备控制。
     *
     * <p>多设备控制合并到一次 HTTP 请求中，actions 数组包含多个设备的控制动作。
     * 每个 action 包含 deviceId、spk、attributes（[{key, value}] 格式）。</p>
     *
     * <p>对标 HDL /home-wisdom/app/device/control 接口，请求体格式：</p>
     * <pre>{@code
     * {
     *   "homeId": "xxx",
     *   "gatewayId": "xxx",
     *   "actions": [
     *     {"deviceId":"dev1","spk":"light.rgb","attributes":[{"key":"on_off","value":"on"},{"key":"brightness","value":77},{"key":"rgb","value":"200,200,255"}]},
     *     {"deviceId":"dev2","spk":"light.cct","attributes":[{"key":"on_off","value":"on"},{"key":"brightness","value":77},{"key":"cct","value":77}]}
     *   ]
     * }
     * }</pre>
     *
     * @param actions  设备动作列表，每个 Map 含 deviceId、spk、attributes（List<Map>，每个 Map 含 key 和 value）
     * @param gatewayId 网关 ID（同一网关下的设备才能批量控制）
     * @param session   用户会话
     * @return HDL 原始响应
     */
    public HdlResponse batchControlDevice(List<Map<String, Object>> actions, String gatewayId,
                                          UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_ID, session.getCurrentHomeId());
        data.put(HdlApiConstants.FIELD_GATEWAY_ID, gatewayId);
        data.put("actions", actions);

        return apiClient.post(HdlApiConstants.DEVICE_CONTROL, data, true, true, session);
    }

    /**
     * 场景列表（对标 scene/list）。
     *
     * @param homeId  房屋 ID（为 null 时使用 session 当前房屋）
     * @param roomId  房间 ID（可为 null）
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse getSceneList(String homeId, String roomId, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_ID, homeId != null ? homeId : session.getCurrentHomeId());
        if (roomId != null && !roomId.isEmpty()) {
            data.put("roomId", roomId);
        }
        return apiClient.post(HdlApiConstants.SCENE_LIST, data, true, true, session);
    }

    /**
     * 场景详情（对标 scene/info）。
     *
     * @param sceneId 场景 ID
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse getSceneDetail(String sceneId, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userSceneIds", List.of(sceneId));
        return apiClient.post(HdlApiConstants.SCENE_INFO, data, true, true, session);
    }

    /**
     * 创建场景（对标 scene/add）。
     *
     * <p>前端请求体字段：{sceneName, functions:[{sid, delaySeconds, status:[{key,value}]}], collect, delaySeconds, executePush, gatewayId?}</p>
     * <p>HDL 要求格式：{homeId, scenes:[{name, gatewayId, collect, delay, executePush, functions:[{sid, delaySeconds, status}]}]}</p>
     *
     * <p>本方法做两件事：</p>
     * <ol>
     *   <li>把前端扁平结构包装为 scenes 数组（HDL 要求 scenes 不能为空）</li>
     *   <li>补充 homeId</li>
     * </ol>
     *
     * @param body    场景参数（前端扁平结构）
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse createScene(Map<String, Object> body, UserSession session) {
        // 校验 homeId 非空，避免向 HDL 网关发送 homeId=null 导致创建失败
        String currentHomeId = session != null ? session.getCurrentHomeId() : null;
        if (currentHomeId == null || currentHomeId.isBlank()) {
            log.error("[HdlIotService] createScene 失败：当前会话未绑定 homeId，无法创建场景");
            HdlResponse fail = new HdlResponse();
            fail.setIsSuccess(false);
            fail.setCode(-1);
            fail.setMsg("当前会话未绑定家庭，无法创建场景");
            return fail;
        }

        // 构造 HDL 要求的 scenes 数组格式
        Map<String, Object> scene = new LinkedHashMap<>();
        // 场景名称：sceneName → name
        Object sceneName = body.get("sceneName");
        if (sceneName != null) {
            scene.put(HdlApiConstants.FIELD_NAME, String.valueOf(sceneName));
        }
        // gatewayId：从前端传入，或从 functions[0] 推断
        Object gatewayId = body.get(HdlApiConstants.FIELD_GATEWAY_ID);
        if (gatewayId == null) {
            // 从 functions 列表中的 sid 无法直接获取 gatewayId，需要前端传入或从设备缓存获取
            // 这里兜底取 body 中可能存在的 gatewayId 字段
            gatewayId = body.get("gatewayId");
        }
        if (gatewayId != null) {
            scene.put(HdlApiConstants.FIELD_GATEWAY_ID, String.valueOf(gatewayId));
        }
        // collect：boolean → int（HDL 要求 0/1）
        Object collect = body.get("collect");
        if (collect != null) {
            scene.put("collect", collect instanceof Boolean b ? (b ? 1 : 0) : collect);
        }
        // delay：秒 → 毫秒字符串
        Object delaySeconds = body.get("delaySeconds");
        if (delaySeconds != null) {
            int seconds = 0;
            if (delaySeconds instanceof Number n) {
                seconds = n.intValue();
            } else {
                try { seconds = Integer.parseInt(String.valueOf(delaySeconds)); } catch (NumberFormatException ignored) {}
            }
            scene.put("delay", String.valueOf(seconds * 1000));
        }
        // executePush
        Object executePush = body.get("executePush");
        if (executePush != null) {
            scene.put("executePush", executePush);
        }
        // functions：直接透传
        Object functions = body.get("functions");
        if (functions != null) {
            scene.put("functions", functions);
        }

        // 组装 HDL 请求体（homeId 已在方法入口校验非空）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_ID, currentHomeId);
        data.put("scenes", List.of(scene));

        log.info("[HdlIotService] 创建场景请求体转换: sceneName={}, functions={}",
                sceneName, functions != null ? "有" : "无");
        return apiClient.post(HdlApiConstants.SCENE_ADD, data, true, true, session);
    }

    /**
     * 更新场景（对标 scene/update）。
     *
     * @param body    场景参数（含 sceneId）
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse updateScene(Map<String, Object> body, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>(body);
        data.put(HdlApiConstants.FIELD_HOME_ID, session.getCurrentHomeId());
        return apiClient.post(HdlApiConstants.SCENE_UPDATE, data, true, true, session);
    }

    /**
     * 执行场景（对标 scene/execute）。
     *
     * @param sceneId 场景 ID
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse executeScene(String sceneId, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userSceneIds", List.of(sceneId));
        return apiClient.post(HdlApiConstants.SCENE_EXECUTE, data, true, true, session);
    }

    /**
     * 删除场景（对标 scene/delete）。
     *
     * @param sceneId 场景 ID
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse deleteScene(String sceneId, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userSceneIds", List.of(sceneId));
        return apiClient.post(HdlApiConstants.SCENE_DELETE, data, true, true, session);
    }

    /**
     * 产品详情（对标 getMallProductInfo）。
     *
     * @param productId 产品 ID
     * @param session    用户会话
     * @return HDL 原始响应
     */
    public HdlResponse getProductDetail(String productId, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", productId);
        return apiClient.post(HdlApiConstants.PRODUCT_MALL_INFO, data, true, true, session);
    }

    /**
     * 产品列表搜索（对标 mallList）。
     *
     * <p>按产品名关键词搜索产品列表，用于 ReAct 模式下产品名→productId 解析。
     * 返回的列表中每个产品含 productId、productNameCn、SKU 等字段。</p>
     *
     * @param productName 产品名关键词（如 "方悦"）
     * @param session     用户会话
     * @return HDL 原始响应（data.list 为产品列表）
     */
    public HdlResponse searchProductList(String productName, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productName", productName);
        data.put("pageNo", 1);
        data.put("pageSize", 20);
        return apiClient.post(HdlApiConstants.PRODUCT_MALL_LIST, data, true, true, session);
    }

    /**
     * 加入购物车（对标 shoppingCarts/add）。
     *
     * @param skuId     SKU ID
     * @param productId 产品 ID
     * @param quantity  数量（<=0 时默认 1）
     * @param erpNo     ERP 编号（可为 null）
     * @param session   用户会话
     * @return HDL 原始响应
     */
    public HdlResponse addCart(String skuId, String productId, int quantity, String erpNo, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skuId", skuId);
        data.put("productId", productId);
        data.put("skuNum", quantity > 0 ? quantity : 1);
        if (erpNo != null && !erpNo.isEmpty()) {
            data.put("erpNo", erpNo);
        }
        return apiClient.post(HdlApiConstants.CART_ADD, data, true, true, session);
    }

    /**
     * 购物车列表（对标 shoppingCarts/list）。
     *
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse getCartList(UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNo", 1);
        data.put("pageSize", 20);
        return apiClient.post(HdlApiConstants.CART_LIST, data, true, true, session);
    }

    // ===== 储能服务（对标 agent-skills-energy.yml） =====

    /**
     * 储能电站列表（对标 powerStation/page）。
     *
     * <p>查询用户名下所有储能电站，返回电站名称、发电功率、今日发电量、电池容量等。
     * 不需要 homeId（查全量电站列表）。</p>
     *
     * @param homeName 电站名称关键词（可为空，模糊搜索）
     * @param session  用户会话
     * @return HDL 原始响应（data.list 为电站列表）
     */
    public HdlResponse getEnergyStationList(String homeName, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (homeName != null && !homeName.isBlank()) {
            data.put("homeName", homeName);
        }
        data.put("pageNo", 1);
        data.put("pageSize", 20);
        return apiClient.post(HdlApiConstants.POWER_STATION_LIST, data, true, true, session);
    }

    /**
     * 储能电站详情（对标 powerStation/info）。
     *
     * <p>查询指定电站的详情信息，包括电站类型、并网类型、装机容量、投产日期、电价等。
     * 房屋即电站，homeId = session.currentHomeId。</p>
     *
     * @param homeId  电站 ID（等同 homeId）
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse getEnergyStationDetail(String homeId, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("homeId", homeId);
        return apiClient.post(HdlApiConstants.POWER_STATION_INFO, data, true, true, session);
    }

    /**
     * 逆变器实时数据（对标 device/inverter/allInfo）。
     *
     * <p>查询储能逆变器实时数据，包括电池 SOC、充放电功率、光伏发电功率、
     * 负载功率、今日发电量、电价阶段等。</p>
     *
     * @param homeId  电站 ID（等同 homeId）
     * @param session 用户会话
     * @return HDL 原始响应
     */
    public HdlResponse getInverterInfo(String homeId, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("homeId", homeId);
        return apiClient.post(HdlApiConstants.DEVICE_INVERTER_ALL_INFO, data, true, true, session);
    }

    // ===== 解析方法 =====

    /**
     * 解析房屋列表
     *
     * <p>兼容两种 data 形态：</p>
     * <ul>
     *   <li>HDL home/list 接口：data 直接是数组 [{ homeId, homeName }, ...]</li>
     *   <li>部分接口：data 是对象 { list: [...] } 或 { records: [...] }</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<HdlHome> parseHomes(Object data) {
        List<HdlHome> homes = new ArrayList<>();
        if (data == null) return homes;

        Object list = data;
        // 如果 data 是 Map，尝试从中取出 list/records 字段
        if (data instanceof Map<?, ?> map) {
            list = map.get(FIELD_LIST);
            if (list == null) list = map.get(FIELD_RECORDS);
            if (list == null) list = map.get(FIELD_HOMES);
            // 如果 Map 里没有 list 字段，但 Map 本身就是单个房屋对象
            if (list == null && map.containsKey(HdlApiConstants.FIELD_HOME_ID)) {
                homes.add(toHome(map));
                return homes;
            }
        }

        if (list instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    homes.add(toHome(m));
                }
            }
        }
        return homes;
    }

    private HdlHome toHome(Map<?, ?> m) {
        HdlHome home = new HdlHome();
        home.setHomeId(getString(m, HdlApiConstants.FIELD_HOME_ID));
        home.setHomeName(getString(m, HdlApiConstants.FIELD_HOME_NAME));
        home.setHomeType(getString(m, HdlApiConstants.FIELD_HOME_TYPE));
        home.setDeviceCount(toInt(m.get(HdlApiConstants.FIELD_DEVICE_COUNT)));
        home.setRemoteControl(toBool(m.get(HdlApiConstants.FIELD_IS_REMOTE_CONTROL)));
        return home;
    }

    /**
     * 解析设备列表
     *
     * <p>兼容两种 data 形态：</p>
     * <ul>
     *   <li>HDL device/list 接口：data 直接是数组 [{ deviceId, name, spk }, ...]</li>
     *   <li>部分接口：data 是对象 { list: [...] } 或单个设备对象 { deviceId, ... }</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<HdlDevice> parseDevices(Object data) {
        List<HdlDevice> devices = new ArrayList<>();
        if (data == null) return devices;

        Object list = data;
        // 如果 data 是 Map，尝试从中取出 list/records 字段
        if (data instanceof Map<?, ?> map) {
            list = map.get(FIELD_LIST);
            if (list == null) list = map.get(FIELD_RECORDS);
            if (list == null) list = map.get(FIELD_DEVICES);
            // 如果 Map 里没有 list 字段，但 Map 本身就是单个设备对象
            if (list == null && map.containsKey(HdlApiConstants.FIELD_DEVICE_ID)) {
                devices.add(toDevice(map));
                return devices;
            }
        }

        if (list instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    devices.add(toDevice(m));
                }
            }
        }
        return devices;
    }

    @SuppressWarnings("unchecked")
    private HdlDevice toDevice(Map<?, ?> m) {
        HdlDevice device = new HdlDevice();
        device.setDeviceId(getString(m, HdlApiConstants.FIELD_DEVICE_ID));
        device.setGatewayId(getString(m, HdlApiConstants.FIELD_GATEWAY_ID));
        device.setName(getString(m, HdlApiConstants.FIELD_NAME));
        // 设备 SID：场景创建时 functions.sid 需要此值
        // HDL 设备列表 API 可能用不同字段名返回设备总线标识，依次尝试所有候选字段
        // 候选字段顺序：sid → uid → deviceUid → devUid → deviceSn → sn → serialNo → mac → addr → deviceAddr → nic → devSn
        String deviceSid = getString(m, HdlApiConstants.FIELD_SCENE_SID);
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_UID);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_DEVICE_UID);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_DEV_UID);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_DEVICE_SN);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_SN);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_SERIAL_NO);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_MAC);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_ADDR);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_DEVICE_ADDR);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_NIC);
        }
        if (deviceSid == null || deviceSid.isEmpty()) {
            deviceSid = getString(m, FIELD_DEV_SN);
        }
        device.setSid(deviceSid);
        device.setSpk(getString(m, HdlApiConstants.FIELD_SPK));
        device.setOnline(toBool(m.get(HdlApiConstants.FIELD_ONLINE)));
        // 调试日志：记录设备原始字段和所有值，用于排查 sid 字段名
        // 打印所有字段值，方便定位哪个字段包含期望的 sid（如 0301010F33CD6D01020400010103）
        log.info("[IotService] 设备原始字段: deviceId={}, keys={}, sid={}, allFields={}",
                device.getDeviceId(), m.keySet(), device.getSid(), m);
        Object attrs = m.get(FIELD_ATTRIBUTES);
        if (attrs instanceof Map<?, ?> am) {
            Map<String, Object> attrMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : am.entrySet()) {
                attrMap.put(String.valueOf(e.getKey()), e.getValue());
            }
            device.setAttributes(attrMap);
        }
        return device;
    }

    private String getString(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long toLong(Object v) {
        if (v == null) return 0;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean toBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return BOOL_TRUE_STR.equalsIgnoreCase(String.valueOf(v)) || BOOL_TRUE_CODE.equals(String.valueOf(v));
    }

    // ===== 结果 DTO =====

    @Data
    public static class LoginResult {
        private boolean success;
        private long expiresIn;
        private String msg;
    }
}
