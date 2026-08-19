package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ExceedMaxItersEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ExceedMaxItersEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 达到最大迭代次数事件处理器。
 * <p>对应 AgentScope {@link ExceedMaxItersEvent}：Agent 达到 maxIters 限制时触发。
 * 前端据此提示用户"已达到最大迭代次数，回复可能不完整"，区分正常结束和被截断。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class ExceedMaxItersHandler implements AgentEventHandler<ExceedMaxItersEvent> {

    @Override
    public Class<ExceedMaxItersEvent> getEventType() {
        return ExceedMaxItersEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ExceedMaxItersEvent event) throws Exception {
        log.warn("[Handler] Agent 达到最大迭代次数: sessionId={}, replyId={}",
                ctx.getSessionId(), event.getReplyId());

        ExceedMaxItersEventBO eventBO = ExceedMaxItersEventBO.builder()
                .type(AgentEventEnum.EXCEED_MAX_ITERS.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(event.getReplyId())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
