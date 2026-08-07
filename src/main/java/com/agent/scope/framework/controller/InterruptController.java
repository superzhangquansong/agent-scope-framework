package com.agent.scope.framework.controller;

import com.agent.scope.framework.annotation.Auditable;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import com.agent.scope.framework.service.ChatService;
import com.agent.scope.framework.vo.Response;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
 *   <li>框架中断：调用 {@code harnessAgent.getDelegate().interrupt(userId, sessionId)}
 *       设置 session 级中断标志，ReAct 循环在下一检查点应检测并终止。
 *       此步骤负责框架内部状态保存（handleInterrupt → saveStateToSession）。</li>
 *   <li>订阅中断（兜底）：调用 {@code chatService.interruptSession(sessionId)}
 *       直接 dispose Reactor 订阅并关闭 SSE 连接。
 *       此步骤确保 SSE 流立即终止，避免 AgentScope 2.0.0 框架中断信号
 *       在 ReAct 循环检查点未能及时生效时 Agent 继续执行多轮的问题。</li>
 * </ol>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat/interrupt")
@RequiredArgsConstructor
public class InterruptController {

    /**
     * HarnessAgent 实例（系统主入口 Agent，@Primary 标记，与 ChatService 注入的同一引用）。
     */
    private final HarnessAgent harnessAgent;

    /**
     * 聊天服务（用于直接 dispose Reactor 订阅，兜底中断）。
     */
    private final ChatService chatService;

    /**
     * 中断指定会话的 Agent 执行。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 中断结果
     */
    @PostMapping
    @Auditable(action = "INTERRUPT", target = "中断Agent执行")
    public Response<Map<String, Object>> interrupt(@RequestParam String userId,
                                         @RequestParam String sessionId) {
        log.info("[Interrupt] 收到中断请求: userId={}, sessionId={}", userId, sessionId);

        if (!isValidUserId(userId) || !isValidSessionId(sessionId)) {
            String hint = String.format(
                    "参数格式错误: userId 应为纯数字, sessionId 应为 32 位十六进制字符串。"
                            + "当前 userId=%s, sessionId=%s。请检查是否传反。",
                    userId, sessionId);
            log.warn("[Interrupt] {}", hint);
            throw new BusinessException(ErrorCode.INTERRUPT_PARAM_INVALID, hint);
        }

        // 步骤1：发送框架中断信号（设置 session 级中断标志，供 ReAct 循环检查）
        try {
            harnessAgent.getDelegate().interrupt(userId, sessionId);
            log.info("[Interrupt] 框架中断信号已发送: userId={}, sessionId={}", userId, sessionId);
        } catch (Exception e) {
            log.warn("[Interrupt] 框架中断信号发送失败（继续执行订阅中断）: error={}", e.getMessage());
        }

        // 步骤2：直接 dispose Reactor 订阅（兜底机制，确保 SSE 流立即终止）
        boolean subscriptionInterrupted = chatService.interruptSession(sessionId);

        Map<String, Object> result = new HashMap<>(4);
        result.put(BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK);
        result.put(BusinessConst.RESPONSE_KEY_MESSAGE,
                subscriptionInterrupted ? "中断成功，SSE 流已终止" : "中断信号已发送，会话可能已结束");
        result.put("userId", userId);
        result.put("sessionId", sessionId);
        result.put("subscriptionDisposed", subscriptionInterrupted);
        return Response.success(result);
    }

    /**
     * 校验用户 ID 格式（纯数字）。
     */
    private boolean isValidUserId(String userId) {
        return userId != null && !userId.isBlank() && userId.matches("\\d+");
    }

    /**
     * 校验会话 ID 格式（32 位十六进制字符串）。
     */
    private boolean isValidSessionId(String sessionId) {
        return sessionId != null && !sessionId.isBlank()
                && sessionId.matches("[0-9a-fA-F]{32}");
    }
}
