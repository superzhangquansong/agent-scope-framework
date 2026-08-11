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
 * 储能电站 REST 代理控制器。
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
public class EnergyController {

    private final HdlApiPort hdlApiPort;
    private final SessionManager sessionManager;

    private SessionContext resolveContext(String sessionToken) {
        UserSession session = sessionManager.getSession(sessionToken);
        if (session == null || !session.isLoggedIn()) return null;
        return SessionContext.builder()
                .userId(session.getLoginName())
                .houseId(session.getCurrentHomeId())
                .accessToken(session.getHdlAccessToken())
                .build();
    }

    @PostMapping("/station/detail")
    public ToolResultVO stationDetail(@RequestHeader("X-Session-Token") String sessionToken,
                                       @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String homeId = (String) body.get("homeId");
        return hdlApiPort.queryEnergyStationDetail(homeId, ctx);
    }

    @PostMapping("/battery/report")
    public ToolResultVO batteryReport(@RequestHeader("X-Session-Token") String sessionToken,
                                       @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        // 电池报告：通过电站详情获取基础数据，报告由前端/LLM 生成
        String homeId = (String) body.get("homeId");
        return hdlApiPort.queryEnergyStationDetail(homeId, ctx);
    }

    @PostMapping("/savings/report")
    public ToolResultVO savingsReport(@RequestHeader("X-Session-Token") String sessionToken,
                                       @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String homeId = (String) body.get("homeId");
        return hdlApiPort.queryEnergyStationDetail(homeId, ctx);
    }

    @PostMapping("/inverter/info")
    public ToolResultVO inverterInfo(@RequestHeader("X-Session-Token") String sessionToken,
                                      @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String homeId = (String) body.get("homeId");
        return hdlApiPort.queryInverterInfo(homeId, ctx);
    }

    @PostMapping("/fault/diagnosis")
    public ToolResultVO faultDiagnosis(@RequestHeader("X-Session-Token") String sessionToken,
                                        @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String homeId = (String) body.get("homeId");
        // 返回电站列表数据进行故障诊断
        return hdlApiPort.queryEnergyStationList(null, ctx);
    }
}
