package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.SubagentExposedEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.SubagentExposedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 子 Agent 暴露事件处理器。
 * <p>对应 AgentScope {@link SubagentExposedEvent}：通过
 * {@code agent_spawn(expose_to_user=true)} 生成的子 Agent 被暴露为
 * 用户可寻址的入口点时触发。前端据此渲染新的会话入口（如侧边栏新增子 Agent 标签）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class SubagentExposedHandler implements AgentEventHandler<SubagentExposedEvent> {

    @Override
    public Class<SubagentExposedEvent> getEventType() {
        return SubagentExposedEvent.class;
    }

    @Override
    public void handle(EventContext ctx, SubagentExposedEvent event) throws Exception {
        log.info("[Handler] 子 Agent 被暴露: sessionId={}, subagentId={}, agentId={}, subSessionId={}, label={}",
                ctx.getSessionId(), event.getSubagentId(), event.getAgentId(),
                event.getSessionId(), event.getLabel());

        SubagentExposedEventBO eventBO = SubagentExposedEventBO.builder()
                .type(AgentEventEnum.SUBAGENT_EXPOSED.getDesc())
                .sessionId(ctx.getSessionId())
                .subagentId(event.getSubagentId())
                .agentId(event.getAgentId())
                .subSessionId(event.getSessionId())
                .label(event.getLabel())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
