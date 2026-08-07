package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolResultEndEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import com.agent.scope.framework.service.ChatRecordService;
import io.agentscope.core.event.ToolResultEndEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具执行结束事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ToolResultEndEvent 分支：
 * 转发执行状态（SUCCESS/ERROR 等），并异步保存工具调用完整记录（入参/出参/状态/耗时）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolResultEndHandler implements AgentEventHandler<ToolResultEndEvent> {

    private final ChatRecordService chatRecordService;

    @Override
    public Class<ToolResultEndEvent> getEventType() {
        return ToolResultEndEvent.class;
    }

    @Override
    public void handle(EventContext ctx, ToolResultEndEvent tr) throws Exception {
        String toolCallId = tr.getToolCallId();
        String state = tr.getState() != null ? tr.getState().name() : "UNKNOWN";
        ToolResultEndEventBO eventBO = ToolResultEndEventBO.builder()
                .type(AgentEventEnum.TOOL_RESULT_END.getDesc())
                .sessionId(ctx.getSessionId())
                .toolCallId(toolCallId)
                .toolName(tr.getToolCallName())
                .state(state)
                .build();
        ctx.sendEventBo(eventBO);

        saveToolCallRecord(ctx, tr, state);
    }

    /**
     * 异步保存工具调用完整记录。
     * <p>从会话记录容器中取出该工具调用的开始时间、累积入参和出参，
     * 计算执行耗时后通过 {@link ChatRecordService} 异步落库。</p>
     */
    private void saveToolCallRecord(EventContext ctx, ToolResultEndEvent tr, String state) {
        String toolCallId = tr.getToolCallId();
        if (toolCallId == null) {
            return;
        }
        var recorder = ctx.getRecorder();
        Long startTime = recorder.toolCallStartTimes.remove(toolCallId);
        StringBuilder argsBuilder = recorder.toolCallArguments.remove(toolCallId);
        StringBuilder resultBuilder = recorder.toolCallResults.remove(toolCallId);
        String arguments = argsBuilder != null ? argsBuilder.toString() : null;
        String result = resultBuilder != null ? resultBuilder.toString() : null;
        long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0L;

        // 调试日志：打印工具结果内容，用于排查 HITL 恢复后 LLM 误判工具失败的问题
        // 如果 result 为 null 或空，说明 ToolResultTextDeltaHandler 未正确累积 delta
        // （常见于 HITL 恢复路径中缺少 ToolCallStartEvent 导致 map 未初始化）
        log.info("[Handler] 工具执行结束: sessionId={}, toolCallId={}, toolName={}, state={}, "
                        + "argumentsLen={}, resultLen={}, resultPreview={}",
                ctx.getSessionId(), toolCallId, tr.getToolCallName(), state,
                arguments != null ? arguments.length() : 0,
                result != null ? result.length() : 0,
                result != null ? result.substring(0, Math.min(result.length(), 200)) : "<null>");

        chatRecordService.saveToolCall(ctx.getSessionId(), toolCallId, tr.getToolCallName(),
                arguments, result, state, durationMs);
    }
}
