package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ModelCallEndEventBO;
import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.enums.AgentEventEnum;
import com.agent.scope.framework.service.ChatRecordService;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.model.ChatUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模型调用结束事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ModelCallEndEvent 分支：
 * 累积 Token 消耗到会话总量、计算耗时、取出累积输出内容、异步保存单次模型调用记录，
 * 并推送 model_call_end 事件。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelCallEndHandler implements AgentEventHandler<ModelCallEndEvent> {

    private final ChatRecordService chatRecordService;
    private final AgentScopeProperties agentScopeProperties;

    @Override
    public Class<ModelCallEndEvent> getEventType() {
        return ModelCallEndEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ModelCallEndEvent event) throws Exception {
        String replyId = event.getReplyId();
        int inputTokens = 0;
        int outputTokens = 0;
        int cachedTokens = 0;
        try {
            ChatUsage usage = event.getUsage();
            if (usage != null) {
                inputTokens = usage.getInputTokens();
                outputTokens = usage.getOutputTokens();
                cachedTokens = usage.getCachedTokens();
                ctx.getRecorder().totalInputTokens.addAndGet(inputTokens);
                ctx.getRecorder().totalOutputTokens.addAndGet(outputTokens);
            }
        } catch (Exception e) {
            log.debug("[Handler] 获取Token使用量失败: {}", e.getMessage());
        }

        var recorder = ctx.getRecorder();
        Long startTime = replyId != null ? recorder.modelCallStartTimes.remove(replyId) : null;
        long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0L;
        StringBuilder outputBuilder = replyId != null ? recorder.modelCallOutputs.remove(replyId) : null;
        String outputContent = outputBuilder != null ? outputBuilder.toString() : null;

        chatRecordService.saveModelCall(ctx.getSessionId(), replyId, outputContent,
                inputTokens, outputTokens, cachedTokens,
                agentScopeProperties.getModelName(), durationMs);

        ModelCallEndEventBO eventBO = ModelCallEndEventBO.builder()
                .type(AgentEventEnum.MODEL_CALL_END.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(replyId)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .build();
        ctx.sendEventBo(eventBO);
    }
}
