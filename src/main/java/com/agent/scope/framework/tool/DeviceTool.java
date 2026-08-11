package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.AttributeMutexRules;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.hdl.port.SpkAttributeResolver;
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

    /** HDL 业务 API 端口 */
    private final HdlApiPort hdlApiPort;

    /** SPK 物模型属性解析器（确定性关键词匹配，不调 LLM） */
    private final SpkAttributeResolver spkAttributeResolver;

    /** 属性互斥规则端口（Nacos 热重载） */
    private final AttributeMutexRules attributeMutexRules;

    /** colorful 属性名（HDL 物模型协议字段） */
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
            description = "查询 HDL 设备列表。返回当前房屋下所有设备的 ID、名称、种类码、网关 ID、sid 等信息。"
                    + "使用场景：控制设备或创建场景前必须先调用此工具获取真实 deviceId、gatewayId、sid、spk。"
                    + "禁止事项：禁止根据用户描述编造设备 ID 或网关 ID，所有 ID 必须来自本工具的返回结果。",
            readOnly = true)
    public ToolResultVO queryDeviceList(RuntimeContext runtimeContext) {
        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[DeviceTool] 查询设备列表: userId={}, houseId={}",
                sessionContext.getUserId(), sessionContext.getHouseId());
        // spk 传 null，查询全部设备（避免 LLM 传入错误 spk 导致过滤后为空）
        return hdlApiPort.queryDeviceList(null, sessionContext);
    }

    /**
     * 查询设备详情（最新状态）。
     *
     * @param deviceIds      设备 ID 列表（逗号分隔）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 为设备详情 JSON 数组）
     */
    @Tool(name = "query_device_detail",
            description = "查询 HDL 设备详情（最新状态）。传入设备 ID 列表（逗号分隔），返回设备的开关、亮度、色温、在线状态等。"
                    + "使用场景：控制设备后查询最新状态，或单独查询设备当前状态。"
                    + "参数来源要求：设备 ID 必须来自 query_device_list 的返回结果，禁止编造。",
            readOnly = true)
    public ToolResultVO queryDeviceDetail(
            @ToolParam(name = "deviceIds", required = true,
                    description = "设备ID列表，逗号分隔。必须来自query_device_list返回的真实设备ID，禁止使用示例ID") String deviceIds,
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
            description = "批量控制多个 HDL 设备（多设备合并一次请求）。"
                    + "使用场景：当用户指令包含一个或多个设备控制时使用此工具，如'RGB开蓝色亮度77调光开冷色亮度99'、'打开客厅灯'。"
                    + "全关/全开场景：用户说'全关'、'全开'、'关闭所有设备'、'打开所有设备'时，直接传 mode='all_off' 或 mode='all_on'，"
                    + "无需传 actionsJson，工具内部自动查询所有设备并控制。禁止逐个枚举设备！"
                    + "参数来源要求：actionsJson 中每个元素的 deviceId/gatewayId/spk/deviceName 必须来自 query_device_list 返回结果。"
                    + "设备名称优先匹配：用户输入中的设备关键词应优先匹配设备列表中名称包含该关键词的设备。"
                    + "多设备合并：用户输入可能没有逗号分隔符（如'调光开冷色亮度48RGB开红色亮度88'），"
                    + "这是多个设备控制指令连写，必须识别为多设备控制合并为一次 batch_control_device 调用，禁止拆分成多次调用。"
                    + "禁止事项：禁止编造设备 ID、网关 ID 或种类码；禁止使用示例值。",
            readOnly = true,
            concurrencySafe = false)
    public ToolResultVO batchControlDevice(
            @ToolParam(name = "actionsJson", required = false,
                    description = "设备动作JSON数组字符串，格式：[{\"deviceId\":\"<query_device_list返回的deviceId>\",\"gatewayId\":\"<query_device_list返回的gatewayId>\",\"spk\":\"<query_device_list返回的spk>\",\"userInput\":\"用户对该设备的控制描述\",\"deviceName\":\"<query_device_list返回的设备名称>\"}]。全关/全开时留空，传mode参数。deviceId/gatewayId/spk/deviceName必须来自query_device_list返回结果") String actionsJson,
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

            if (deviceId == null || deviceId.isBlank()) {
                return ToolResultVO.failure(400, "第 " + (i + 1) + " 个 action 缺少 deviceId");
            }
            if (spk == null || spk.isBlank()) {
                return ToolResultVO.failure(400, "第 " + (i + 1) + " 个 action 缺少 spk");
            }
            if (userInput == null || userInput.isBlank()) {
                return ToolResultVO.failure(400, "第 " + (i + 1) + " 个 action 缺少 userInput");
            }

            // 确定性属性解析：spk + userInput → Map<String, Object>
            Map<String, Object> attrMap = spkAttributeResolver.resolveAttributes(spk, userInput);
            if (attrMap == null || attrMap.isEmpty()) {
                // 无法解析属性的设备（如传感器）跳过，不中断整个批量操作
                log.warn("[DeviceTool] 第 {} 个 action 属性解析为空，跳过: deviceId={}, spk={}, userInput={}",
                        i + 1, deviceId, spk, userInput);
                continue;
            }

            // RGB 与 colorful 互斥逻辑
            applyColorfulMutex(attrMap, userInput);

            // 构建 HDL 期望的 attributes 格式：[{key, value}]
            List<Map<String, Object>> attributes = new ArrayList<>(attrMap.size());
            for (Map.Entry<String, Object> entry : attrMap.entrySet()) {
                Map<String, Object> attr = new LinkedHashMap<>(2);
                attr.put("key", entry.getKey());
                attr.put("value", entry.getValue());
                attributes.add(attr);
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
                    deviceId, spk, deviceName, userInput, attrMap);
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
}
