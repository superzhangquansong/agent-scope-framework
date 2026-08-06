package com.agent.scope.framework.controller;

import com.agent.scope.framework.constant.BusinessConst;
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
 * <b>重要</b>：必须使用 {@link ReActAgent#interrupt(String, String)} 指定 userId 和 sessionId，
 * 而非 @Deprecated 的无参 {@code interrupt()}（后者使用 defaultSessionId，多会话场景下无效）。
 * HarnessAgent 内部委托的 delegate 就是此 ReActAgent 实例，两者共享同一份 AgentState。
 * </p>
 * <p>
 * <b>参数顺序</b>：{@code interrupt(userId, sessionId)}，userId 是用户 ID（纯数字），
 * sessionId 是会话 ID（32 位十六进制字符串）。传反会导致中断信号发到不存在的会话，无效。
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

    /**
     * ReActAgent 实例（HarnessAgent 的 delegate，共享 AgentState）。
     * 注入 ReActAgent 类型而非 Agent 接口，以访问 {@link ReActAgent#interrupt(String, String)} 方法。
     */
    private final ReActAgent reActAgent;

    /**
     * 中断指定会话的 Agent 执行。
     * <p>
     * 调用 {@link ReActAgent#interrupt(String, String)} 精确中断指定 (userId, sessionId) 的执行，
     * ReAct 循环在下次迭代检查中断标志时安全终止。
     * </p>
     *
     * <p><b>参数说明</b>：
     * <ul>
     *   <li>{@code userId}：用户 ID（纯数字，如 {@code 1680784336610086914}）</li>
     *   <li>{@code sessionId}：会话 ID（32 位十六进制字符串，如 {@code 62ef010c9c194beb8986d2fe53280014}）</li>
     * </ul>
     * </p>
     *
     * @param userId    用户 ID（纯数字）
     * @param sessionId 会话 ID（32 位十六进制字符串）
     * @return 操作结果
     */
    @PostMapping
    public Map<String, Object> interrupt(@RequestParam String userId,
                                         @RequestParam String sessionId) {
        log.info("[Interrupt] 收到中断请求: userId={}, sessionId={}", userId, sessionId);

        // 参数校验：防止 userId 和 sessionId 传反（常见错误）
        // userId 是纯数字，sessionId 是 32 位十六进制字符串
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
            // 精确中断指定 (userId, sessionId) 的 ReAct 循环
            reActAgent.interrupt(userId, sessionId);
            log.info("[Interrupt] 中断信号已发送: userId={}, sessionId={}", userId, sessionId);
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

    /**
     * 校验 userId 格式：纯数字字符串。
     *
     * @param userId 用户 ID
     * @return true 格式正确
     */
    private boolean isValidUserId(String userId) {
        return userId != null && !userId.isBlank() && userId.matches("\\d+");
    }

    /**
     * 校验 sessionId 格式：32 位十六进制字符串。
     *
     * @param sessionId 会话 ID
     * @return true 格式正确
     */
    private boolean isValidSessionId(String sessionId) {
        return sessionId != null && !sessionId.isBlank()
                && sessionId.matches("[0-9a-fA-F]{32}");
    }
}
