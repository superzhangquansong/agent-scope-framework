package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ModelCallStartEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.ModelCallStartEvent;
import org.springframework.stereotype.Component;

/**
 * 模型调用开始事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ModelCallStartEvent 分支：
 * 记录调用起始时间（按 replyId）、初始化输出累积器，并推送 model_call_start 事件。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
public class ModelCallStartHandler implements AgentEventHandler<ModelCallStartEvent> {

    @Override
    public Class<ModelCallStartEvent> getEventType() {
        return ModelCallStartEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ModelCallStartEvent event) throws Exception {
        String replyId = event.getReplyId();
        if (replyId != null) {
            ctx.getRecorder().modelCallStartTimes.put(replyId, System.currentTimeMillis());
            ctx.getRecorder().modelCallOutputs.put(replyId, new StringBuilder());
        }
        ModelCallStartEventBO eventBO = ModelCallStartEventBO.builder()
                .type(AgentEventEnum.MODEL_CALL_START.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(replyId)
                .build();
        ctx.sendEventBo(eventBO);
    }
}
