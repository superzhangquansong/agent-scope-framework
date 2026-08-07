package com.agent.scope.framework.controller;

import com.agent.scope.framework.constant.BusinessConst;
import io.agentscope.harness.agent.HarnessAgent;
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
 * <b>官方中断机制</b>（基于 AgentScope 2.0 文档）：
 * <ol>
 *   <li>调用 {@code harnessAgent.getDelegate().interrupt(userId, sessionId)} 设置 session 级中断标志</li>
 *   <li>ReAct 循环在下一个检查点（reasoning 开始、acting 开始、streaming 每个 chunk）
 *       通过 {@code checkInterrupted()} 发现中断标志，抛出 {@link InterruptedException}</li>
 *   <li>{@code handleInterrupt} 被调用：自动调用 {@code saveStateToSession} 保存 AgentState
 *       到 Redis，返回带 {@code GenerateReason.INTERRUPTED} 标记的恢复消息</li>
 *   <li>Reactor Flux 正常完成（非 dispose 取消），ChatService 的 {@code doOnComplete} 发送
 *       {@code agent_end} 事件并关闭 SSE 连接</li>
 *   <li>下次对同一 session 发起 {@code call()} 时，{@code interruptControl().reset()} 清除标志，
 *       从中断点恢复执行</li>
 * </ol>
 * <b>关键：必须注入实际执行 streamEvents 的 HarnessAgent 实例</b>。
 * 早期版本注入的是 {@code ReActAgent rootReActAgent} bean，但 {@code ChatService} 实际通过
 * {@code HarnessAgent.streamEvents()} 执行（{@code HarnessAgent.Builder.fromAgent(reActAgent)}
 * 内部建立了独立执行流），导致对 rootReActAgent 调用 interrupt() 无法影响 HarnessAgent 的执行，
 * 中断信号被丢弃。修正后改为注入 @Primary 标记的 HarnessAgent 实例，并通过 {@code getDelegate()}
 * 拿到其内部 ReActAgent 调用 per-session 中断方法。
 * </p>
 * <p>
 * <b>同样不能直接 dispose Reactor 订阅</b>，否则会绕过 {@code handleInterrupt} 的状态保存逻辑，
 * 导致 AgentState 不被持久化，下次 call 无法原位恢复。
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
     * HarnessAgent 实例（系统主入口 Agent，@Primary 标记，与 ChatService 注入的同一引用）。
     * <p>调用 {@code interrupt(userId, sessionId)} 设置 session 级中断标志，
     * 由 ReAct 循环在下一个检查点优雅终止并保存状态。</p>
     * <p><b>必须与 ChatService 使用同一 Agent 实例</b>，否则中断标志设置在错误的对象上，
     * 实际执行流检测不到，中断失效。</p>
     */
    private final HarnessAgent harnessAgent;

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
            // 设置 session 级中断标志（per-session，不影响其他并发会话）
            // 必须调用与 ChatService.streamEvents() 同一 Agent 实例的 interrupt 方法，
            // 否则中断标志设置在错误对象上，实际执行流检测不到（早期 bug：注入了 rootReActAgent
            // 而 ChatService 用的是 harnessAgent，两者不是同一执行流）
            //
            // HarnessAgent 自身只暴露 interrupt() / interrupt(Msg) 两个无参/单参方法（中断
            // defaultSessionId），不支持 per-session 中断；但其内部 delegate（ReActAgent）
            // 支持 interrupt(userId, sessionId) per-session 中断。因此通过 getDelegate() 拿到
            // ChatService 实际使用的 ReActAgent 实例，再调用其 per-session 中断方法。
            //
            // ReAct 循环在下一个检查点优雅终止 → handleInterrupt 保存状态 → doOnComplete 发送 agent_end
            // 不 dispose Reactor 订阅，确保 handleInterrupt 的 saveStateToSession 正常执行
            harnessAgent.getDelegate().interrupt(userId, sessionId);
            log.info("[Interrupt] 框架中断信号已发送: userId={}, sessionId={}（等待 ReAct 循环在下一检查点终止）",
                    userId, sessionId);

            return Map.of(
                    "code", BusinessConst.HTTP_OK,
                    "message", "中断信号已发送，Agent 将在下一检查点终止并保存状态",
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
