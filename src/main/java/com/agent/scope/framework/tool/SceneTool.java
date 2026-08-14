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
 * 场景工具（特性 3：ReAct 智能体与工具调用）。
 *
 * <p>封装 HDL 场景查询、执行与创建能力。用户说"回家模式"或"启动离家场景"时，
 * LLM 通过 ReAct 推理调用 execute_scene 工具完成场景执行；
 * 用户说"创建一个回家场景 RGB 开绿色亮度 98"时，LLM 调用 create_scene 工具完成场景创建。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneTool extends AbstractTool {

    /**
     * HDL 业务 API 端口
     */
    private final HdlApiPort hdlApiPort;

    /**
     * SPK 物模型属性解析器
     */
    private final SpkAttributeResolver spkAttributeResolver;

    /**
     * 属性互斥规则端口（Nacos 热重载）
     */
    private final AttributeMutexRules attributeMutexRules;

    /**
     * colorful 属性名
     */
    private static final String ATTR_COLORFUL = "colorful";

    /**
     * 默认延迟执行秒数
     */
    private static final int DEFAULT_DELAY_SECONDS = 0;

    /**
     * spk 长度阈值：超过此长度疑似 sid
     */
    private static final int SPK_MAX_LENGTH = 20;

    /**
     * spk 格式分隔符
     */
    private static final String SPK_SEPARATOR = ".";

    /**
     * 查询场景列表。
     *
     * @param homeId         房屋 ID（可选，留空时自动从会话上下文获取）
     * @param roomId         房间 ID（可选）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 为场景列表 JSON）
     */
    @Tool(name = "query_scene_list",
            description = """
                    查询 HDL 场景列表。查询当前房屋下所有场景（homeId 可不传，默认查当前房屋），可选按房间 ID 过滤。
                    使用场景：用户询问'有哪些场景'、'回家模式'、'启动离家场景'前先查询场景列表获取 sceneId。
                    参数来源要求：homeId 从 query_home_list 获取或留空使用当前房屋；roomId 从 query_device_list 获取或留空。
                    禁止事项：禁止编造场景 ID，执行场景前必须先调用本工具获取真实 sceneId。
                    """,
            readOnly = true)
    public ToolResultVO querySceneList(
            @ToolParam(name = "homeId", required = false,
                    description = "房屋ID，可选。留空时自动从会话上下文获取当前房屋ID") String homeId,
            @ToolParam(name = "roomId", required = false,
                    description = "房间ID，可选。留空时查询所有房间的场景") String roomId,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        // homeId 为空时从会话上下文自动填充当前房屋 ID
        String resolvedHomeId = (homeId != null && !homeId.isBlank())
                ? homeId
                : (sessionContext.getHouseId() != null ? sessionContext.getHouseId() : "");
        log.info("[SceneTool] 查询场景列表: homeId={}, resolvedHomeId={}, roomId={}, userId={}",
                homeId, resolvedHomeId, roomId, sessionContext.getUserId());
        return hdlApiPort.querySceneList(resolvedHomeId, roomId, sessionContext);
    }

    /**
     * 执行场景。
     *
     * @param sceneId        场景 ID 或场景名称
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO
     */
    @Tool(name = "execute_scene",
            description = """
                    执行 HDL 场景。传入场景 ID，触发该场景包含的所有设备控制动作（如'回家模式'会自动开关多个设备）。
                    使用场景：用户说'启动回家场景'、'执行晚安模式'、'打开离家场景'时调用。
                    参数来源要求：sceneId 必须来自 query_scene_list 的返回结果。
                    禁止事项：禁止编造场景 ID，禁止使用示例值。
                    """,
            concurrencySafe = false)
    public ToolResultVO executeScene(
            @ToolParam(name = "sceneId", required = true,
                    description = "场景ID或场景名称。示例：回家场景。从query_scene_list返回结果获取") String sceneId,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[SceneTool] 执行场景: sceneId={}, userId={}",
                sceneId, sessionContext.getUserId());
        return hdlApiPort.executeScene(sceneId, sessionContext);
    }

    /**
     * 创建场景。
     *
     * <p>LLM 先调用 query_device_list 获取设备列表，然后为每个受控设备构造 function：
     * {sid, spk, userInput}，工具内部用 spkAttributeResolver 解析为 status。</p>
     *
     * @param sceneName      场景名称
     * @param gatewayId      网关 ID
     * @param functionsJson  设备动作 JSON 数组字符串
     * @param collect        是否收藏场景（可选）
     * @param delaySeconds   场景级延迟执行秒数（可选）
     * @param executePush    是否推送执行结果通知（可选）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO
     */
//    @Tool(name = "create_scene",
//            description = """
//                    创建 HDL 场景。需先调用 query_device_list 获取设备 sid/spk/gatewayId，然后传入场景名和设备动作列表。
//                    设备动作通过 userInput 字段描述（如'RGB开绿色亮度98'），工具内部自动解析为标准控制属性，无需 LLM 输出属性 key。
//                    使用场景：用户说'创建一个回家场景 RGB 开绿色亮度 98'时调用。
//                    参数来源要求：gatewayId、sid、spk 必须来自 query_device_list 返回结果。
//                    禁止事项：禁止编造网关 ID、设备 sid 或种类码；禁止使用示例值。
//                    """,
//            concurrencySafe = false)
    public ToolResultVO createScene(
            @ToolParam(name = "sceneName", required = true,
                    description = "场景名称。示例：回家场景、晚安场景。从用户输入提取，通常是创建/新建之后、场景之前的文字") String sceneName,
            @ToolParam(name = "gatewayId", required = true,
                    description = "网关ID。必须从query_device_list返回结果获取，禁止编造或使用任何示例值") String gatewayId,
            @ToolParam(name = "functionsJson", required = true,
                    description = "设备动作JSON数组字符串。格式：[{\"sid\":\"<query_device_list返回的设备SID>\",\"spk\":\"<query_device_list返回的spk>\",\"userInput\":\"用户对该设备的控制描述\",\"delaySeconds\":0}]。sid和spk从query_device_list响应获取，禁止编造或使用任何示例值") String functionsJson,
            @ToolParam(name = "collect", required = false,
                    description = "是否收藏场景。默认false") Boolean collect,
            @ToolParam(name = "delaySeconds", required = false,
                    description = "场景级延迟执行秒数。默认0") Integer delaySeconds,
            @ToolParam(name = "executePush", required = false,
                    description = "是否推送执行结果通知。默认false") Boolean executePush,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[SceneTool] 创建场景: sceneName={}, gatewayId={}, userId={}",
                sceneName, gatewayId, sessionContext.getUserId());

        // 1. 解析 functionsJson
        JSONArray rawFunctions = JSON.parseArray(functionsJson);
        if (rawFunctions == null || rawFunctions.isEmpty()) {
            return ToolResultVO.failure(400,
                    "functionsJson 解析失败或为空，请确认 JSON 格式正确且至少包含一个设备动作");
        }

        // 2. 对每个 function 做 spk + userInput → status 解析
        List<Map<String, Object>> functions = new ArrayList<>(rawFunctions.size());
        for (int i = 0; i < rawFunctions.size(); i++) {
            JSONObject rawFn = rawFunctions.getJSONObject(i);
            String sid = rawFn.getString("sid");
            String spk = rawFn.getString("spk");
            String userInput = rawFn.getString("userInput");
            int fnDelaySeconds = rawFn.getIntValue("delaySeconds", DEFAULT_DELAY_SECONDS);

            if (sid == null || sid.isBlank()) {
                return ToolResultVO.failure(400,
                        "第 " + (i + 1) + " 个 function 缺少 sid 字段，请从 query_device_list 响应中获取");
            }
            if (spk == null || spk.isBlank()) {
                return ToolResultVO.failure(400,
                        "第 " + (i + 1) + " 个 function 缺少 spk 字段，请从 query_device_list 响应中获取");
            }
            // spk 疑似 sid 时尝试修正（spk 过长或不含点号）
            if (spk.length() > SPK_MAX_LENGTH || !spk.contains(SPK_SEPARATOR)) {
                log.warn("[SceneTool] spk 疑似 sid，尝试通过设备列表反查: sid={}, wrongSpk={}", sid, spk);
            }
            if (userInput == null || userInput.isBlank()) {
                return ToolResultVO.failure(400,
                        "第 " + (i + 1) + " 个 function 缺少 userInput 字段，需提供用户对该设备的控制描述");
            }

            // 确定性属性解析：spk + userInput → Map<String, Object>
            Map<String, Object> attrMap = spkAttributeResolver.resolveAttributes(spk, userInput);
            if (attrMap == null || attrMap.isEmpty()) {
                return ToolResultVO.failure(400,
                        "第 " + (i + 1) + " 个 function 属性解析为空，spk=" + spk
                                + "，userInput=" + userInput + "，请确认 spk 和描述正确");
            }

            // RGB 与 colorful 互斥逻辑
            applyColorfulMutex(attrMap, userInput);

            // 构建 status 格式：[{key, value}]
            List<Map<String, Object>> status = new ArrayList<>(attrMap.size());
            for (Map.Entry<String, Object> entry : attrMap.entrySet()) {
                Map<String, Object> attr = new LinkedHashMap<>(2);
                attr.put("key", entry.getKey());
                attr.put("value", entry.getValue());
                status.add(attr);
            }

            // 构建 function
            Map<String, Object> function = new LinkedHashMap<>(4);
            function.put("sid", sid);
            function.put("spk", spk);
            function.put("status", status);
            function.put("delaySeconds", fnDelaySeconds);
            functions.add(function);

            log.info("[SceneTool] 场景设备属性解析: sid={}, spk={}, userInput={}, attrs={}",
                    sid, spk, userInput, attrMap);
        }

        // 3. 构建场景创建请求体
        Map<String, Object> body = new LinkedHashMap<>(6);
        body.put("sceneName", sceneName);
        body.put("gatewayId", gatewayId);
        body.put("functions", functions);
        body.put("collect", collect != null ? collect : false);
        body.put("delaySeconds", delaySeconds != null ? delaySeconds : DEFAULT_DELAY_SECONDS);
        body.put("executePush", executePush != null ? executePush : false);

        // 4. 调用 HDL API 创建场景
        return hdlApiPort.createScene(body, sessionContext);
    }

    /**
     * RGB 与 colorful 互斥逻辑（与 DeviceTool 保持一致）。
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
            log.info("[SceneTool] 移除 colorful 属性（用户未提到炫彩）");
        } else if (hasColorfulKeyword) {
            List<String> mutexAttrs = mutexRules.get(ATTR_COLORFUL);
            if (mutexAttrs != null) {
                for (String mutexAttr : mutexAttrs) {
                    attrMap.remove(mutexAttr);
                }
                if (!attrMap.containsKey(ATTR_COLORFUL)) {
                    attrMap.put(ATTR_COLORFUL, "on");
                }
                log.info("[SceneTool] 保留 colorful，移除互斥属性: {}", mutexAttrs);
            }
        }
    }
}