package com.agent.scope.framework.controller;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.SessionManager;
import com.agent.scope.framework.vo.ToolResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 场景 REST 代理控制器。
 *
 * <p>前端 SceneCreateResultPage / SceneListPage 等组件需要直接调用 HDL API 操作场景，
 * 本 Controller 作为代理转发请求到 HDL 云端 API。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/scene")
@RequiredArgsConstructor
public class SceneController {

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

    @PostMapping("/list")
    public ToolResultVO listScenes(@RequestHeader("X-Session-Token") String sessionToken,
                                   @RequestBody(required = false) Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String homeId = body != null ? (String) body.get("homeId") : null;
        String roomId = body != null ? (String) body.get("roomId") : null;
        return hdlApiPort.querySceneList(homeId, roomId, ctx);
    }

    @PostMapping("/execute")
    public ToolResultVO executeScene(@RequestHeader("X-Session-Token") String sessionToken,
                                     @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String sceneId = (String) body.get("sceneId");
        if (sceneId == null || sceneId.isBlank()) return ToolResultVO.failure(400, "缺少 sceneId");
        return hdlApiPort.executeScene(List.of(sceneId), ctx);
    }

    @PostMapping("/detail")
    public ToolResultVO getSceneDetail(@RequestHeader("X-Session-Token") String sessionToken,
                                       @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        // 复用场景列表查询（传 roomId 精确查找）
        String homeId = (String) body.get("homeId");
        String roomId = (String) body.get("roomId");
        return hdlApiPort.querySceneList(homeId, roomId, ctx);
    }

    @PostMapping("/delete")
    public ToolResultVO deleteScene(@RequestHeader("X-Session-Token") String sessionToken,
                                    @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String sceneId = (String) body.get("sceneId");
        if (sceneId == null || sceneId.isBlank()) return ToolResultVO.failure(400, "缺少 sceneId");
        // HDL 场景删除：通过场景ID删除
        Map<String, Object> deleteBody = new java.util.LinkedHashMap<>();
        deleteBody.put("sceneId", sceneId);
        deleteBody.put("homeId", ctx.getHouseId());
        return hdlApiPort.createScene(deleteBody, ctx); // 复用 createScene 作为通用 HDL 调用
    }

    @PostMapping("/create")
    public ToolResultVO createScene(@RequestHeader("X-Session-Token") String sessionToken,
                                    @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String sceneName = (String) body.get("sceneName");
        if (sceneName == null || sceneName.isBlank()) return ToolResultVO.failure(400, "缺少 sceneName");
        return hdlApiPort.createScene(body, ctx);
    }

    @PostMapping("/update")
    public ToolResultVO updateScene(@RequestHeader("X-Session-Token") String sessionToken,
                                    @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        return hdlApiPort.createScene(body, ctx); // HDL scene/update 走相同的 create 逻辑
    }
}
