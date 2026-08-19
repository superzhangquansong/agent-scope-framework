package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.RequestStopEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.RequestStopEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 提前停止请求事件处理器。
 * <p>对应 AgentScope {@link RequestStopEvent}：中间件或工具发起提前停止请求时触发。
 * 前端据此感知非正常结束，区分"用户主动中断"和"中间件/工具请求停止"。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class RequestStopHandler implements AgentEventHandler<RequestStopEvent> {

    @Override
    public Class<RequestStopEvent> getEventType() {
        return RequestStopEvent.class;
    }

    @Override
    public void handle(EventContext ctx, RequestStopEvent event) throws Exception {
        // RequestStopEvent 携带 reason（停止原因）和 generateReason（生成原因枚举），
        // 不含 replyId
        log.info("[Handler] 收到提前停止请求: sessionId={}, reason={}",
                ctx.getSessionId(), event.getReason());

        RequestStopEventBO eventBO = RequestStopEventBO.builder()
                .type(AgentEventEnum.REQUEST_STOP.getDesc())
                .sessionId(ctx.getSessionId())
                .reason(event.getReason())
                .build();
        ctx.sendEventBo(eventBO);
    }
}
