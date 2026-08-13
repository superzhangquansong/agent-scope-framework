package com.agent.scope.framework.service;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.AttributeMutexRules;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.hdl.port.SpkAttributeResolver;
import com.agent.scope.framework.vo.ToolResultVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地快速通道执行器（仿 hdl-agent IntelligentFallbackExecutor）。
 *
 * <h3>两层路由策略</h3>
 * <ol>
 *   <li><b>关键词预匹配</b>（0 LLM 调用，0 延迟）：查询类/全开全关等明确意图直接命中，
 *       关键词从 Nacos 配置读取，配置驱动非硬编码</li>
 *   <li><b>1.5b 分类兜底</b>（1 次 Ollama 调用）：控制类指令（"开灯""RGB开蓝色"）需要
 *       提取设备名和操作参数，走 1.5b JSON 分类</li>
 * </ol>
 *
 * <h3>为什么 1.5b 只做兜底</h3>
 * <p>
 * 实测 1.5b 对"我有哪些设备"等查询类指令分类极不稳定（把查询当控制），
 * 但对"RGB开蓝色65调光开暖色98"等控制类指令的参数提取尚可。
 * 关键词匹配处理确定性意图，1.5b 只处理需要参数提取的控制类指令。
 * </p>
 *
 * <h3>执行流程</h3>
 * <pre>
 * 用户输入
 *   ├─ 关键词命中（查询/全开全关）→ Java 直接执行 → 1.5b 汇总 → 返回
 *   ├─ 关键词未命中 → 1.5b 分类 → Java 执行 → 1.5b 汇总 → 返回
 *   └─ 1.5b 识别为闲聊 → 返回 null → 回退云端 ReAct
 * </pre>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
public class LocalFastPathExecutor {

    /** 工具名：闲聊（非设备控制意图） */
    private static final String TOOL_CHAT = "chat";

    /** 工具名：批量控制设备 */
    private static final String TOOL_BATCH_CONTROL = "batch_control_device";

    /** 工具名：查询设备列表 */
    private static final String TOOL_QUERY_DEVICE_LIST = "query_device_list";

    /** 工具名：查询设备详情 */
    private static final String TOOL_QUERY_DEVICE_DETAIL = "query_device_detail";

    /** 全开模式 */
    private static final String MODE_ALL_ON = "all_on";

    /** 全关模式 */
    private static final String MODE_ALL_OFF = "all_off";

    /**
     * 1.5b 分类系统提示词（保守策略：仅确定指令走快速通道，不确定一律走云端）。
     * <p>
     * 核心原则：宁可漏判走云端，不可误判走快速通道。只有同时包含「明确动作词 + 设备名 + 参数」
     * 的指令才输出 batch_control_device，其余一律输出 chat。
     * </p>
     */
    private static final String CLASSIFY_SYSTEM_PROMPT = """
            你是智能家居指令分类器。严格输出JSON，不要输出任何其他内容。

            可选工具：
            - batch_control_device：仅限【确定指令】——同时包含明确动作词（开/关/调/设置）+ 设备名 + 具体参数值
            - query_device_list：查询设备列表
            - query_device_detail：查询设备状态
            - chat：以上都不符合时的兜底（闲聊、模糊意图、感受描述、多意图、不确定）

            【batch_control_device 的判定条件——必须同时满足】
            1. 包含明确动作词：开、关、调、设置、改为、变成
            2. 包含设备名：RGB、调光、灯、空调、窗帘等
            3. 包含具体参数值：亮度数字、颜色名、温度数字等
            三个条件缺一个 → 输出 chat

            【必须输出 chat 的场景】
            - 感受描述：太亮/太暗/太黑/太冷/太热/不舒服/又黑了/光线不好
            - 模糊指令：亮一点/暗一点/开亮些/调暗些（无具体数值）
            - 多意图：开灯并查电费/开空调顺便关窗
            - 闲聊：今天天气/你是谁/谢谢
            - 任何不确定的情况

            【严禁自行脑补参数】
            用户说"太暗了""太亮了""太冷了"等感受描述时，禁止自行编造亮度数值、温度数值或设备名。
            感受描述 = 用户在表达主观感受，不是在下达控制指令，一律输出 chat。

            【复杂口语指令一律输出 chat】
            以下特征说明用户在用自然口语表达，指令不连贯或含转折，1.5b 无法准确解析参数，必须输出 chat：
            - 含转折/纠正词：不对、不是、算了吧、改一下、说错了
            - 多个"然后"串联的长句（3个以上动作）
            - 口语化填充词：帮我把、那个、就是、嗯、呃
            - 句子不连贯、语义跳跃、中途改主意
            - 百分比/分数等非直接数值（百分之三十 → 需换算）
            batch_control_device 只处理简洁明确的指令（如"RGB开蓝色亮度65"），不处理口语长句。

            示例：
            用户：开灯
            输出：{"tool":"batch_control_device","isChat":false,"args":{"actions":[{"deviceName":"灯","userInput":"开灯"}]}}

            用户：RGB开蓝色亮度65调光开暖色98
            输出：{"tool":"batch_control_device","isChat":false,"args":{"actions":[{"deviceName":"RGB","userInput":"开蓝色亮度65"},{"deviceName":"调光","userInput":"开暖色亮度98"}]}}

            用户：全开
            输出：{"tool":"batch_control_device","isChat":false,"args":{"mode":"all_on"}}

            用户：全关
            输出：{"tool":"batch_control_device","isChat":false,"args":{"mode":"all_off"}}

            用户：我有哪些设备
            输出：{"tool":"query_device_list","isChat":false,"args":{}}

            用户：客厅灯开着吗
            输出：{"tool":"query_device_detail","isChat":false,"args":{"deviceIds":""}}

            用户：太暗了
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：太亮了
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：太冷了
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：太热了
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：RGB太亮了
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：调光太暗了
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：这样又太黑了
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：亮一点
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：暗一点
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：帮我把rgb灯打开然后颜色调成为蓝色然后不对绿色然后亮度调整为百分之三十
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：帮我开一下那个客厅的灯就是那个大灯
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：把空调温度调到嗯二十六度吧不对二十四度
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：先开灯然后开空调然后关窗帘然后调温度
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：rgb灯调成蓝色吧算了还是绿色吧
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：开灯并查电费
            输出：{"tool":"chat","isChat":true,"args":{}}

            用户：今天天气怎么样
            输出：{"tool":"chat","isChat":true,"args":{}}

            只输出JSON，不要输出任何其他内容。""";

    /** 结果汇总系统提示词 */
    private static final String SUMMARY_SYSTEM_PROMPT = """
            根据用户指令和操作结果，用简短的口语化中文回复用户。不超过50字。
            控制成功要确认，查询结果要总结。""";

    /** colorful 属性名（与 DeviceTool 保持一致） */
    private static final String ATTR_COLORFUL = "colorful";

    private final OllamaClient ollamaClient;
    private final HdlApiPort hdlApiPort;
    private final AgentScopeProperties properties;
    private final SpkAttributeResolver spkAttributeResolver;
    private final AttributeMutexRules attributeMutexRules;

    /**
     * 构造快速通道执行器。
     *
     * @param ollamaClient          Ollama 轻量 HTTP 客户端
     * @param hdlApiPort            HDL 业务 API 端口
     * @param properties            AgentScope 全局配置（读取 fastPath.keywords）
     * @param spkAttributeResolver  SPK 属性解析器（与 DeviceTool 共用，确定性解析不调 LLM）
     * @param attributeMutexRules   属性互斥规则（与 DeviceTool 共用）
     */
    public LocalFastPathExecutor(OllamaClient ollamaClient, HdlApiPort hdlApiPort,
                                 AgentScopeProperties properties,
                                 SpkAttributeResolver spkAttributeResolver,
                                 AttributeMutexRules attributeMutexRules) {
        this.ollamaClient = ollamaClient;
        this.hdlApiPort = hdlApiPort;
        this.properties = properties;
        this.spkAttributeResolver = spkAttributeResolver;
        this.attributeMutexRules = attributeMutexRules;
    }

    /**
     * 尝试走本地快速通道。
     *
     * @param userInput      用户原始输入
     * @param sessionContext 会话上下文
     * @return 快速通道结果；null 表示未命中，需回退云端
     */
    public FastPathResult tryExecute(String userInput, SessionContext sessionContext) {
        if (!properties.getFastPath().isEnabled()) {
            return null;
        }

        long start = System.currentTimeMillis();

        try {
            // ===== 第一层：关键词预匹配（0 LLM 调用，0 延迟） =====
            ToolSelection selection = matchByKeywords(userInput);
            if (selection != null) {
                log.info("[FastPath] 关键词命中: tool={}, mode={}", selection.toolName,
                        selection.args.get("mode"));
            } else {
                // ===== 第二层：1.5b 分类兜底（控制类指令参数提取） =====
                String classifyJson = ollamaClient.chatJson(CLASSIFY_SYSTEM_PROMPT, userInput);
                log.info("[FastPath] 1.5b分类输出: {}", classifyJson);

                selection = parseToolSelection(classifyJson);
                if (selection.isChat || TOOL_CHAT.equals(selection.toolName)) {
                    log.info("[FastPath] 识别为闲聊，回退云端");
                    return null;
                }
            }

            // ===== Java 确定性执行（0 次 LLM 调用） =====
            ToolResultVO toolResult = executeTool(selection, userInput, sessionContext);

            // 分类失败（toolName 为空/未知工具）→ 回退云端
            if (!toolResult.isSuccess() && toolResult.getErrorCode() != null
                    && toolResult.getErrorCode() == 400) {
                log.info("[FastPath] 分类或工具不匹配，回退云端: tool={}", selection.toolName);
                return null;
            }

            log.info("[FastPath] 工具执行完成: tool={}, success={}, routePath={}",
                    selection.toolName, toolResult.isSuccess(), toolResult.getRoutePath());

            // ===== 1.5b 结果汇总 =====
            String reply = generateSummary(userInput, toolResult);

            long elapsed = System.currentTimeMillis() - start;
            log.info("[FastPath] 快速通道完成: tool={}, elapsed={}ms", selection.toolName, elapsed);

            return new FastPathResult(reply, toolResult);

        } catch (Exception e) {
            log.error("[FastPath] 快速通道异常，回退云端: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 关键词预匹配（配置驱动） ====================

    /**
     * 基于配置的关键词预匹配。
     * <p>
     * 从 Nacos {@code fast-path.keywords} 读取关键词规则，
     * 用户输入包含任一关键词即命中对应工具。
     * 多工具同时命中时不拦截（回退 1.5b 做精确分类）。
     * </p>
     *
     * @param userInput 用户原始输入
     * @return 命中单一工具时返回 ToolSelection；未命中或多工具命中返回 null
     */
    private ToolSelection matchByKeywords(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        Map<String, List<String>> keywords = properties.getFastPath().getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        String input = userInput.trim();
        String matchedTool = null;

        for (Map.Entry<String, List<String>> entry : keywords.entrySet()) {
            String toolKey = entry.getKey();
            for (String keyword : entry.getValue()) {
                if (keyword != null && input.contains(keyword)) {
                    if (matchedTool != null && !matchedTool.equals(extractToolName(toolKey))) {
                        // 多工具命中，不拦截，走 1.5b
                        log.info("[FastPath] 关键词命中多工具，走1.5b: tools={},{}",
                                matchedTool, extractToolName(toolKey));
                        return null;
                    }
                    matchedTool = extractToolName(toolKey);
                    // 构建 ToolSelection（含 mode 参数）
                    ToolSelection selection = new ToolSelection();
                    selection.toolName = matchedTool;
                    selection.isChat = false;
                    selection.args = new LinkedHashMap<>();
                    // toolKey 格式：toolName 或 toolName:mode（如 batch_control_device:all_on）
                    String mode = extractMode(toolKey);
                    if (mode != null) {
                        selection.args.put("mode", mode);
                    }
                    return selection;
                }
            }
        }

        return null;
    }

    /**
     * 从 toolKey 提取工具名。
     * <p>toolKey 格式：{@code toolName} 或 {@code toolName:mode}</p>
     *
     * @param toolKey 配置中的 key
     * @return 工具名
     */
    private String extractToolName(String toolKey) {
        int colon = toolKey.indexOf(':');
        return colon > 0 ? toolKey.substring(0, colon) : toolKey;
    }

    /**
     * 从 toolKey 提取 mode 参数。
     * <p>toolKey 格式：{@code toolName:mode}，如 {@code batch_control_device:all_on}</p>
     *
     * @param toolKey 配置中的 key
     * @return mode 值；无 mode 返回 null
     */
    private String extractMode(String toolKey) {
        int colon = toolKey.indexOf(':');
        return colon > 0 ? toolKey.substring(colon + 1) : null;
    }

    // ==================== 工具执行（策略模式） ====================

    /**
     * 根据分类结果执行对应工具。
     *
     * @param selection      分类结果
     * @param userInput      用户原始输入
     * @param sessionContext 会话上下文
     * @return 工具执行结果
     */
    private ToolResultVO executeTool(ToolSelection selection, String userInput,
                                     SessionContext sessionContext) {
        if (selection.toolName == null || selection.toolName.isBlank()) {
            log.warn("[FastPath] toolName 为空，回退云端");
            return ToolResultVO.failure(400, "分类失败：toolName 为空");
        }
        return switch (selection.toolName) {
            case TOOL_BATCH_CONTROL -> executeBatchControl(selection.args, userInput, sessionContext);
            case TOOL_QUERY_DEVICE_LIST -> hdlApiPort.queryDeviceList("", sessionContext);
            case TOOL_QUERY_DEVICE_DETAIL -> hdlApiPort.queryDeviceDetail(
                    getArg(selection.args, "deviceIds", ""), sessionContext);
            default -> {
                log.warn("[FastPath] 未知工具，回退云端: tool={}", selection.toolName);
                yield ToolResultVO.failure(400, "未知工具: " + selection.toolName);
            }
        };
    }

    /**
     * 批量控制设备。
     * <p>
     * 复用 DeviceTool 的确定性属性解析逻辑：
     * 查设备列表 → 匹配设备名 → SPK 属性解析 → colorful 互斥 → HDL API 调用。
     * 确保返回的 ToolResultVO 与云端 ReAct 路径完全一致（routePath/data/broadcastText）。
     * </p>
     *
     * @param args           分类参数（含 mode 或 actions）
     * @param userInput      用户原始输入
     * @param sessionContext 会话上下文
     * @return 工具执行结果（与 DeviceTool.batchControlDevice 返回值格式一致）
     */
    private ToolResultVO executeBatchControl(Map<String, Object> args, String userInput,
                                             SessionContext sessionContext) {
        // 全开/全关模式：直接调 HdlApiPort（与 DeviceTool.batchControlAllDevices 相同入口）
        String mode = getArg(args, "mode", "");
        if (MODE_ALL_ON.equalsIgnoreCase(mode) || MODE_ALL_OFF.equalsIgnoreCase(mode)) {
            log.info("[FastPath] 全开/全关模式: mode={}", mode);
            return hdlApiPort.batchControlDevice("[]", sessionContext);
        }

        // 普通控制：查设备列表 → 匹配设备名 → SPK 属性解析 → 构建 resolvedActions
        List<Map<String, Object>> deviceList = hdlApiPort.queryDeviceListRaw(sessionContext);
        if (deviceList == null || deviceList.isEmpty()) {
            return ToolResultVO.failure(500, "设备列表为空，无法控制");
        }

        Object actionsObj = args.get("actions");
        if (actionsObj == null) {
            return ToolResultVO.failure(400, "batch_control_device 缺少 actions 参数");
        }

        JSONArray llmActions = JSON.parseArray(JSON.toJSONString(actionsObj));
        List<Map<String, Object>> resolvedActions = new ArrayList<>();
        String gatewayId = null;
        StringBuilder deviceIdsForDetail = new StringBuilder();

        for (int i = 0; i < llmActions.size(); i++) {
            JSONObject llmAction = llmActions.getJSONObject(i);
            String deviceName = llmAction.getString("deviceName");
            String actionUserInput = llmAction.getString("userInput");

            // 设备名匹配（双向子串匹配）
            Map<String, Object> matchedDevice = matchDevice(deviceName, deviceList);
            if (matchedDevice == null) {
                log.warn("[FastPath] 未匹配到设备: deviceName={}", deviceName);
                continue;
            }

            String deviceId = String.valueOf(matchedDevice.get("deviceId"));
            String spk = String.valueOf(matchedDevice.get("spk"));
            String resolvedUserInput = actionUserInput != null ? actionUserInput : userInput;

            // SPK 确定性属性解析（与 DeviceTool 相同，不调 LLM）
            Map<String, Object> attrMap = spkAttributeResolver.resolveAttributes(spk, resolvedUserInput);
            if (attrMap == null || attrMap.isEmpty()) {
                log.warn("[FastPath] 属性解析为空，跳过: deviceId={}, spk={}", deviceId, spk);
                continue;
            }

            // colorful 互斥逻辑（与 DeviceTool.applyColorfulMutex 相同）
            applyColorfulMutex(attrMap, resolvedUserInput);

            // 构建 HDL 期望的 attributes 格式：[{key, value}]
            List<Map<String, Object>> attributes = new ArrayList<>(attrMap.size());
            for (Map.Entry<String, Object> entry : attrMap.entrySet()) {
                Map<String, Object> attr = new LinkedHashMap<>(2);
                attr.put("key", entry.getKey());
                attr.put("value", entry.getValue());
                attributes.add(attr);
            }

            // 构建解析后的 action（与 DeviceTool 格式一致）
            Map<String, Object> resolvedAction = new LinkedHashMap<>(4);
            resolvedAction.put("deviceId", deviceId);
            resolvedAction.put("spk", spk);
            resolvedAction.put("attributes", attributes);
            resolvedAction.put("deviceName", matchedDevice.get("name"));
            resolvedActions.add(resolvedAction);

            // 提取 gatewayId（同一网关下的设备才能批量控制）
            if (gatewayId == null) {
                gatewayId = String.valueOf(matchedDevice.get("gatewayId"));
            }

            // 拼接 deviceIds 供控制后查询详情
            if (deviceIdsForDetail.length() > 0) {
                deviceIdsForDetail.append(",");
            }
            deviceIdsForDetail.append(deviceId);

            log.info("[FastPath] 设备属性解析: deviceId={}, spk={}, attrs={}", deviceId, spk, attrMap);
        }

        if (resolvedActions.isEmpty()) {
            return ToolResultVO.failure(400, "未匹配到任何设备，请确认设备名称");
        }

        // 调用 List 重载（与 DeviceTool 相同入口），返回 ToolResultVO 含 routePath + data + broadcastText
        log.info("[FastPath] resolvedActions构建完成: count={}", resolvedActions.size());
        return hdlApiPort.batchControlDevice(resolvedActions, gatewayId,
                deviceIdsForDetail.toString(), sessionContext);
    }

    /**
     * RGB 与 colorful 互斥逻辑（与 DeviceTool.applyColorfulMutex 完全一致）。
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
            attrMap.remove(ATTR_COLORFUL);
        } else if (hasColorfulKeyword) {
            List<String> mutexAttrs = mutexRules.get(ATTR_COLORFUL);
            if (mutexAttrs != null) {
                for (String mutexAttr : mutexAttrs) {
                    attrMap.remove(mutexAttr);
                }
                if (!attrMap.containsKey(ATTR_COLORFUL)) {
                    attrMap.put(ATTR_COLORFUL, "on");
                }
            }
        }
    }

    /**
     * 在设备列表中匹配设备（双向子串匹配）。
     *
     * @param deviceName 设备关键词
     * @param deviceList 设备列表
     * @return 匹配到的设备 Map；未匹配返回 null
     */
    private Map<String, Object> matchDevice(String deviceName, List<Map<String, Object>> deviceList) {
        if (deviceName == null || deviceName.isBlank()) {
            return null;
        }
        String keyword = deviceName.trim();
        for (Map<String, Object> device : deviceList) {
            String name = String.valueOf(device.getOrDefault("name", ""));
            if (name.contains(keyword) || keyword.contains(name)) {
                return device;
            }
        }
        return null;
    }

    // ==================== 结果汇总 ====================

    /**
     * 1.5b 基于工具结果生成自然语言回复。
     *
     * @param userInput  用户原始输入
     * @param toolResult 工具执行结果
     * @return 自然语言回复
     */
    private String generateSummary(String userInput, ToolResultVO toolResult) {
        try {
            String toolData = toolResult.getData() != null
                    ? JSON.toJSONString(toolResult.getData())
                    : toolResult.getMessage();
            String userPrompt = "用户指令：" + userInput + "\n操作结果：" + toolData;

            String summary = ollamaClient.chat(SUMMARY_SYSTEM_PROMPT, userPrompt);
            return (summary != null && !summary.isBlank())
                    ? summary
                    : toolResult.getBroadcastText();
        } catch (Exception e) {
            log.warn("[FastPath] 结果汇总失败，使用 broadcastText 兜底: {}", e.getMessage());
            return toolResult.getBroadcastText() != null
                    ? toolResult.getBroadcastText()
                    : toolResult.getMessage();
        }
    }

    // ==================== JSON 解析（兼容 1.5b 多种输出格式） ====================

    /**
     * 解析 1.5b 输出的工具选择 JSON。
     * <p>
     * 兼容两种格式：
     * <ul>
     *   <li>标准格式：{@code {"tool":"xxx","isChat":false,"args":{...}}}</li>
     *   <li>1.5b 嵌套格式：{@code {"batch_control_device":{"actions":[...]}, "query_device_list":{}}}
     *       —— 1.5b 倾向于把工具名当 key，需遍历找到非空 value 的已知工具名</li>
     * </ul>
     * </p>
     *
     * @param raw 1.5b 原始输出
     * @return 解析结果
     */
    private ToolSelection parseToolSelection(String raw) {
        ToolSelection selection = new ToolSelection();
        if (raw == null || raw.isBlank()) {
            selection.isChat = true;
            return selection;
        }

        try {
            String json = extractJson(raw);
            JSONObject node = JSON.parseObject(json);

            // 标准格式：{"tool":"xxx","args":{...}}
            String tool = node.getString("tool");
            if (tool != null && !tool.isBlank()) {
                selection.toolName = tool;
                selection.isChat = node.getBooleanValue("isChat", false);
                selection.args = parseArgs(node.getJSONObject("args"));
                return selection;
            }

            // 兼容 1.5b 嵌套格式：{"batch_control_device":{...}, "query_device_list":{}}
            // 遍历已知工具名，找到 value 非空的那个
            for (String knownTool : List.of(TOOL_BATCH_CONTROL, TOOL_QUERY_DEVICE_LIST, TOOL_QUERY_DEVICE_DETAIL)) {
                JSONObject toolArgs = node.getJSONObject(knownTool);
                if (toolArgs != null && !toolArgs.isEmpty()) {
                    selection.toolName = knownTool;
                    selection.isChat = false;
                    selection.args = parseArgs(toolArgs);
                    log.info("[FastPath] 兼容1.5b嵌套格式: tool={}", knownTool);
                    return selection;
                }
            }

            // 未识别到已知工具，视为闲聊
            log.warn("[FastPath] 未识别到工具，降级为闲聊: raw={}", raw);
            selection.isChat = true;
        } catch (Exception e) {
            log.warn("[FastPath] JSON解析失败，降级为闲聊: raw={}, err={}", raw, e.getMessage());
            selection.isChat = true;
        }
        return selection;
    }

    /**
     * 从可能包含 markdown 代码块或额外文本的字符串中提取 JSON。
     *
     * @param raw 原始字符串
     * @return 提取出的 JSON 字符串
     */
    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                return trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return trimmed.substring(first, last + 1);
        }
        return trimmed;
    }

    /**
     * 解析 args JSON 节点为 Map。
     *
     * @param argsNode args JSON 节点
     * @return 参数 Map
     */
    private Map<String, Object> parseArgs(JSONObject argsNode) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (argsNode == null || argsNode.isEmpty()) {
            return args;
        }
        for (String key : argsNode.keySet()) {
            args.put(key, argsNode.get(key));
        }
        return args;
    }

    /**
     * 从参数 Map 中安全获取字符串值。
     *
     * @param args         参数 Map
     * @param key          键名
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private String getArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    // ==================== 内部数据结构 ====================

    /**
     * 工具选择解析结果。
     */
    private static class ToolSelection {
        /** 工具名 */
        String toolName = "";
        /** 是否闲聊（true 时回退云端） */
        boolean isChat = false;
        /** 工具参数 */
        Map<String, Object> args = new LinkedHashMap<>();
    }

    /**
     * 快速通道执行结果。
     *
     * @param reply      1.5b 生成的自然语言回复
     * @param toolResult 工具执行结果
     */
    public record FastPathResult(String reply, ToolResultVO toolResult) {}
}
