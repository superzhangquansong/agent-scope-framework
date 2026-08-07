package com.agent.scope.framework.controller;

import com.agent.scope.framework.config.SessionLifecycleConfig.SessionLifecycleManager;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * 会话生命周期管理控制器（特性三十）。
 * <p>
 * 暴露会话的显式管理操作（销毁/列表/存在性检查），封装 StateStore 底层操作。
 * 多租户隔离通过 (userId, sessionId) 二元组自动实现。
 * </p>
 *
 * <p><b>接口列表</b>：
 * <ul>
 *   <li>DELETE /api/session/{userId}/{sessionId} - 销毁指定会话</li>
 *   <li>GET    /api/session/{userId}/list       - 列出用户所有会话</li>
 *   <li>GET    /api/session/{userId}/{sessionId}/exists - 检查会话是否存在</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.session", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SessionLifecycleController {

    private final SessionLifecycleManager sessionLifecycleManager;

    /**
     * 销毁指定会话。
     * <p>
     * 从 StateStore 中删除指定 (userId, sessionId) 的所有状态，释放存储空间。
     * 常用于用户主动结束会话或超时清理。
     * </p>
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/{userId}/{sessionId}")
    public Map<String, Object> destroySession(@PathVariable String userId,
                                              @PathVariable String sessionId) {
        log.info("[SessionLifecycle] 销毁会话: userId={}, sessionId={}", userId, sessionId);
        try {
            sessionLifecycleManager.destroySession(userId, sessionId);
            return Map.of(
                    BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK,
                    BusinessConst.RESPONSE_KEY_MESSAGE, "会话已销毁",
                    "userId", userId,
                    "sessionId", sessionId
            );
        } catch (Exception e) {
            log.error("[SessionLifecycle] 销毁会话失败: userId={}, sessionId={}", userId, sessionId, e);
            throw new BusinessException(ErrorCode.SESSION_DESTROY_FAILED,
                    "销毁会话失败: userId=" + userId + ", sessionId=" + sessionId, e);
        }
    }

    /**
     * 列出指定用户的所有会话 ID。
     *
     * @param userId 用户 ID
     * @return 会话 ID 集合
     */
    @GetMapping("/{userId}/list")
    public Map<String, Object> listSessions(@PathVariable String userId) {
        log.info("[SessionLifecycle] 列出会话: userId={}", userId);
        try {
            Set<String> sessionIds = sessionLifecycleManager.listSessions(userId);
            return Map.of(
                    BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK,
                    "userId", userId,
                    "sessionIds", sessionIds,
                    "count", sessionIds.size()
            );
        } catch (Exception e) {
            log.error("[SessionLifecycle] 列出会话失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.SESSION_LIST_FAILED,
                    "列出会话失败: userId=" + userId, e);
        }
    }

    /**
     * 检查会话是否存在。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 存在性结果
     */
    @GetMapping("/{userId}/{sessionId}/exists")
    public Map<String, Object> sessionExists(@PathVariable String userId,
                                             @PathVariable String sessionId) {
        log.info("[SessionLifecycle] 检查会话存在性: userId={}, sessionId={}", userId, sessionId);
        try {
            boolean exists = sessionLifecycleManager.sessionExists(userId, sessionId);
            return Map.of(
                    BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK,
                    "userId", userId,
                    "sessionId", sessionId,
                    "exists", exists
            );
        } catch (Exception e) {
            log.error("[SessionLifecycle] 检查会话存在性失败: userId={}, sessionId={}", userId, sessionId, e);
            throw new BusinessException(ErrorCode.SESSION_CHECK_FAILED,
                    "检查会话存在性失败: userId=" + userId + ", sessionId=" + sessionId, e);
        }
    }
}
