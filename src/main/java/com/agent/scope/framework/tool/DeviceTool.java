package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.AttributeMutexRules;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.hdl.port.SpkAttributeResolver;
import com.agent.scope.framework.service.DeviceContextService;
import com.agent.scope.framework.vo.ToolResultVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备工具（特性 3：ReAct 智能体与工具调用）。
 *
 * <p>封装 HDL 设备查询和控制能力。用户说"开灯"、"RGB 开蓝色亮度 98"时，
 * LLM 通过 ReAct 推理调用 batch_control_device 工具完成设备控制。</p>
 *
 * <p><b>核心能力</b>：</p>
 * <ul>
 *   <li>{@code query_device_list}：查询当前房屋下所有设备</li>
 *   <li>{@code query_device_detail}：查询设备最新状态</li>
 *   <li>{@code batch_control_device}：批量控制多个设备（多设备合并一次请求）</li>
 * </ul>
 *
 * <p><b>属性解析</b>：使用 {@link SpkAttributeResolver} 基于 spk-schemas.json 做确定性关键词匹配，
 * 把用户自然语言指令（如"RGB 开蓝色亮度 98"）转换为 HDL 后端期望的属性列表，
 * 不依赖 LLM 输出属性 key，提升准确度。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTool extends AbstractTool {

    /**
     * HDL 业务 API 端口
     */
    private final HdlApiPort hdlApiPort;

    /**
     * SPK 物模型属性解析器（确定性关键词匹配，不调 LLM）
     */
    private final SpkAttributeResolver spkAttributeResolver;

    /**
     * 属性互斥规则端口（Nacos 热重载）
     */
    private final AttributeMutexRules attributeMutexRules;

    /**
     * 设备列表上下文缓存服务（缓存查询结果并注入系统提示词，避免重复查询）
     */
    private final DeviceContextService deviceContextService;

    /**
     * colorful 属性名（HDL 物模型协议字段）
     */
    private static final String ATTR_COLORFUL = "colorful";

    /**
     * 查询设备列表。
     *
     * <p>查询当前房屋下所有设备，返回设备 ID、名称、种类码、网关 ID、sid 等信息。
     * 控制设备前必须先调用此工具获取真实 deviceId 和 gatewayId。</p>
     *
     * @param runtimeContext 运行时上下文（自动注入，含会话信息）
     * @return 工具结果 VO（data 为设备列表 JSON）
     */
    @Tool(name = "query_device_list",
            description = """
                    查询 HDL 设备列表。返回当前房屋下所有设备的 ID、名称、种类码、网关 ID、sid 等信息。
                    使用场景：全开/全关场景，或系统提示词中无缓存的设备列表时调用。若系统提示词已包含【当前房屋设备列表（缓存）】，直接使用缓存数据，无需调用本工具。
                    禁止事项：禁止根据用户描述编造设备 ID 或网关 ID，所有 ID 必须来自本工具的返回结果或系统提示词中的缓存列表。
                    """,
            readOnly = true)
    public ToolResultVO queryDeviceList(RuntimeContext runtimeContext) {
        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[DeviceTool] 查询设备列表: userId={}, houseId={}",
                sessionContext.getUserId(), sessionContext.getHouseId());
        // spk 传 null，查询全部设备（避免 LLM 传入错误 spk 导致过滤后为空）
        ToolResultVO result = hdlApiPort.queryDeviceList(null, sessionContext);
        // 缓存设备列表到 Redis，下次推理时注入系统提示词，LLM 无需再调用本工具
        if (result.isSuccess() && result.getData() != null) {
            deviceContextService.cacheDeviceList(sessionContext.getHouseId(), result.getData());
        }
        return result;
    }

    /**
     * 查询设备详情（最新状态）。
     *
     * @param deviceIds      设备 ID 列表（逗号分隔）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 为设备详情 JSON 数组）
     */
    @Tool(name = "query_device_detail",
            description = """
                    查询 HDL 设备详情（最新状态）。传入设备 ID 列表（逗号分隔），返回设备的开关、亮度、色温、在线状态等。
                    使用场景：控制设备后查询最新状态，或单独查询设备当前状态。
                    参数来源要求：设备 ID 必须来自 query_device_list 的返回结果，禁止编造。
                    """,
            readOnly = true)
    public ToolResultVO queryDeviceDetail(
            @ToolParam(name = "deviceIds",
                    required = true,
                    description = "设备ID列表，逗号分隔。必须来自query_device_list返回的真实设备ID，禁止使用示例ID"
            ) String deviceIds,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[DeviceTool] 查询设备详情: deviceIds={}, userId={}",
                deviceIds, sessionContext.getUserId());
        return hdlApiPort.queryDeviceDetail(deviceIds, sessionContext);
    }

    /**
     * 批量控制设备。
     *
     * <p>多设备控制合并到一次 HTTP 请求，HDL actions 数组包含多个设备的控制动作。
     * 每个设备使用 spk + userInput 做确定性属性解析，生成 HDL 期望的 attributes 格式。</p>
     *
     * @param actionsJson    设备动作 JSON 数组字符串
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 含 controlResult 和 devices 设备详情）
     */
    @Tool(name = "batch_control_device",
            description = """
                    批量控制多个 HDL 设备（多设备合并一次请求）。
                    使用场景：当用户指令包含一个或多个设备控制时使用此工具，如'RGB开蓝色亮度77调光开冷色亮度99'、'打开客厅灯'。
                    全关/全开场景：用户说'全关'、'全开'、'关闭所有设备'、'打开所有设备'时，直接传 mode='all_off' 或 mode='all_on'，无需传 actionsJson，工具内部自动查询所有设备并控制。禁止逐个枚举设备！
                    参数来源要求：actionsJson 中每个元素的 deviceId/gatewayId/spk/deviceName 必须来自 query_device_list 返回结果。
                    设备名称优先匹配：用户输入中的设备关键词应优先匹配设备列表中名称包含该关键词的设备。
                    多设备合并：用户输入可能没有逗号分隔符（如'调光开冷色亮度48RGB开红色亮度88'），这是多个设备控制指令连写，必须识别为多设备控制合并为一次 batch_control_device 调用，禁止拆分成多次调用。
                    【场景化指令处理】：当用户说'观影模式''会客模式''睡眠模式''浪漫模式'等场景词时，必须为每个设备提供 attributes 参数（直接指定属性值），不能只传 userInput 场景词。
                    LLM 应根据场景语义自行推断每个设备的合理属性值，如观影模式→灯光亮度20%暖色+空调26度制冷。
                    禁止事项：禁止编造设备 ID、网关 ID 或种类码；禁止使用示例值。
                    """,
            readOnly = true,
            concurrencySafe = false
    )
    public ToolResultVO batchControlDevice(
            @ToolParam(
                    name = "actionsJson",
                    required = false,
                    description = """
                            设备动作JSON数组字符串。
                            【直接控制指令】（如'开灯''亮度调到50'）：传 userInput，工具自动解析属性。
                            格式：[{"deviceId":"<真实ID>","gatewayId":"<真实ID>","spk":"<真实spk>","userInput":"开灯","deviceName":"<真实名称>"}]。
                            【场景化指令】（如'观影模式''睡眠模式'）：必须传 attributes，LLM 自行推断属性值。
                            格式：[{"deviceId":"<真实ID>","gatewayId":"<真实ID>","spk":"light.rgbcw","deviceName":"RGBCW灯","attributes":[{"key":"on_off","value":"on"},{"key":"brightness","value":20},{"key":"cct","value":3000}]}]。
                            attributes 的 key 和 value 必须符合 spk 对应的物模型属性定义（可写属性）。
                            全关/全开时留空，传mode参数。deviceId/gatewayId/spk/deviceName必须来自query_device_list返回结果
                            """
            ) String actionsJson,
            @ToolParam(name = "mode", required = false,
                    description = "全关/全开模式。'all_off'=全关所有设备，'all_on'=全开所有设备。设置后无需传actionsJson，工具内部自动查询并控制所有设备") String mode,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[DeviceTool] 批量控制设备: actionsJson={}, mode={}, userId={}",
                actionsJson, mode, sessionContext.getUserId());

        // 全关/全开模式：工具内部查询所有设备并控制，避免 LLM 逐个枚举设备导致 token 生成耗时巨大
        if (mode != null && !mode.isBlank()) {
            return batchControlAllDevices(mode, sessionContext);
        }

        // 1. 解析 actionsJson
        JSONArray actions = JSON.parseArray(actionsJson);
        if (actions == null || actions.isEmpty()) {
            return ToolResultVO.failure(400, "actionsJson 解析失败或为空，全关/全开请传 mode 参数");
        }

        // 2. 对每个 action 做属性解析
        List<Map<String, Object>> resolvedActions = new ArrayList<>(actions.size());
        String gatewayId = null;
        StringBuilder deviceIdsForDetail = new StringBuilder();

        for (int i = 0; i < actions.size(); i++) {
            JSONObject action = actions.getJSONObject(i);
            String deviceId = action.getString("deviceId");
            String actionGatewayId = action.getString("gatewayId");
            String spk = action.getString("spk");
            String userInput = action.getString("userInput");
            String deviceName = action.getString("deviceName");
            // LLM 直传的属性列表（场景化指令时使用，绕过确定性解析器）
            JSONArray directAttrs = action.getJSONArray("attributes");

            if (deviceId == null || deviceId.isBlank()) {
                return ToolResultVO.failure(400, "第 " + (i + 1) + " 个 action 缺少 deviceId");
            }
            if (spk == null || spk.isBlank()) {
                return ToolResultVO.failure(400, "第 " + (i + 1) + " 个 action 缺少 spk");
            }

            // 构建 HDL 期望的 attributes 格式：[{key, value}]
            List<Map<String, Object>> attributes;

            if (directAttrs != null && !directAttrs.isEmpty()) {
                // 【场景化指令路径】LLM 直接提供属性值（如观影模式→brightness:20, cct:3000）
                // 绕过 SpkAttributeResolverImpl，信任 LLM 的语义推断
                attributes = new ArrayList<>(directAttrs.size());
                for (int j = 0; j < directAttrs.size(); j++) {
                    JSONObject attrObj = directAttrs.getJSONObject(j);
                    Map<String, Object> attr = new LinkedHashMap<>(2);
                    attr.put("key", attrObj.getString("key"));
                    attr.put("value", attrObj.get("value"));
                    attributes.add(attr);
                }
                log.info("[DeviceTool] 使用 LLM 直传属性: deviceId={}, spk={}, attrs={}", deviceId, spk, attributes);
            } else {
                // 【直接控制指令路径】确定性属性解析：spk + userInput → Map<String, Object>
                if (userInput == null || userInput.isBlank()) {
                    return ToolResultVO.failure(400, "第 " + (i + 1) + " 个 action 缺少 userInput 或 attributes，"
                            + "请至少提供一种。场景化指令（如观影模式）请提供 attributes 参数");
                }

                Map<String, Object> attrMap = spkAttributeResolver.resolveAttributes(spk, userInput);
                if (attrMap == null || attrMap.isEmpty()) {
                    // 解析失败：返回设备 schema 摘要，引导 LLM 自行推断属性值并重试
                    String schemaSummary = spkAttributeResolver.getSchemaSummary(spk);
                    return ToolResultVO.failure(422, "无法解析指令「" + userInput + "」的属性。"
                            + "请根据设备物模型自行推断属性值，通过 attributes 参数重新调用。"
                            + schemaSummary);
                }

                // RGB 与 colorful 互斥逻辑
                applyColorfulMutex(attrMap, userInput);

                attributes = new ArrayList<>(attrMap.size());
                for (Map.Entry<String, Object> entry : attrMap.entrySet()) {
                    String attrKey = entry.getKey();
                    Object attrValue = entry.getValue();

                    // 检测相对调整标记（STEP_UP:{step} / STEP_DOWN:{step}）
                    // 查询设备当前值并加/减步长，实现"调高一点""调低一点"等相对调整
                    if (attrValue instanceof String strVal
                            && (strVal.startsWith("STEP_UP:") || strVal.startsWith("STEP_DOWN:"))) {
                        Object resolvedValue = resolveStepAdjustValue(deviceId, attrKey,
                                strVal, sessionContext);
                        if (resolvedValue != null) {
                            attrValue = resolvedValue;
                            log.info("[DeviceTool] 相对调整成功: deviceId={}, attrKey={}, marker={}, newValue={}",
                                    deviceId, attrKey, strVal, resolvedValue);
                        } else {
                            log.warn("[DeviceTool] 相对调整解析失败，跳过属性: deviceId={}, attrKey={}",
                                    deviceId, attrKey);
                            continue;
                        }
                    }

                    Map<String, Object> attr = new LinkedHashMap<>(2);
                    attr.put("key", attrKey);
                    attr.put("value", attrValue);
                    attributes.add(attr);
                }
            }

            // 构建解析后的 action（含 deviceName 供前端回显）
            Map<String, Object> resolvedAction = new LinkedHashMap<>(4);
            resolvedAction.put("deviceId", deviceId);
            resolvedAction.put("spk", spk);
            resolvedAction.put("attributes", attributes);
            if (deviceName != null && !deviceName.isBlank()) {
                resolvedAction.put("deviceName", deviceName);
            }
            resolvedActions.add(resolvedAction);

            // 提取 gatewayId（同一网关下的设备才能批量控制）
            if (gatewayId == null) {
                gatewayId = actionGatewayId;
            }

            // 拼接 deviceIds 供控制后查询详情
            if (deviceIdsForDetail.length() > 0) {
                deviceIdsForDetail.append(",");
            }
            deviceIdsForDetail.append(deviceId);

            log.info("[DeviceTool] 设备属性解析: deviceId={}, spk={}, deviceName={}, userInput={}, attrs={}",
                    deviceId, spk, deviceName, userInput, attributes);
        }

        // 3. 调用 HDL API 批量控制
        return hdlApiPort.batchControlDevice(resolvedActions, gatewayId,
                deviceIdsForDetail.toString(), sessionContext);
    }

    /**
     * 全关/全开模式：工具内部查询所有设备并批量控制。
     *
     * <p>LLM 传 mode='all_off' 或 'all_on' 即可，无需逐个枚举设备，
     * 避免 token 生成耗时巨大（20 个设备需要生成大量 JSON）。</p>
     *
     * @param mode           'all_off' 或 'all_on'
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    private ToolResultVO batchControlAllDevices(String mode, SessionContext sessionContext) {
        log.info("[DeviceTool] 全关/全开模式: mode={}, userId={}", mode, sessionContext.getUserId());

        // 查询所有设备
        List<Map<String, Object>> allDevices = hdlApiPort.queryDeviceListRaw(sessionContext);
        if (allDevices.isEmpty()) {
            return ToolResultVO.failure(400, "未查询到任何设备，无法执行全关/全开操作");
        }

        String userInput = "all_off".equals(mode) ? "关" : "开";
        List<Map<String, Object>> resolvedActions = new ArrayList<>(allDevices.size());
        String gatewayId = null;
        StringBuilder deviceIdsForDetail = new StringBuilder();

        for (Map<String, Object> device : allDevices) {
            String deviceId = String.valueOf(device.get("deviceId"));
            String spk = String.valueOf(device.get("spk"));
            String deviceName = String.valueOf(device.getOrDefault("name", deviceId));
            String gwId = String.valueOf(device.get("gatewayId"));

            if (deviceId == null || deviceId.isBlank() || "null".equals(deviceId)) continue;
            if (spk == null || spk.isBlank() || "null".equals(spk)) continue;

            // 属性解析
            Map<String, Object> attrMap = spkAttributeResolver.resolveAttributes(spk, userInput);
            if (attrMap == null || attrMap.isEmpty()) {
                log.warn("[DeviceTool] 全关/全开属性解析为空，跳过: deviceId={}, spk={}", deviceId, spk);
                continue;
            }

            // 构建 attributes
            List<Map<String, Object>> attributes = new ArrayList<>(attrMap.size());
            for (Map.Entry<String, Object> entry : attrMap.entrySet()) {
                Map<String, Object> attr = new LinkedHashMap<>(2);
                attr.put("key", entry.getKey());
                attr.put("value", entry.getValue());
                attributes.add(attr);
            }

            Map<String, Object> resolvedAction = new LinkedHashMap<>(4);
            resolvedAction.put("deviceId", deviceId);
            resolvedAction.put("spk", spk);
            resolvedAction.put("attributes", attributes);
            resolvedAction.put("deviceName", deviceName);
            resolvedActions.add(resolvedAction);

            if (gatewayId == null) gatewayId = gwId;
            if (deviceIdsForDetail.length() > 0) deviceIdsForDetail.append(",");
            deviceIdsForDetail.append(deviceId);
        }

        log.info("[DeviceTool] 全关/全开构建完成: mode={}, deviceCount={}", mode, resolvedActions.size());
        return hdlApiPort.batchControlDevice(resolvedActions, gatewayId,
                deviceIdsForDetail.toString(), sessionContext);
    }

    /**
     * RGB 与 colorful 互斥逻辑。
     *
     * <p>规则：用户未提到"炫彩"时移除 colorful 属性，保留 rgb 和 on_off；
     * 用户提到"炫彩"时保留 colorful，移除 rgb 和 on_off。</p>
     *
     * @param attrMap   属性 Map（会被修改）
     * @param userInput 用户输入
     */
    private void applyColorfulMutex(Map<String, Object> attrMap, String userInput) {
        String colorfulKeyword = attributeMutexRules.getColorfulKeyword();
        Map<String, List<String>> mutexRules = attributeMutexRules.getAttrMutexRules();

        if (mutexRules == null || mutexRules.isEmpty()) {
            return;
        }

        boolean hasColorfulKeyword = userInput.contains(colorfulKeyword);
        boolean hasColorfulAttr = attrMap.containsKey(ATTR_COLORFUL);

        if (hasColorfulAttr && !hasColorfulKeyword) {
            // 用户未提到炫彩，移除 colorful
            attrMap.remove(ATTR_COLORFUL);
            log.info("[DeviceTool] 移除 colorful 属性（用户未提到炫彩）");
        } else if (hasColorfulKeyword) {
            // 用户提到炫彩，移除与 colorful 互斥的属性
            List<String> mutexAttrs = mutexRules.get(ATTR_COLORFUL);
            if (mutexAttrs != null) {
                for (String mutexAttr : mutexAttrs) {
                    attrMap.remove(mutexAttr);
                }
                // 确保 colorful 存在
                if (!attrMap.containsKey(ATTR_COLORFUL)) {
                    attrMap.put(ATTR_COLORFUL, "on");
                }
                log.info("[DeviceTool] 保留 colorful，移除互斥属性: {}", mutexAttrs);
            }
        }
    }

    /**
     * 处理相对调整标记（STEP_UP:{step} / STEP_DOWN:{step}）。
     * <p>
     * 查询设备当前属性值，加/减步长后返回新值。
     * 用于"调高一点""调低一点""调亮一点""调暗一点"等相对调整指令。
     * </p>
     *
     * @param deviceId       设备 ID
     * @param attrKey        属性 key（如 brightness、cct、set_temp、volume）
     * @param stepMarker     "STEP_UP:10" 或 "STEP_DOWN:10"
     * @param sessionContext 会话上下文
     * @return 计算后的新值（Integer 或 Double），查询失败返回 null
     */
    private Object resolveStepAdjustValue(String deviceId, String attrKey,
                                          String stepMarker, SessionContext sessionContext) {
        // 解析方向和步长
        boolean isUp = stepMarker.startsWith("STEP_UP:");
        int step;
        try {
            step = Integer.parseInt(stepMarker.substring(stepMarker.indexOf(':') + 1));
        } catch (NumberFormatException e) {
            log.warn("[DeviceTool] 相对调整步长解析失败: {}", stepMarker);
            return null;
        }

        // 查询设备当前状态
        ToolResultVO detailResult = hdlApiPort.queryDeviceDetail(deviceId, sessionContext);
        if (detailResult == null || !detailResult.isSuccess() || detailResult.getData() == null) {
            log.warn("[DeviceTool] 相对调整：查询设备详情失败, deviceId={}", deviceId);
            return null;
        }

        // 从返回数据中提取设备列表（data 可能是 List 或 Map 含 list/devices/records 键）
        List<?> deviceList = null;
        Object data = detailResult.getData();
        if (data instanceof List<?> list) {
            deviceList = list;
        } else if (data instanceof Map<?, ?> map) {
            Object listObj = map.get("list");
            if (listObj == null) listObj = map.get("devices");
            if (listObj == null) listObj = map.get("records");
            if (listObj instanceof List<?> rawList) {
                deviceList = rawList;
            }
        }

        if (deviceList == null || deviceList.isEmpty()) {
            log.warn("[DeviceTool] 相对调整：设备列表为空, deviceId={}", deviceId);
            return null;
        }

        // 遍历设备列表找到目标设备，提取属性当前值
        for (Object devObj : deviceList) {
            if (!(devObj instanceof Map<?, ?> dev)) {
                continue;
            }
            String devId = String.valueOf(dev.get("deviceId"));
            if (!deviceId.equals(devId)) {
                continue;
            }
            // 设备的 attributes 数组：[{key, value}]
            Object attrsObj = dev.get("attributes");
            if (attrsObj instanceof List<?> attrs) {
                for (Object attrObj : attrs) {
                    if (!(attrObj instanceof Map<?, ?> attr)) {
                        continue;
                    }
                    String key = String.valueOf(attr.get("key"));
                    if (attrKey.equals(key)) {
                        Object val = attr.get("value");
                        if (val instanceof Number num) {
                            double currentVal = num.doubleValue();
                            double newVal = isUp ? currentVal + step : currentVal - step;
                            return (newVal == Math.floor(newVal) && !Double.isInfinite(newVal))
                                    ? (int) newVal : newVal;
                        }
                    }
                }
            }
        }

        log.warn("[DeviceTool] 相对调整：未找到属性当前值, deviceId={}, attrKey={}", deviceId, attrKey);
        return null;
    }
}