package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.AgentStartEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.AgentStartEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 开始回复事件处理器。
 * <p>对应 AgentScope {@link AgentStartEvent}：Agent 每次开始新的回复周期时触发。
 * 本处理器统一发送 {@code agent_start} SSE 事件，替代 ChatService 中原来的手动发送逻辑，
 * 避免框架产出的 AgentStartEvent 走兜底 OTHER 导致前端收到重复事件。</p>
 *
 * <p>修复前流程（有 bug）：
 * <pre>
 * ChatService 手动发 agent_start
 *   → streamEvents 产出 AgentStartEvent
 *     → 无 Handler 匹配 → 走兜底 OTHER → 发送 "AgentStartEvent" 类名事件
 *     → 前端收到两个事件（agent_start + AgentStartEvent）
 * </pre>
 *
 * 修复后流程：
 * <pre>
 * streamEvents 产出 AgentStartEvent
 *   → AgentStartHandler 匹配 → 发送 agent_start SSE 事件
 *   → 前端只收到一个事件
 * </pre>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class AgentStartHandler implements AgentEventHandler<AgentStartEvent> {

    @Override
    public Class<AgentStartEvent> getEventType() {
        return AgentStartEvent.class;
    }

    @Override
    public void handle(EventContext ctx, AgentStartEvent event) throws Exception {
        log.info("[Handler] Agent 开始回复: sessionId={}, replyId={}, agentName={}",
                ctx.getSessionId(), event.getReplyId(), event.getName());

        AgentStartEventBO eventBO = AgentStartEventBO.builder()
                .type(AgentEventEnum.AGENT_START.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(event.getReplyId())
                .agentName(event.getName())
                .role(event.getRole())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
