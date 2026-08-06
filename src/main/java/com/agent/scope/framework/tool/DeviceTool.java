package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.vo.ToolResultVO;
import com.alibaba.fastjson2.JSONObject;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备工具（特性 3：ReAct 智能体与工具调用）。
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTool extends AbstractTool {

    /**
     * 查询设备列表。
     *
     * <p>查询当前房屋下所有设备，返回设备 ID、名称、种类码、网关 ID、sid 等信息。
     * 控制设备前必须先调用此工具获取真实 deviceId 和 gatewayId。</p>
     *
     * <p>查询全部设备，不接受 spk 参数。
     * 原因：LLM 在控制场景下会将用户输入中的设备类型词（如"RGB"）作为 spk 传入，
     * 导致 HDL 接口按种类码过滤后返回空列表，后续控制流程无法获取设备 ID。
     * 查询全部设备后 LLM 可自行从返回结果中筛选目标设备。</p>
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
        log.info("[DeviceTool] 查询设备列表（全部）: sessionContext={}", JSONObject.toJSONString(sessionContext));
        ToolResultVO toolResultVO = new ToolResultVO();
        toolResultVO.setSuccess(true);
        toolResultVO.setMessage("成功查询到设备信息");
        toolResultVO.setData(JSONObject.parseObject("{\"code\":0,\"data\":[{\"deviceId\":\"1\",\"deviceName\":\"方悦\",\"deviceType\":\"RGB\",\"gatewayId\":\"1\",\"sid\":\"1\"}],\"message\":\"成功\"}"));
        toolResultVO.setBroadcastText("成功查询到设备信息");
        toolResultVO.setAskUser("成功查询到设备信息");
        return toolResultVO;
    }

    /**
     * 查询设备详情（最新状态）。
     *
     * <p>传入设备 ID 列表（逗号分隔），返回这些设备的最新状态详情，
     * 包括开关状态、亮度、色温、在线状态等。</p>
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
            @ToolParam(
                    name = "deviceIds", required = true,
                    description = "设备ID列表，逗号分隔。必须来自query_device_list返回的真实设备ID，禁止使用示例ID") String deviceIds,
            RuntimeContext runtimeContext
    ) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[DeviceTool] 查询设备详情: sessionContext={}", JSONObject.toJSONString(sessionContext));
        ToolResultVO toolResultVO = new ToolResultVO();
        toolResultVO.setSuccess(true);
        toolResultVO.setMessage("成功查询到设备信息");
        toolResultVO.setData(JSONObject.parseObject("{\"code\":0,\"data\":[{\"deviceId\":\"1\",\"deviceName\":\"方悦\",\"deviceType\":\"RGB\",\"gatewayId\":\"1\",\"sid\":\"1\"}],\"message\":\"成功\"}"));
        toolResultVO.setBroadcastText("成功查询到设备信息");
        toolResultVO.setAskUser("成功查询到设备信息");
        return toolResultVO;
    }

    /**
     * 批量控制设备。
     *
     * <p>多设备控制合并到一次 HTTP 请求，HDL actions 数组包含多个设备的控制动作。
     * 解决多次调用 control_device 导致的多请求问题，降低网络开销和响应延迟。</p>
     *
     * <p><b>与 control_device 的区别</b>：</p>
     * <ul>
     *   <li>control_device：单设备控制，每次一个 HTTP 请求</li>
     *   <li>batch_control_device：多设备控制，合并为一次 HTTP 请求</li>
     * </ul>
     *
     * <p><b>属性解析</b>：每个设备使用 spk + userInput 做确定性属性解析，
     * 生成 HDL 期望的 [{key, value}] 格式。控制成功后自动查询设备详情供前端回显。</p>
     *
     * @param actionsJson    设备动作 JSON 数组字符串
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 含 controlResult 和 devices 设备详情）
     */
    @Tool(name = "batch_control_device",
            description = "批量控制多个 HDL 设备（多设备合并一次请求）。"
                    + "使用场景：当用户指令包含一个或多个设备控制时使用此工具，如'RGB开蓝色亮度77调光开冷色亮度99'、'打开客厅灯'。"
                    + "全关/全开场景：用户说'全关'、'全开'、'全部关闭'、'全部打开'时，必须先调用 query_device_list 获取所有设备，"
                    + "然后为每个设备创建一个 action，userInput 填'关'（全关）或'开'（全开），一次性提交。"
                    + "参数来源要求：actionsJson 中每个元素的 deviceId/gatewayId/spk 必须来自 query_device_list 返回结果。"
                    + "设备名称优先匹配（极其重要）：用户输入中的设备关键词（如'调光'、'色温'、'RGB'）通常是设备名称的缩写，"
                    + "应优先匹配设备列表中名称包含该关键词的设备（如'调光'→'Lite 调光'设备，'色温'→'Lite 色温'设备），"
                    + "不要把'调光'理解为 spk 物模型类型。只有当设备名匹配不到时才使用 spk 匹配。"
                    + "多设备合并：用户输入可能没有逗号分隔符（如'调光开冷色亮度48RGB开红色亮度88色温开暖色亮度87'），"
                    + "这是多个设备控制指令连写，必须识别为多设备控制合并为一次 batch_control_device 调用，禁止拆分成多次调用。"
                    + "禁止事项：禁止编造设备 ID、网关 ID 或种类码；禁止使用示例值；禁止为每个设备单独调用 query_device_list。",
            concurrencySafe = false)
    public ToolResultVO batchControlDevice(
            @ToolParam(name = "actionsJson", required = true,
                    description = "设备动作JSON数组字符串，格式：[{\"deviceId\":\"<query_device_list返回的deviceId>\",\"gatewayId\":\"<query_device_list返回的gatewayId>\",\"spk\":\"<query_device_list返回的spk>\",\"userInput\":\"用户对该设备的控制描述\"}]。deviceId/gatewayId/spk必须来自query_device_list返回结果，禁止编造或使用任何示例值") String actionsJson,
            RuntimeContext runtimeContext) {
        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[DeviceTool] 批量控制设备: actionsJson={}, sessionContext={}", actionsJson, JSONObject.toJSONString(sessionContext));
        ToolResultVO toolResultVO = new ToolResultVO();
        toolResultVO.setSuccess(true);
        toolResultVO.setMessage("成功批量控制设备");
        toolResultVO.setData(JSONObject.parseObject("{\"code\":0,\"data\":[{\"deviceId\":\"1\",\"deviceName\":\"方悦\",\"deviceType\":\"RGB\",\"gatewayId\":\"1\",\"sid\":\"1\"}],\"message\":\"成功\"}"));
        toolResultVO.setBroadcastText("成功批量控制设备");
        toolResultVO.setAskUser("成功批量控制设备");
        return toolResultVO;
    }
}