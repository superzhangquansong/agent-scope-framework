package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.ToolResultEndEventBO;
import com.agent.scope.framework.enums.AgentEventEnum;
import com.agent.scope.framework.service.ChatRecordService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.agentscope.core.event.ToolResultEndEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * 工具执行结束事件处理器。
 * <p>对应 ChatService.forwardAgentEvent 原有 ToolResultEndEvent 分支：
 * 转发执行状态（SUCCESS/ERROR 等），并异步保存工具调用完整记录（入参/出参/状态/耗时）。
 * 当工具结果包含 routePath 时，同时向前端发送结构化 result 事件以触发对应 UI 渲染。</p>
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

        // 从 recorder 中取出工具执行结果，若含 routePath 则向前端发送结构化 result 事件
        forwardStructuredResult(ctx, tr);

        saveToolCallRecord(ctx, tr, state);
    }

    /**
     * 将工具结果中的 routePath + data 以 result 事件推送给前端。
     *
     * <p>工具返回的 ToolResultVO 格式为 {success, message, data, routePath, ...}，
     * 其中 data 是前端 DynamicPage 需要渲染的结构化数据（如设备列表、场景列表）。
     * 本方法从累积的结果 JSON 中提取 routePath 和 data，构造 SSE result 事件。</p>
     */
    private void forwardStructuredResult(EventContext ctx, ToolResultEndEvent tr) {
        String toolCallId = tr.getToolCallId();
        if (toolCallId == null) return;

        var recorder = ctx.getRecorder();
        StringBuilder resultBuilder = recorder.toolCallResults.get(toolCallId);
        if (resultBuilder == null || resultBuilder.isEmpty()) {
            log.debug("[Handler] 工具 {} 无结果内容，跳过 result 事件转发", tr.getToolCallName());
            return;
        }

        String resultJson = resultBuilder.toString();
        try {
            JSONObject resultObj = JSON.parseObject(resultJson);
            if (resultObj == null) return;

            String routePath = resultObj.getString("routePath");
            if (routePath == null || routePath.isEmpty()) return;

            // 提取结构化 data + human-readable message
            Object data = resultObj.get("data");
            String message = resultObj.getString("message");
            if (message == null) message = "";

            // 构造 result 事件 JSON 并发送
            JSONObject resultEvent = new JSONObject();
            resultEvent.put("type", "result");
            resultEvent.put("sessionId", ctx.getSessionId());
            resultEvent.put("routePath", routePath);
            resultEvent.put("data", data != null ? data : new JSONObject());
            resultEvent.put("message", message);

            SseEmitter emitter = ctx.getEmitter();
            emitter.send(SseEmitter.event().name("result").data(resultEvent.toJSONString()));
            log.info("[Handler] 转发结构化 result 事件: toolName={}, routePath={}", tr.getToolCallName(), routePath);
        } catch (IOException e) {
            log.warn("[Handler] 发送 result 事件失败: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("[Handler] 解析工具结果 JSON 失败: toolName={}, error={}", tr.getToolCallName(), e.getMessage());
        }
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
