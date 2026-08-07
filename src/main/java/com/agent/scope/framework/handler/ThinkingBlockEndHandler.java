package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ThinkingBlockEndEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import com.agent.scope.framework.service.ChatRecordService;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 思考块结束事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ThinkingBlockEndEvent 分支：
 * 标记推理过程完成，并异步保存本次思考过程摘要（一次 ReAct 迭代可能产生多次思考，分别落库）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class ThinkingBlockEndHandler implements AgentEventHandler<ThinkingBlockEndEvent> {

    private final ChatRecordService chatRecordService;

    @Override
    public Class<ThinkingBlockEndEvent> getEventType() {
        return ThinkingBlockEndEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ThinkingBlockEndEvent tb) throws Exception {
        ThinkingBlockEndEventBO eventBO = ThinkingBlockEndEventBO.builder()
                .type(AgentEventEnum.THINKING_END.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(tb.getReplyId())
                .blockId(tb.getBlockId())
                .build();
        ctx.sendEventBo(eventBO);

        if (ctx.getRecorder().thinkingContent.length() > 0) {
            chatRecordService.saveThinkingMessage(ctx.getSessionId(), ctx.getUserId(),
                    ctx.getRecorder().thinkingContent.toString());
            ctx.getRecorder().thinkingContent.setLength(0);
        }
    }
}
