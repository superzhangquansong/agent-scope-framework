package com.agent.scope.framework.controller;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.DeviceContextService;
import com.agent.scope.framework.service.SceneRecommendService;
import com.agent.scope.framework.service.SessionManager;
import com.agent.scope.framework.vo.SceneRecommendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景推荐 REST 控制器。
 *
 * <p>前端入口推荐：前端首页展示推荐场景卡片时调用此接口获取推荐方案。
 * 推荐结果仅展示给用户，用户点击确认后前端调用 /api/scene/create 创建场景。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/scene")
@RequiredArgsConstructor
public class SceneRecommendController {

    /**
     * 场景推荐引擎
     */
    private final SceneRecommendService sceneRecommendService;

    /**
     * 设备上下文服务（读取 Redis 缓存的设备列表）
     */
    private final DeviceContextService deviceContextService;

    /**
     * 会话管理器
     */
    private final SessionManager sessionManager;

    /**
     * 获取场景推荐列表。
     * <p>
     * 根据当前登录用户的房屋设备组合，推荐匹配的场景方案。
     * 前端展示推荐卡片，用户确认后调用 /api/scene/create 创建场景。
     * </p>
     *
     * @param sessionToken 会话令牌
     * @return 推荐方案列表
     */
    @PostMapping("/recommend")
    public List<SceneRecommendVO> recommend(@RequestHeader("X-Session-Token") String sessionToken) {
        log.info("[SceneRecommend] 收到推荐请求: sessionToken={}", sessionToken);

        UserSession session = sessionManager.getSession(sessionToken);
        if (session == null || !session.isLoggedIn()) {
            log.warn("[SceneRecommend] 未登录或会话已过期");
            return List.of();
        }

        String houseId = session.getCurrentHomeId();
        if (houseId == null || houseId.isBlank()) {
            log.warn("[SceneRecommend] 当前房屋 ID 为空");
            return List.of();
        }

        // 从 Redis 缓存读取设备列表
        List<DeviceContextService.DeviceBrief> devices = deviceContextService.getCachedDeviceBriefs(houseId);
        if (devices.isEmpty()) {
            log.warn("[SceneRecommend] 设备缓存为空，houseId={}，请先调用 query_device_list", houseId);
            return List.of();
        }

        List<SceneRecommendVO> result = sceneRecommendService.recommend(devices);
        log.info("[SceneRecommend] 推荐完成: userId={}, houseId={}, 推荐数量={}",
                session.getLoginName(), houseId, result.size());
        return result;
    }
}
