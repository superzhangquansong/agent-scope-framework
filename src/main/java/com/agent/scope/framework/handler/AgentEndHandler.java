package com.agent.scope.framework.handler;

import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.AgentEndEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 完成回复事件处理器。
 * <p>对应 AgentScope {@link AgentEndEvent}：Agent 推理-执行循环完成时触发。</p>
 *
 * <p><b>注意</b>：本处理器<b>不发送</b> {@code agent_end} SSE 事件。
 * 实际的 {@code agent_end} 由 {@link com.agent.scope.framework.service.ChatService} 的
 * {@code doOnComplete} 回调负责发送，因为需要携带最终回复内容和耗时统计，
 * 并在发送后关闭 SseEmitter。</p>
 *
 * <p>本处理器的作用是：
 * <ol>
 *   <li>在 Handler 体系中注册 AgentEndEvent，使其被 {@link AgentEventHandlerRegistry#dispatch} 匹配，
 *       避免走兜底 OTHER（修复前 AgentEndEvent 被 forwardAgentEvent 特殊排除）</li>
 *   <li>记录日志，便于追踪 Agent 完成时机</li>
 *   <li>清理 forwardAgentEvent 中的 instanceof 特殊分支，统一事件分发链路</li>
 * </ol>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class AgentEndHandler implements AgentEventHandler<AgentEndEvent> {

    @Override
    public Class<AgentEndEvent> getEventType() {
        return AgentEndEvent.class;
    }

    @Override
    public void handle(EventContext ctx, AgentEndEvent event) throws Exception {
        log.info("[Handler] Agent 完成回复: sessionId={}, replyId={}, 等待 doOnComplete 关闭 SSE",
                ctx.getSessionId(), event.getReplyId());
        // 不发送 agent_end SSE 事件，由 ChatService.doOnComplete 统一处理
        // 仅在 Handler 体系中注册，避免 AgentEndEvent 走兜底 OTHER 或被特殊排除
    }
}
