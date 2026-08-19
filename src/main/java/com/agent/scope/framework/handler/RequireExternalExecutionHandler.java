package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.RequireExternalExecutionEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部执行请求事件处理器。
 * <p>对应 AgentScope {@link RequireExternalExecutionEvent}：Agent 暂停等待外部系统
 * 执行工具时触发。与 {@link RequireUserConfirmHandler}（HITL 用户确认）并列，
 * 区别在于本事件将工具执行权交给外部系统而非人工审批。</p>
 *
 * <p>前端据此展示"等待外部执行"状态，并可触发外部系统回调接口将执行结果回传
 * （通过 {@code ExternalExecutionResultEvent} 输入事件恢复 Agent）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class RequireExternalExecutionHandler implements AgentEventHandler<RequireExternalExecutionEvent> {

    @Override
    public Class<RequireExternalExecutionEvent> getEventType() {
        return RequireExternalExecutionEvent.class;
    }

    @Override
    public void handle(EventContext ctx, RequireExternalExecutionEvent event) throws Exception {
        log.info("[Handler] 外部执行请求: sessionId={}, replyId={}, toolCount={}",
                ctx.getSessionId(), event.getReplyId(),
                event.getToolCalls() != null ? event.getToolCalls().size() : 0);

        // 将 ToolUseBlock 列表转为前端可消费的 Map 结构
        List<Map<String, Object>> toolCallMaps = List.of();
        if (event.getToolCalls() != null) {
            toolCallMaps = event.getToolCalls().stream()
                    .map(tc -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("toolCallId", tc.getId());
                        map.put("toolName", tc.getName());
                        map.put("input", tc.getInput());
                        return map;
                    })
                    .toList();
        }

        RequireExternalExecutionEventBO eventBO = RequireExternalExecutionEventBO.builder()
                .type(AgentEventEnum.REQUIRE_EXTERNAL_EXECUTION.getDesc())
                .sessionId(ctx.getSessionId())
                .replyId(event.getReplyId())
                .toolCalls(toolCallMaps)
                .build();
        ctx.sendEventBo(eventBO);
    }
}
