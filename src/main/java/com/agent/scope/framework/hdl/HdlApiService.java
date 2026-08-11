package com.agent.scope.framework.hdl;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.vo.ToolResultVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HDL 业务 API 服务实现（六边形架构适配器）。
 *
 * <p>本类是 {@link HdlApiPort} 端口的唯一实现，整合了原 hdl-agent 项目中
 * HdlIotService 与 HdlApiPortAdapter 的职责，通过 {@link HdlApiClient}
 * 调用 HDL 云端 API 完成智能家居、储能、商城等业务操作。</p>
 *
 * <p>所有方法均从 {@link SessionContext} 获取 accessToken 和 houseId，
 * 不再依赖原项目的 ToolSessionContext / UserSession，适配当前项目架构。</p>
 *
 * <p>方法返回 {@link ToolResultVO}，其中 routePath 指导前端渲染对应业务界面，
 * broadcastText 用于 SSE 推送时的语音播报。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HdlApiService implements HdlApiPort {

    // ===== 前端路由路径常量（用于 ToolResultVO.routePath） =====

    /** 房屋列表路由 */
    private static final String ROUTE_HOME_LIST = "/home/list";
    /** 设备列表路由 */
    private static final String ROUTE_DEVICE_LIST = "/device/list";
    /** 设备详情路由 */
    private static final String ROUTE_DEVICE_DETAIL = "/device/detail";
    /** 设备状态/控制路由 */
    private static final String ROUTE_DEVICE_STATUS = "/device/status";
    /** 场景列表路由 */
    private static final String ROUTE_SCENE_LIST = "/scene/list";
    /** 场景执行路由 */
    private static final String ROUTE_SCENE_EXECUTE = "/scene/execute";
    /** 场景创建路由 */
    private static final String ROUTE_SCENE_CREATE = "/scene/create";
    /** 产品详情路由 */
    private static final String ROUTE_PRODUCT_DETAIL = "/product/detail";
    /** 产品列表路由 */
    private static final String ROUTE_PRODUCT_LIST = "/product/list";
    /** 加入购物车路由 */
    private static final String ROUTE_CART_ADD = "/cart/add";
    /** 购物车列表路由 */
    private static final String ROUTE_CART_LIST = "/cart/list";
    /** 知识库问答路由 */
    private static final String ROUTE_QA_LIST = "/qa/list";
    /** 储能电站列表路由 */
    private static final String ROUTE_ENERGY_STATION_LIST = "/energy/station/list";
    /** 储能电站详情路由 */
    private static final String ROUTE_ENERGY_STATION_DETAIL = "/energy/station/detail";
    /** 逆变器信息路由 */
    private static final String ROUTE_ENERGY_INVERTER = "/energy/inverter/info";
    /** 知识库文档列表路由 */
    private static final String ROUTE_KNOWLEDGE_LIST = "/knowledge/list";

    // ===== 成功播报文本常量（用于 ToolResultVO.broadcastText） =====

    /** 房屋列表查询播报 */
    private static final String BROADCAST_HOME_LIST = "已查询到您的房屋列表";
    /** 设备列表查询播报 */
    private static final String BROADCAST_DEVICE_LIST = "已查询到您的设备列表";
    /** 设备详情查询播报 */
    private static final String BROADCAST_DEVICE_DETAIL = "已查询到设备详情";
    /** 设备控制播报 */
    private static final String BROADCAST_DEVICE_STATUS = "设备控制成功";
    /** 场景列表查询播报 */
    private static final String BROADCAST_SCENE_LIST = "已查询到场景列表";
    /** 场景执行播报 */
    private static final String BROADCAST_SCENE_EXECUTE = "场景执行成功";
    /** 场景创建播报 */
    private static final String BROADCAST_SCENE_CREATE = "场景创建成功";
    /** 产品详情查询播报 */
    private static final String BROADCAST_PRODUCT_DETAIL = "已查询到产品详情";
    /** 产品列表查询播报 */
    private static final String BROADCAST_PRODUCT_LIST = "已查询到产品列表";
    /** 加入购物车播报 */
    private static final String BROADCAST_CART_ADD = "已加入购物车";
    /** 购物车列表查询播报 */
    private static final String BROADCAST_CART_LIST = "已查询到购物车列表";
    /** 储能电站列表查询播报 */
    private static final String BROADCAST_ENERGY_STATION_LIST = "已查询到储能电站列表";
    /** 储能电站详情查询播报 */
    private static final String BROADCAST_ENERGY_STATION_DETAIL = "已查询到储能电站详情";
    /** 逆变器信息查询播报 */
    private static final String BROADCAST_ENERGY_INVERTER = "已查询到逆变器数据";

    /** 操作失败错误码 */
    private static final int CODE_OPERATION_FAILED = 500;
    /** 功能未实现错误码 */
    private static final int CODE_NOT_IMPLEMENTED = 501;

    /** HDL API HTTP 客户端 */
    private final HdlApiClient hdlApiClient;

    /** HDL API 配置属性 */
    private final HdlApiProperties hdlApiProperties;


    // ==================== 房屋相关 ====================

    /**
     * 查询房屋列表。
     *
     * <p>调用 HDL 房屋列表接口，返回当前用户名下所有房屋的 homeId 和 homeName。
     * homeType 设为 ALL 查询全部类型房屋，autoGenerate 设为 false 不自动生成默认房屋。</p>
     *
     * @param sessionContext 会话上下文（含 accessToken）
     * @return 工具结果 VO（data 为房屋列表，含 homeId、homeName）
     */
    @Override
    public ToolResultVO queryHomeList(SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("homeType", "ALL");
            data.put("autoGenerate", false);

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.HOME_LIST, data, true,
                    sessionContext.getAccessToken());
            if (resp.success()) {
                List<Map<String, Object>> rawList = extractListFromResponse(resp);
                List<Map<String, Object>> homeList = new ArrayList<>();
                if (rawList != null) {
                    for (Map<String, Object> raw : rawList) {
                        Map<String, Object> home = new LinkedHashMap<>();
                        home.put("homeId", raw.get("homeId"));
                        home.put("homeName", raw.get("homeName"));
                        homeList.add(home);
                    }
                }
                return buildSuccessResult("查询房屋列表成功", homeList,
                        ROUTE_HOME_LIST, BROADCAST_HOME_LIST);
            }
            log.warn("[HdlApiService] 查询房屋列表失败: code={}, msg={}", resp.getCode(), resp.getMsg());
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "查询房屋列表失败: " + resp.getMsg());
        } catch (Exception e) {
            log.error("[HdlApiService] 查询房屋列表异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }


    // ==================== 设备相关 ====================

    /**
     * 查询设备列表。
     *
     * <p>查询当前房屋下所有设备，返回 deviceId、name、spk、gatewayId、sid、online 等关键字段。
     * 控制设备前必须先调用此方法获取真实 deviceId 和 gatewayId。</p>
     *
     * @param spk            设备种类码（null 或空则查询全部）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为设备列表）
     */
    @Override
    public ToolResultVO queryDeviceList(String spk, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("homeId", sessionContext.getHouseId());
            data.put("searchType", "ALL");
            if (spk != null && !spk.isEmpty()) {
                data.put("spk", spk);
            }

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.DEVICE_LIST, data, true,
                    sessionContext.getAccessToken());
            if (resp.success()) {
                List<Map<String, Object>> rawList = extractListFromResponse(resp);
                List<Map<String, Object>> deviceList = new ArrayList<>();
                if (rawList != null) {
                    for (Map<String, Object> raw : rawList) {
                        Map<String, Object> device = new LinkedHashMap<>();
                        device.put("deviceId", raw.get("deviceId"));
                        device.put("name", raw.get("name"));
                        device.put("spk", raw.get("spk"));
                        device.put("gatewayId", raw.get("gatewayId"));
                        device.put("sid", raw.get("sid"));
                        device.put("online", raw.get("online"));
                        deviceList.add(device);
                    }
                }
                return buildSuccessResult("查询设备列表成功", deviceList,
                        ROUTE_DEVICE_LIST, BROADCAST_DEVICE_LIST);
            }
            log.warn("[HdlApiService] 查询设备列表失败: code={}, msg={}", resp.getCode(), resp.getMsg());
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "查询设备列表失败: " + resp.getMsg());
        } catch (Exception e) {
            log.error("[HdlApiService] 查询设备列表异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 查询设备列表完整数据（含 attributes 等全部字段）。
     *
     * <p>供内部工具方法使用，返回 HDL 原始设备数据，不做字段裁剪。
     * 响应失败时返回空列表，不抛异常。</p>
     *
     * @param sessionContext 会话上下文
     * @return 设备列表（List&lt;Map&gt;）；无数据或失败返回空列表
     */
    @Override
    public List<Map<String, Object>> queryDeviceListRaw(SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("homeId", sessionContext.getHouseId());
            data.put("searchType", "ALL");

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.DEVICE_LIST, data, true,
                    sessionContext.getAccessToken());
            if (resp.success()) {
                List<Map<String, Object>> list = extractListFromResponse(resp);
                return list != null ? list : new ArrayList<>();
            }
            log.warn("[HdlApiService] 查询设备原始列表失败: code={}, msg={}", resp.getCode(), resp.getMsg());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("[HdlApiService] 查询设备原始列表异常", e);
            return new ArrayList<>();
        }
    }

    /**
     * 查询设备详情（最新状态）。
     *
     * <p>传入设备 ID 列表（逗号分隔），返回这些设备的最新状态详情。
     * 用于控制设备后查询最新状态，或单独查询设备当前状态。</p>
     *
     * @param deviceIds      设备 ID 列表（逗号分隔）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为设备详情）
     */
    @Override
    public ToolResultVO queryDeviceDetail(String deviceIds, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deviceIds", deviceIds);
            data.put("homeId", sessionContext.getHouseId());

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.DEVICE_INFO, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "查询设备详情成功",
                    ROUTE_DEVICE_DETAIL, BROADCAST_DEVICE_DETAIL);
        } catch (Exception e) {
            log.error("[HdlApiService] 查询设备详情异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 控制单个设备。
     *
     * <p>将 attributesJson 解析为属性列表，构建单元素 actions 后委托给
     * {@link #batchControlDevice(List, String, String, SessionContext)} 执行。
     * 控制成功后自动查询设备详情供前端回显。</p>
     *
     * @param deviceId       设备 ID
     * @param gatewayId      网关 ID
     * @param spk            设备种类码
     * @param attributesJson 控制属性 JSON 字符串（如 [{"key":"power","value":1}]）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 含控制后设备详情）
     */
    @Override
    public ToolResultVO controlDevice(String deviceId, String gatewayId, String spk,
                                       String attributesJson, SessionContext sessionContext) {
        try {
            // 使用 fastjson2 解析控制属性 JSON 为 List<Map>
            List<Map<String, Object>> attributes = new ArrayList<>();
            if (attributesJson != null && !attributesJson.isEmpty()) {
                JSONArray attrArray = JSON.parseArray(attributesJson);
                for (int i = 0; i < attrArray.size(); i++) {
                    JSONObject obj = attrArray.getJSONObject(i);
                    attributes.add(new LinkedHashMap<>(obj));
                }
            }

            // 构建单元素 actions 列表
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("deviceId", deviceId);
            action.put("spk", spk);
            action.put("attributes", attributes);

            List<Map<String, Object>> actions = new ArrayList<>();
            actions.add(action);

            // 委托批量控制方法执行
            return batchControlDevice(actions, gatewayId, deviceId, sessionContext);
        } catch (Exception e) {
            log.error("[HdlApiService] 控制设备异常: deviceId={}, gatewayId={}", deviceId, gatewayId, e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 批量控制设备（接收 JSON 字符串）。
     *
     * <p>使用 fastjson2 解析 actionsJson 为动作数组，提取首个动作的 gatewayId，
     * 拼接所有 deviceId，转换为 List&lt;Map&gt; 后委托给
     * {@link #batchControlDevice(List, String, String, SessionContext)} 执行。</p>
     *
     * @param actionsJson    设备动作 JSON 数组字符串
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    @Override
    public ToolResultVO batchControlDevice(String actionsJson, SessionContext sessionContext) {
        try {
            JSONArray actionsArray = JSON.parseArray(actionsJson);
            if (actionsArray == null || actionsArray.isEmpty()) {
                return ToolResultVO.failure(CODE_OPERATION_FAILED, "设备动作列表为空");
            }

            String gatewayId = null;
            StringBuilder deviceIdsBuilder = new StringBuilder();
            List<Map<String, Object>> actions = new ArrayList<>();

            for (int i = 0; i < actionsArray.size(); i++) {
                JSONObject jsonAction = actionsArray.getJSONObject(i);
                Map<String, Object> action = new LinkedHashMap<>(jsonAction);

                // 从首个动作提取 gatewayId
                if (i == 0 && action.get("gatewayId") != null) {
                    gatewayId = String.valueOf(action.get("gatewayId"));
                }

                // 拼接所有 deviceId（逗号分隔）
                if (action.get("deviceId") != null) {
                    if (deviceIdsBuilder.length() > 0) {
                        deviceIdsBuilder.append(",");
                    }
                    deviceIdsBuilder.append(action.get("deviceId"));
                }
                actions.add(action);
            }

            String deviceIds = deviceIdsBuilder.toString();
            return batchControlDevice(actions, gatewayId, deviceIds, sessionContext);
        } catch (Exception e) {
            log.error("[HdlApiService] 批量控制设备异常: actionsJson={}", actionsJson, e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 批量控制设备（接收已解析的 actions）。
     *
     * <p>构建包含 homeId、gatewayId、actions 的请求体调用 HDL 设备控制接口。
     * 每个 action 仅保留 deviceId、spk、attributes 三个字段。
     * 控制成功后查询 deviceIdsForDetail 对应设备的最新状态，返回 devices 数组供前端回显。</p>
     *
     * @param actions            设备动作列表（每个 action 含 deviceId、spk、attributes）
     * @param gatewayId          网关 ID
     * @param deviceIdsForDetail 控制成功后需查询详情的设备 ID 列表（逗号分隔）
     * @param sessionContext     会话上下文
     * @return 工具结果 VO（data 含 devices 设备详情数组）
     */
    @Override
    public ToolResultVO batchControlDevice(List<Map<String, Object>> actions, String gatewayId,
                                            String deviceIdsForDetail, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("homeId", sessionContext.getHouseId());
            data.put("gatewayId", gatewayId);

            // 构建干净的 actions 列表（仅保留 deviceId、spk、attributes）
            List<Map<String, Object>> cleanActions = new ArrayList<>();
            for (Map<String, Object> action : actions) {
                Map<String, Object> clean = new LinkedHashMap<>();
                clean.put("deviceId", action.get("deviceId"));
                clean.put("spk", action.get("spk"));
                clean.put("attributes", action.get("attributes"));
                cleanActions.add(clean);
            }
            data.put("actions", cleanActions);

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.DEVICE_CONTROL, data, true,
                    sessionContext.getAccessToken());
            if (!resp.success()) {
                log.warn("[HdlApiService] 批量控制设备失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                return ToolResultVO.failure(CODE_OPERATION_FAILED, "设备控制失败: " + resp.getMsg());
            }

            // 控制成功后查询设备详情，返回最新状态供前端回显
            List<Map<String, Object>> devices = new ArrayList<>();
            if (deviceIdsForDetail != null && !deviceIdsForDetail.isEmpty()) {
                Map<String, Object> detailData = new LinkedHashMap<>();
                detailData.put("deviceIds", deviceIdsForDetail);
                detailData.put("homeId", sessionContext.getHouseId());
                HdlResponse detailResp = hdlApiClient.post(HdlApiConstants.DEVICE_INFO, detailData, true,
                        sessionContext.getAccessToken());
                if (detailResp.success()) {
                    List<Map<String, Object>> detailList = extractListFromResponse(detailResp);
                    if (detailList != null && !detailList.isEmpty()) {
                        devices = detailList;
                    } else {
                        log.warn("[HdlApiService] 设备详情查询返回空列表: deviceIds={}", deviceIdsForDetail);
                    }
                } else {
                    log.warn("[HdlApiService] 设备详情查询失败: code={}, msg={}, deviceIds={}",
                            detailResp.getCode(), detailResp.getMsg(), deviceIdsForDetail);
                }
            }
            // 兜底：如果查询失败，从 actions 构造最小设备信息供前端展示
            if (devices.isEmpty()) {
                for (Map<String, Object> action : actions) {
                    Map<String, Object> dev = new LinkedHashMap<>();
                    dev.put("deviceId", action.get("deviceId"));
                    dev.put("spk", action.get("spk"));
                    dev.put("deviceName", "设备(" + action.get("deviceId") + ")");
                    dev.put("controlResult", "success");
                    devices.add(dev);
                }
                log.info("[HdlApiService] 使用 action 数据构造设备信息: count={}", devices.size());
            }

            // 包装为前端 DeviceStatusPage 期望的格式：{devices: [...], multiDevice: boolean}
            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("devices", devices);
            if (devices.size() > 1) {
                resultData.put("multiDevice", true);
            }
            return buildSuccessResult("设备控制成功", resultData,
                    ROUTE_DEVICE_STATUS, BROADCAST_DEVICE_STATUS);
        } catch (Exception e) {
            log.error("[HdlApiService] 批量控制设备异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }


    // ==================== 场景相关 ====================

    /**
     * 查询场景列表。
     *
     * <p>查询指定房屋下的场景列表，homeId 为空时自动使用会话上下文中的 houseId。
     * roomId 可选，传入时查询指定房间下的场景。</p>
     *
     * @param homeId         房屋 ID（为空时使用 sessionContext.getHouseId()）
     * @param roomId         房间 ID（可选）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为场景列表）
     */
    @Override
    public ToolResultVO querySceneList(String homeId, String roomId, SessionContext sessionContext) {
        try {
            String effectiveHomeId = (homeId != null && !homeId.isEmpty())
                    ? homeId : sessionContext.getHouseId();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("homeId", effectiveHomeId);
            if (roomId != null && !roomId.isEmpty()) {
                data.put("roomId", roomId);
            }

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.SCENE_LIST, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "查询场景列表成功",
                    ROUTE_SCENE_LIST, BROADCAST_SCENE_LIST);
        } catch (Exception e) {
            log.error("[HdlApiService] 查询场景列表异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 执行场景。
     *
     * <p>触发指定场景的执行，场景下的所有设备动作将一次性下发。</p>
     *
     * @param sceneId        场景 ID
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    @Override
    public ToolResultVO executeScene(String sceneId, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("homeId", sessionContext.getHouseId());
            data.put("sceneId", sceneId);

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.SCENE_EXECUTE, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "场景执行成功",
                    ROUTE_SCENE_EXECUTE, BROADCAST_SCENE_EXECUTE);
        } catch (Exception e) {
            log.error("[HdlApiService] 执行场景异常: sceneId={}", sceneId, e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 创建场景。
     *
     * <p>向 HDL 下发场景创建请求，body 中如未包含 homeId 则自动填充当前房屋 ID。</p>
     *
     * @param body           场景参数（含场景名称、设备动作等）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    @Override
    public ToolResultVO createScene(Map<String, Object> body, SessionContext sessionContext) {
        try {
            if (body == null) {
                body = new LinkedHashMap<>();
            }
            // body 中未指定 homeId 时，自动填充当前会话房屋 ID
            if (!body.containsKey("homeId")) {
                body.put("homeId", sessionContext.getHouseId());
            }

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.SCENE_ADD, body, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "场景创建成功",
                    ROUTE_SCENE_CREATE, BROADCAST_SCENE_CREATE);
        } catch (Exception e) {
            log.error("[HdlApiService] 创建场景异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }


    // ==================== 产品/商城相关 ====================

    /**
     * 查询产品详情。
     *
     * <p>查询商城产品详情信息，含价格、规格、库存等。</p>
     *
     * @param productId      产品 ID
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为产品详情）
     */
    @Override
    public ToolResultVO queryProductDetail(String productId, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("productId", productId);

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.PRODUCT_MALL_INFO, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "查询产品详情成功",
                    ROUTE_PRODUCT_DETAIL, BROADCAST_PRODUCT_DETAIL);
        } catch (Exception e) {
            log.error("[HdlApiService] 查询产品详情异常: productId={}", productId, e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 查询产品详情原始数据。
     *
     * <p>返回 HDL 原始响应 Map，含 skuId、price、erpNo 等加购必需字段。
     * 供内部加购流程使用，失败时返回 null。</p>
     *
     * @param productId      产品 ID
     * @param sessionContext 会话上下文
     * @return HDL 原始响应 Map，失败返回 null
     */
    @Override
    public Map<String, Object> queryProductDetailRaw(String productId, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("productId", productId);

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.PRODUCT_MALL_INFO, data, true,
                    sessionContext.getAccessToken());
            if (resp.success()) {
                return resp.getDataAsMap();
            }
            log.warn("[HdlApiService] 查询产品原始详情失败: code={}, msg={}", resp.getCode(), resp.getMsg());
            return null;
        } catch (Exception e) {
            log.error("[HdlApiService] 查询产品原始详情异常: productId={}", productId, e);
            return null;
        }
    }

    /**
     * 搜索产品列表。
     *
     * <p>按产品名关键词搜索商城产品列表。</p>
     *
     * @param productName    产品名关键词
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为产品列表）
     */
    @Override
    public ToolResultVO searchProductList(String productName, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("productName", productName);

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.PRODUCT_MALL_LIST, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "搜索产品列表成功",
                    ROUTE_PRODUCT_LIST, BROADCAST_PRODUCT_LIST);
        } catch (Exception e) {
            log.error("[HdlApiService] 搜索产品列表异常: productName={}", productName, e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 加入购物车。
     *
     * <p>将指定 SKU 加入购物车，支持指定数量和 ERP 编号。</p>
     *
     * @param skuId          SKU ID
     * @param productId      产品 ID
     * @param quantity       数量
     * @param erpNo          ERP 编号（可选）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    @Override
    public ToolResultVO addCart(String skuId, String productId, int quantity,
                                 String erpNo, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("skuId", skuId);
            data.put("productId", productId);
            data.put("quantity", quantity);
            if (erpNo != null && !erpNo.isEmpty()) {
                data.put("erpNo", erpNo);
            }

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.CART_ADD, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "加入购物车成功",
                    ROUTE_CART_ADD, BROADCAST_CART_ADD);
        } catch (Exception e) {
            log.error("[HdlApiService] 加入购物车异常: skuId={}, productId={}", skuId, productId, e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 查询购物车列表。
     *
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为购物车列表）
     */
    @Override
    public ToolResultVO queryCartList(SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.CART_LIST, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "查询购物车列表成功",
                    ROUTE_CART_LIST, BROADCAST_CART_LIST);
        } catch (Exception e) {
            log.error("[HdlApiService] 查询购物车列表异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }


    // ==================== 知识库相关（桩实现） ====================

    /**
     * 知识库检索（RAG）。
     *
     * <p>当前为桩实现，完整 RAG 功能需配置 Qdrant 向量库 + Embedding 服务。
     * 后续接入后替换为真实的向量检索逻辑。</p>
     *
     * @param question       用户问题
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（未实现提示）
     */
    @Override
    public ToolResultVO queryKnowledge(String question, SessionContext sessionContext) {
        log.info("[HdlApiService] 知识库检索功能未配置: question={}", question);
        return ToolResultVO.failure(CODE_NOT_IMPLEMENTED,
                "知识库检索功能尚未配置，请先配置 RAG 服务");
    }

    /**
     * 列出知识库所有文档。
     *
     * <p>当前为桩实现，完整功能需配置 RAG 服务。</p>
     *
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（未实现提示）
     */
    @Override
    public ToolResultVO listKnowledgeDocuments(SessionContext sessionContext) {
        log.info("[HdlApiService] 知识库文档列表功能未配置");
        return ToolResultVO.failure(CODE_NOT_IMPLEMENTED,
                "知识库检索功能尚未配置，请先配置 RAG 服务");
    }

    /**
     * 删除知识库文档。
     *
     * <p>当前为桩实现，完整功能需配置 RAG 服务。</p>
     *
     * @param docCode        文档编码
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（未实现提示）
     */
    @Override
    public ToolResultVO deleteKnowledgeDocument(String docCode, SessionContext sessionContext) {
        log.info("[HdlApiService] 知识库文档删除功能未配置: docCode={}", docCode);
        return ToolResultVO.failure(CODE_NOT_IMPLEMENTED,
                "知识库检索功能尚未配置，请先配置 RAG 服务");
    }


    // ==================== 储能/逆变器相关 ====================

    /**
     * 查询储能电站列表。
     *
     * <p>查询当前用户名下的储能电站列表，支持按电站名称关键词过滤。</p>
     *
     * @param homeName       电站名称关键词（可为空）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为电站列表）
     */
    @Override
    public ToolResultVO queryEnergyStationList(String homeName, SessionContext sessionContext) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            if (homeName != null && !homeName.isEmpty()) {
                data.put("homeName", homeName);
            }

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.POWER_STATION_LIST, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "查询储能电站列表成功",
                    ROUTE_ENERGY_STATION_LIST, BROADCAST_ENERGY_STATION_LIST);
        } catch (Exception e) {
            log.error("[HdlApiService] 查询储能电站列表异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 查询储能电站详情。
     *
     * <p>查询指定电站的详细信息，homeId 为空时自动使用会话上下文中的 houseId。</p>
     *
     * @param homeId         电站 ID（可为空时自动填充）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为电站详情）
     */
    @Override
    public ToolResultVO queryEnergyStationDetail(String homeId, SessionContext sessionContext) {
        try {
            String effectiveHomeId = (homeId != null && !homeId.isEmpty())
                    ? homeId : sessionContext.getHouseId();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("homeId", effectiveHomeId);

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.POWER_STATION_INFO, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "查询储能电站详情成功",
                    ROUTE_ENERGY_STATION_DETAIL, BROADCAST_ENERGY_STATION_DETAIL);
        } catch (Exception e) {
            log.error("[HdlApiService] 查询储能电站详情异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 查询逆变器实时数据。
     *
     * <p>查询指定电站下逆变器的实时运行数据，homeId 为空时自动使用会话上下文中的 houseId。</p>
     *
     * @param homeId         电站 ID
     * @param sessionContext 会话上下文
     * @return 工具结果 VO（data 为逆变器数据）
     */
    @Override
    public ToolResultVO queryInverterInfo(String homeId, SessionContext sessionContext) {
        try {
            String effectiveHomeId = (homeId != null && !homeId.isEmpty())
                    ? homeId : sessionContext.getHouseId();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("homeId", effectiveHomeId);

            HdlResponse resp = hdlApiClient.post(HdlApiConstants.DEVICE_INVERTER_ALL_INFO, data, true,
                    sessionContext.getAccessToken());
            return convertHdlResponse(resp, "查询逆变器数据成功",
                    ROUTE_ENERGY_INVERTER, BROADCAST_ENERGY_INVERTER);
        } catch (Exception e) {
            log.error("[HdlApiService] 查询逆变器数据异常", e);
            return ToolResultVO.failure(CODE_OPERATION_FAILED, "操作失败: " + e.getMessage());
        }
    }


    // ==================== 辅助方法 ====================

    /**
     * 构建成功结果。
     *
     * <p>统一封装 {@link ToolResultVO#success(String, Object, String, String)} 调用，
     * 减少重复代码。</p>
     *
     * @param message       成功消息
     * @param data          业务数据
     * @param routePath     前端路由路径
     * @param broadcastText 成功播报文本
     * @return ToolResultVO 实例
     */
    private ToolResultVO buildSuccessResult(String message, Object data,
                                             String routePath, String broadcastText) {
        return ToolResultVO.success(message, data, routePath, broadcastText);
    }

    /**
     * 从 HDL 响应中提取列表数据（兼容两种格式）。
     *
     * <p>HDL API 返回的 data 可能是直接数组 [{...}]，也可能是 Map 包装 {list: [{...}]}。
     * 本方法先尝试 {@link HdlResponse#getDataAsList()} (直接数组)，
     * 为空时回退到 {@link HdlResponse#getDataAsMap()} 从 list/records/devices 键下提取。</p>
     *
     * @param resp HDL 响应
     * @return 提取的列表（无数据返回空列表，不返回 null）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractListFromResponse(HdlResponse resp) {
        // 1. 先尝试 data 是直接数组
        List<Map<String, Object>> list = resp.getDataAsList();
        if (list != null) {
            return list;
        }
        // 2. data 是 Map，尝试从常见键下提取
        Map<String, Object> dataMap = resp.getDataAsMap();
        if (dataMap != null) {
            Object listObj = dataMap.get("list");
            if (listObj == null) listObj = dataMap.get("records");
            if (listObj == null) listObj = dataMap.get("devices");
            if (listObj instanceof List<?> rawList) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            map.put(String.valueOf(e.getKey()), e.getValue());
                        }
                        result.add(map);
                    }
                }
                return result;
            }
        }
        return new ArrayList<>();
    }

    /**
     * 将 HDL 响应转换为工具结果。
     *
     * <p>响应成功时返回包含 data 的成功结果（附带路由路径和播报文本）；
     * 响应失败时返回包含错误信息的失败结果。</p>
     *
     * @param resp         HDL 响应
     * @param successMsg   成功消息
     * @param routePath    前端路由路径
     * @param broadcastMsg 成功播报文本
     * @return ToolResultVO 实例
     */
    private ToolResultVO convertHdlResponse(HdlResponse resp, String successMsg,
                                             String routePath, String broadcastMsg) {
        if (resp != null && resp.success()) {
            return buildSuccessResult(successMsg, resp.getData(), routePath, broadcastMsg);
        }
        String errorMsg = (resp != null) ? resp.getMsg() : "响应为空";
        return ToolResultVO.failure(CODE_OPERATION_FAILED, errorMsg);
    }

    /**
     * 解析设备 ID 字符串为列表。
     *
     * <p>将逗号分隔的设备 ID 字符串拆分为 List，自动去除前后空白。</p>
     *
     * @param deviceIds 设备 ID 列表（逗号分隔）
     * @return 设备 ID 列表
     */
    private List<String> parseDeviceIds(String deviceIds) {
        List<String> result = new ArrayList<>();
        if (deviceIds == null || deviceIds.isEmpty()) {
            return result;
        }
        for (String id : deviceIds.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
