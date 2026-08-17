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
        return hdlApiPort.deleteScene(sceneId, ctx);
    }

    @PostMapping("/create")
    public ToolResultVO createScene(@RequestHeader("X-Session-Token") String sessionToken,
                                    @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String sceneName = (String) body.get("sceneName");
        if (sceneName == null || sceneName.isBlank()) return ToolResultVO.failure(400, "缺少 sceneName");
        return hdlApiPort.createScene(buildSceneBody(body, false), ctx);
    }

    @PostMapping("/update")
    public ToolResultVO updateScene(@RequestHeader("X-Session-Token") String sessionToken,
                                    @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        return hdlApiPort.updateScene(buildSceneBody(body, true), ctx);
    }

    /**
     * 将前端 CreateSceneRequest（顶层 sceneName/functions/...）转换为 HDL 场景报文。
     *
     * <p>HDL scene/add、scene/update 均要求顶层 {@code scenes} 数组包裹场景对象，
     * 每个场景对象含 name/userSceneId、gatewayId、collect、executePush、functions 字段。
     * 前端直传的是扁平结构，此处统一转换为 HDL 要求的嵌套结构。</p>
     *
     * @param body      前端请求体（含 sceneName/sceneId/gatewayId/collect/executePush/functions）
     * @param forUpdate true 表示更新场景（场景对象含 userSceneId），false 表示创建
     * @return HDL scenes 数组结构 {@code {scenes: [{...}]}}
     */
    private Map<String, Object> buildSceneBody(Map<String, Object> body, boolean forUpdate) {
        Map<String, Object> scene = new java.util.LinkedHashMap<>();
        if (forUpdate) {
            Object sceneId = body.get("sceneId");
            if (sceneId != null) {
                scene.put("userSceneId", sceneId);
            }
        }
        scene.put("name", body.get("sceneName"));
        Object gatewayId = body.get("gatewayId");
        if (gatewayId != null) {
            scene.put("gatewayId", gatewayId);
        }
        Object collect = body.get("collect");
        scene.put("collect", Boolean.TRUE.equals(collect) ? 1 : 0);
        scene.put("executePush", body.get("executePush") != null ? body.get("executePush") : false);
        scene.put("functions", body.get("functions"));

        List<Map<String, Object>> scenes = new java.util.ArrayList<>(1);
        scenes.add(scene);

        Map<String, Object> result = new java.util.LinkedHashMap<>(1);
        result.put("scenes", scenes);
        return result;
    }
}
