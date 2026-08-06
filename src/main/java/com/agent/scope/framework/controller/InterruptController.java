package com.agent.scope.framework.controller;

import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.service.ChatService;
import io.agentscope.core.ReActAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 中断执行控制器（特性5）。
 * <p>
 * 提供运行时中断 Agent 执行的 REST 接口，支持按 (userId, sessionId) 精确中断。
 * 常见场景：用户点击"停止"按钮、超时强制中断、内容审核触发中断。
 * </p>
 * <p>
 * <b>双重中断机制</b>：
 * <ol>
 *   <li>{@link ChatService#interruptSession(String)}：直接 dispose Reactor 订阅 + 关闭 SSE，
 *       立即终止正在运行的 HarnessAgent streamEvents Flux</li>
 *   <li>{@link ReActAgent#interrupt(String, String)}：设置框架级中断标志，
 *       ReAct 循环在下次迭代时检查并安全终止</li>
 * </ol>
 * 单独使用 #2 无效，因为 HarnessAgent 的 streamEvents Flux 与 ReActAgent.interrupt()
 * 的中断标志可能不在同一执行上下文，Flux 不会被取消。必须配合 #1 直接 dispose 订阅。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/interrupt")
@RequiredArgsConstructor
public class InterruptController {

    private final ReActAgent reActAgent;

    /**
     * ChatService 引用，用于直接 dispose Reactor 订阅实现立即中断。
     */
    private final ChatService chatService;

    @PostMapping
    public Map<String, Object> interrupt(@RequestParam String userId,
                                         @RequestParam String sessionId) {
        log.info("[Interrupt] 收到中断请求: userId={}, sessionId={}", userId, sessionId);

        if (!isValidUserId(userId) || !isValidSessionId(sessionId)) {
            String hint = String.format(
                    "参数格式错误: userId 应为纯数字, sessionId 应为 32 位十六进制字符串。"
                            + "当前 userId=%s, sessionId=%s。请检查是否传反。",
                    userId, sessionId);
            log.warn("[Interrupt] {}", hint);
            return Map.of(
                    "code", BusinessConst.HTTP_BAD_REQUEST,
                    "message", hint,
                    "userId", userId,
                    "sessionId", sessionId
            );
        }

        try {
            // 1. 直接取消 Reactor 订阅 + 关闭 SSE（立即生效，终止正在运行的 Flux）
            boolean disposed = chatService.interruptSession(sessionId);
            log.info("[Interrupt] Reactor 订阅中断结果: sessionId={}, disposed={}", sessionId, disposed);

            // 2. 框架级中断标志（ReAct 循环下次迭代时检查，作为补充）
            reActAgent.interrupt(userId, sessionId);
            log.info("[Interrupt] 框架中断信号已发送: userId={}, sessionId={}", userId, sessionId);

            return Map.of(
                    "code", BusinessConst.HTTP_OK,
                    "message", "中断信号已发送",
                    "userId", userId,
                    "sessionId", sessionId
            );
        } catch (Exception e) {
            log.error("[Interrupt] 中断失败: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage());
            return Map.of(
                    "code", BusinessConst.HTTP_INTERNAL_ERROR,
                    "message", "中断失败: " + e.getMessage(),
                    "userId", userId,
                    "sessionId", sessionId
            );
        }
    }

    private boolean isValidUserId(String userId) {
        return userId != null && !userId.isBlank() && userId.matches("\\d+");
    }

    private boolean isValidSessionId(String sessionId) {
        return sessionId != null && !sessionId.isBlank()
                && sessionId.matches("[0-9a-fA-F]{32}");
    }
}
