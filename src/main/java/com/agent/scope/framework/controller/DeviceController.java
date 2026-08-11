package com.agent.scope.framework.controller;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.SessionManager;
import com.agent.scope.framework.vo.ToolResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备 REST 代理控制器。
 *
 * <p>前端 DeviceStatusPage/DeviceListPage 等组件需要直接调用 HDL API 获取设备实时数据，
 * 本 Controller 作为代理转发请求到 HDL 云端 API，避免前端直连 HDL 网关。</p>
 *
 * <p>所有请求需携带 {@code X-Session-Token} 头，用于获取 HDL accessToken 和 houseId。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final HdlApiPort hdlApiPort;
    private final SessionManager sessionManager;

    /**
     * 查询设备列表。
     *
     * <p>查询当前房屋下所有设备的实时状态列表。</p>
     *
     * @param body         请求体（可选含 spk 过滤字段）
     * @param sessionToken 会话令牌
     * @return 设备列表
     */
    @PostMapping("/list")
    public ToolResultVO listDevices(@RequestBody(required = false) Map<String, Object> body,
                                     @RequestHeader("X-Session-Token") String sessionToken) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) {
            return ToolResultVO.failure(401, "未登录或会话已过期");
        }
        String spk = body != null ? (String) body.get("spk") : null;
        return hdlApiPort.queryDeviceList(spk, ctx);
    }

    /**
     * 查询设备详情（最新状态）。
     *
     * <p>传入设备 ID 列表查询设备实时属性值，用于 DeviceStatusPage 挂载时获取最新状态。</p>
     *
     * @param body         请求体（含 deviceIds 和可选 gatewayId）
     * @param sessionToken 会话令牌
     * @return 设备详情
     */
    @PostMapping("/detail")
    public ToolResultVO getDeviceDetail(@RequestBody Map<String, Object> body,
                                         @RequestHeader("X-Session-Token") String sessionToken) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) {
            return ToolResultVO.failure(401, "未登录或会话已过期");
        }
        String deviceIds = (String) body.get("deviceIds");
        if (deviceIds == null || deviceIds.isBlank()) {
            // 兼容单个 deviceId 字段
            deviceIds = (String) body.get("deviceId");
        }
        if (deviceIds == null || deviceIds.isBlank()) {
            return ToolResultVO.failure(400, "缺少 deviceIds 参数");
        }
        return hdlApiPort.queryDeviceDetail(deviceIds, ctx);
    }

    /**
     * 控制单个设备。
     *
     * <p>DeviceStatusPage 的属性控制面板调用此接口下发设备控制指令。</p>
     *
     * @param body         请求体（含 deviceId, gatewayId, spk, attributes）
     * @param sessionToken 会话令牌
     * @return 控制结果
     */
    @PostMapping("/control")
    public ToolResultVO controlDevice(@RequestBody Map<String, Object> body,
                                       @RequestHeader("X-Session-Token") String sessionToken) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) {
            return ToolResultVO.failure(401, "未登录或会话已过期");
        }
        String deviceId = (String) body.get("deviceId");
        String gatewayId = (String) body.get("gatewayId");
        String spk = (String) body.get("spk");
        String attributesJson = body.get("attributes") instanceof String
                ? (String) body.get("attributes")
                : null;

        if (deviceId == null || deviceId.isBlank()) return ToolResultVO.failure(400, "缺少 deviceId");
        if (gatewayId == null || gatewayId.isBlank()) return ToolResultVO.failure(400, "缺少 gatewayId");
        if (spk == null || spk.isBlank()) return ToolResultVO.failure(400, "缺少 spk");

        return hdlApiPort.controlDevice(deviceId, gatewayId, spk, attributesJson, ctx);
    }

    /**
     * 从 X-Session-Token 解析会话上下文。
     *
     * @param sessionToken 会话令牌
     * @return SessionContext，无效时返回 null
     */
    private SessionContext resolveContext(String sessionToken) {
        UserSession session = sessionManager.getSession(sessionToken);
        if (session == null || !session.isLoggedIn()) {
            log.warn("[DeviceController] 会话无效: token={}", sessionToken);
            return null;
        }
        return SessionContext.builder()
                .userId(session.getLoginName())
                .houseId(session.getCurrentHomeId())
                .accessToken(session.getHdlAccessToken())
                .build();
    }
}
