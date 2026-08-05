package com.agent.scope.framework.tool;

import com.agent.scope.framework.client.HdlApiClient;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.vo.ToolResultVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.agentscope.core.agent.RuntimeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 设备控制工具。
 * <p>
 * 提供设备列表查询、设备状态详情查询、批量设备控制能力。
 * 通过 {@link HdlApiClient} 调用云端 API，使用 SessionContext 中的 accessToken 与 houseId。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTool extends AbstractTool {

    /**
     * 云端 API 客户端
     */
    private final HdlApiClient hdlApiClient;


    /**
     * 查询当前房屋所有设备。
     * <p>
     * 从会话上下文获取 accessToken 与 houseId，调用云端设备列表接口，
     * 返回设备列表数据。routePath=/device/list，前端据此渲染设备列表页。
     * </p>
     *
     * @param sessionContext 会话上下文（框架自动注入）
     * @return 工具执行结果，data 为设备列表
     */
    @Tool(description = "查询当前房屋所有设备列表，返回设备ID、名称、在线状态等信息")
    public ToolResultVO query_device_list(RuntimeContext runtimeContext) {
        SessionContext sessionContext = runtimeContext.get(CTX_KEY_SESSION_CONTEXT);
        // 校验会话上下文
        if (sessionContext == null || sessionContext.getAccessToken() == null) {
            ToolResultVO fail = ToolResultVO.fail("用户未登录，请先完成账号登录");
            recordResult("query_device_list", fail);
            return fail;
        }
        String houseId = sessionContext.getHouseId();
        if (houseId == null || houseId.isBlank()) {
            ToolResultVO fail = ToolResultVO.fail("当前未选择房屋，无法查询设备列表");
            recordResult("query_device_list", fail);
            return fail;
        }

        // 调用云端 API 获取设备列表
        List<Map<String, Object>> deviceList = hdlApiClient.getDeviceList(
                sessionContext.getAccessToken(), houseId);

        ToolResultVO result = ToolResultVO.success(deviceList,
                "查询到 " + (deviceList != null ? deviceList.size() : 0) + " 台设备");
        result.setRoutePath("/device/list");
        result.setBroadcastText("已为您查询到当前房屋的设备列表");
        recordResult("query_device_list", result);
        return result;
    }

    /**
     * 查询设备状态详情。
     * <p>
     * 支持传入多个设备 ID（逗号分隔），批量查询设备详细状态信息。
     * </p>
     *
     * @param sessionContext 会话上下文（框架自动注入）
     * @param deviceIds      设备 ID（多个以逗号分隔）
     * @return 工具执行结果，data 为设备详情列表
     */
    @Tool(description = "查询设备状态详情，支持多个设备ID（逗号分隔），返回设备详细属性")
    public ToolResultVO query_device_detail(RuntimeContext runtimeContext,
                                            @ToolParam(description = "设备ID，多个以逗号分隔") String deviceIds) {
        SessionContext sessionContext = runtimeContext.get(CTX_KEY_SESSION_CONTEXT);
        if (sessionContext == null || sessionContext.getAccessToken() == null) {
            ToolResultVO fail = ToolResultVO.fail("用户未登录，请先完成账号登录");
            recordResult("query_device_detail", fail);
            return fail;
        }
        if (deviceIds == null || deviceIds.isBlank()) {
            ToolResultVO fail = ToolResultVO.fail("设备ID不能为空");
            recordResult("query_device_detail", fail);
            return fail;
        }

        // 解析设备 ID 列表，逐个查询详情
        String[] idArray = deviceIds.split(",");
        List<Map<String, Object>> detailList = new java.util.ArrayList<>();
        for (String deviceId : idArray) {
            String trimmedId = deviceId.trim();
            if (!trimmedId.isEmpty()) {
                Map<String, Object> detail = hdlApiClient.getDeviceDetail(
                        sessionContext.getAccessToken(), trimmedId);
                if (detail != null) {
                    detailList.add(detail);
                }
            }
        }

        ToolResultVO result = ToolResultVO.success(detailList,
                "查询到 " + detailList.size() + " 台设备详情");
        result.setRoutePath("/device/detail");
        recordResult("query_device_detail", result);
        return result;
    }

    /**
     * 批量控制设备。
     * <p>
     * 接收 JSON 格式的控制动作列表，解析后逐个发送控制指令。
     * actionsJson 格式：[{"deviceId":"xxx","attributes":[{"name":"switch","value":"on"}]}]
     * </p>
     *
     * @param sessionContext 会话上下文（框架自动注入）
     * @param actionsJson    控制动作 JSON 字符串
     * @return 工具执行结果，data 为各设备控制结果
     */
    @Tool(description = "批量控制设备。根据用户自然语言指令构建控制动作JSON。"
            + "请先调用query_device_list获取设备列表（含deviceId、spk、gatewayId和attributes schema），"
            + "再根据用户指令匹配设备并构造actionsJson。"
            + "actionsJson格式：[{\"deviceId\":\"设备ID\",\"spk\":\"设备spk\",\"gatewayId\":\"网关ID\",\"attributes\":[{\"key\":\"属性名\",\"value\":\"属性值\"}]}]。"
            + "常用属性：on_off(开关,on/off)、brightness(亮度,0-100整数)、rgb(RGB颜色,\"R,G,B\"如\"0,0,255\")、cct(色温,cold/warm)。"
            + "多设备控制合并到一次调用，不拆分。")
    public ToolResultVO batch_control_device(RuntimeContext runtimeContext,
                                             @ToolParam(description = "控制动作JSON数组") String actionsJson) {
        SessionContext sessionContext = runtimeContext.get(CTX_KEY_SESSION_CONTEXT);
        if (sessionContext == null || sessionContext.getAccessToken() == null) {
            ToolResultVO fail = ToolResultVO.fail("用户未登录，请先完成账号登录");
            recordResult("batch_control_device", fail);
            return fail;
        }
        if (actionsJson == null || actionsJson.isBlank()) {
            ToolResultVO fail = ToolResultVO.fail("控制动作不能为空");
            recordResult("batch_control_device", fail);
            return fail;
        }

        // 解析控制动作 JSON
        List<Map<String, Object>> actions;
        try {
            actions = JSON.parseObject(actionsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("解析控制动作JSON失败: {}", e.getMessage());
            ToolResultVO fail = ToolResultVO.fail("控制动作JSON格式错误: " + e.getMessage());
            recordResult("batch_control_device", fail);
            return fail;
        }

        // 一次性批量控制所有设备（HDL API 要求 actions 格式）
        String houseId = sessionContext.getHouseId();
        String gatewayId = actions.isEmpty() ? "" : String.valueOf(actions.get(0).getOrDefault("gatewayId", ""));
        int successCount;
        int failCount;
        try {
            boolean success = hdlApiClient.batchControlDevice(
                    sessionContext.getAccessToken(), houseId, gatewayId, actions);
            if (success) {
                successCount = actions.size();
                failCount = 0;
            } else {
                successCount = 0;
                failCount = actions.size();
            }
        } catch (Exception e) {
            log.error("批量控制设备失败: error={}", e.getMessage());
            successCount = 0;
            failCount = actions.size();
        }

        ToolResultVO result = ToolResultVO.success(
                Map.of("successCount", successCount, "failCount", failCount),
                "批量控制完成：成功 " + successCount + " 台，失败 " + failCount + " 台");
        result.setMultiDevice(true);
        result.setBroadcastText("设备控制指令已发送，成功" + successCount + "台");
        recordResult("batch_control_device", result);
        return result;
    }
}
