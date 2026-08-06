package com.agent.scope.framework.service;

import com.agent.scope.framework.bo.event.*;
import com.agent.scope.framework.constant.FileConst;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.dto.ChatStreamDTO;
import com.agent.scope.framework.enums.AgentEventEnum;
import com.agent.scope.framework.enums.ImageTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.*;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.agent.scope.framework.constant.BusinessConst.CTX_KEY_SESSION_CONTEXT;


/**
 * 核心聊天服务 —— 完全基于 AgentScope HarnessAgent 的智能路由。
 * <p>
 * 本服务是整个系统的核心入口，<b>不包含任何硬编码的意图判断或命令解析逻辑</b>。
 * 所有用户消息统一交给 HarnessAgent 处理，由 LLM 通过 ReAct 循环智能决策：
 * <ol>
 *   <li>LLM 理解用户自然语言意图</li>
 *   <li>LLM 自主选择并调用注册在 Toolkit 中的业务工具（DeviceTool、ProductTool 等）</li>
 *   <li>工具执行后 LLM 观察结果，决定是否继续推理或返回最终回复</li>
 *   <li>Agent 事件流通过 SSE 实时推送到前端</li>
 * </ol>
 * </p>
 * <p>
 * <b>事件处理遵循 AgentScope 2.0 官方规范</b>：
 * 使用 instanceof 按事件类型分别处理，对于流式文本片段（TextBlockDeltaEvent）
 * 只转发增量文本 getDelta()，避免对整个事件对象做全量 JSON 序列化。
 * </p>
 * <p>
 * <b>对话全链路记录</b>：通过 {@link ChatRecordService} 异步落库以下信息，
 * 不阻塞 SSE 主流程：
 * <ul>
 *   <li>用户输入消息（streamEvents 开始时）</li>
 *   <li>LLM 思考过程摘要（ThinkingBlockEndEvent 时）</li>
 *   <li>工具调用全量信息（ToolResultEndEvent 时，含入参/出参/状态/耗时）</li>
 *   <li>LLM 最终回复（doOnComplete 时）</li>
 *   <li>Token 消耗（ModelCallEndEvent 累积，doOnComplete 时统一保存）</li>
 * </ul>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /**
     * Jackson ObjectMapper（线程安全，静态复用，避免每次创建）
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 模型名称标识（用于 Token 消耗记录）
     */
    private static final String MODEL_NAME = "dashscope";

    private final HarnessAgent harnessAgent;

    private final ChatRecordService chatRecordService;

    public void streamEvents(ChatStreamDTO dto, SseEmitter emitter) {
        String sessionId = dto.getSessionId();
        String userId = dto.getUserId();
        String houseId = dto.getHouseId();
        String accessToken = dto.getAccessToken();

        // 会话级别记录容器：累积思考内容、最终回复、工具调用入参/出参/开始时间、Token 消耗
        final ChatSessionRecorder recorder = new ChatSessionRecorder();

        try {
            SessionContext ctx = SessionContext.builder()
                    .userId(userId)
                    .houseId(houseId)
                    .sessionId(sessionId)
                    .accessToken(accessToken)
                    .build();

            // 构建 RuntimeContext，预注入 SessionContext
            // SessionContextMiddleware 会检测到已存在的 SessionContext，不再覆盖
            // 这样 accessToken 等敏感信息能正确传递到工具方法
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .put(CTX_KEY_SESSION_CONTEXT, ctx)
                    .build();

            // 异步保存用户输入消息（不阻塞主流程）
            chatRecordService.saveUserMessage(sessionId, userId, houseId, dto.getUserMessage());

            // 发送 agent_start 事件
            sendEvent(emitter, "agent_start", sessionId, Map.of());

            // 委托 HarnessAgent 执行 ReAct 推理循环
            // LLM 将自主决策调用哪个工具、如何解析用户指令
            // 所有意图路由、参数构造、设备匹配全部由 LLM 智能完成
            harnessAgent.streamEvents(buildUserMessage(dto), runtimeContext)
                    .doOnNext(event -> {
                        try {
                            forwardAgentEvent(emitter, sessionId, event, recorder);
                        } catch (Exception e) {
                            log.warn("[Chat] SSE事件转发失败: eventType={}, error={}",
                                    event.getClass().getSimpleName(), e.getMessage());
                        }
                    })
                    .doOnComplete(() -> {
                        // Agent 执行完成（含记忆整合等后台收尾）
                        long totalDurationMs = System.currentTimeMillis() - recorder.sessionStartTime;
                        log.info("[Chat] HarnessAgent 执行完成: sessionId={}, 总耗时={}ms", sessionId, totalDurationMs);

                        // 异步保存 LLM 最终回复
                        if (recorder.finalReplyContent.length() > 0) {
                            chatRecordService.saveAssistantMessage(sessionId, recorder.finalReplyContent.toString());
                        }

                        // 异步保存 Token 消耗（累积所有 ModelCallEndEvent 的 usage）
                        chatRecordService.saveTokenUsage(sessionId,
                                recorder.totalInputTokens.get(),
                                recorder.totalOutputTokens.get(),
                                MODEL_NAME);

                        try {
                            sendEvent(emitter, "agent_end", sessionId, Map.of());
                        } catch (IOException e) {
                            log.warn("[Chat] 发送 agent_end 失败: {}", e.getMessage());
                        }
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .doOnError(err -> {
                        log.error("[Chat] HarnessAgent 执行异常: sessionId={}", sessionId, err);
                        try {
                            sendEvent(emitter, "error", sessionId, Map.of(
                                    "error", Map.of("code", "AGENT_ERROR",
                                            "message", err.getMessage() != null ? err.getMessage() : "Agent执行异常")));
                        } catch (IOException ignored) {
                            // SSE 发送失败时无法再通知客户端
                        }
                        sendDone(emitter);
                        emitter.complete();
                    })
                    .subscribe(); // 触发异步执行

        } catch (Exception e) {
            log.error("[Chat] 消息处理异常: sessionId={}", sessionId, e);
            try {
                sendEvent(emitter, "error", sessionId, Map.of(
                        "error", Map.of("code", "INTERNAL_ERROR", "message", e.getMessage())));
            } catch (IOException ignored) {
                // SSE 发送失败时无法再通知客户端
            }
            sendDone(emitter);
            emitter.complete();
        }
    }

    // ==================== Agent 事件转发（遵循官方 instanceof 模式）====================

    /**
     * 根据请求 DTO 构建发送给 Agent 的用户消息。
     * <p>
     * 当 {@code images} 非空时构建多模态消息（文本 + 图片 ContentBlock），
     * 否则回退为纯文本消息。图片来源由 {@link ChatStreamDTO#getImageType()} 决定：
     * <ul>
     *   <li>{@code "url"}：使用 {@link URLSource} 包装图片 URL</li>
     *   <li>{@code "base64"}：使用 {@link Base64Source} 包装 Base64 图片数据，
     *       默认 MIME 类型 {@code image/jpeg}</li>
     * </ul>
     * </p>
     *
     * @param dto 聊天请求 DTO
     * @return AgentScope 用户消息
     */
    private Msg buildUserMessage(ChatStreamDTO dto) {
        String userMessage = dto.getUserMessage();
        List<String> images = dto.getImages();
        if (images == null || images.isEmpty()) {
            // 纯文本消息
            return new UserMessage(userMessage);
        }

        // 多模态消息：文本 + N 个图片 ContentBlock
        List<ContentBlock> blocks = new ArrayList<>(images.size() + 1);
        blocks.add(TextBlock.builder().text(userMessage).build());

        boolean isBase64 = ImageTypeEnum.BASE64.equals(dto.getImageType());
        for (String image : images) {
            if (image == null || image.isBlank()) {
                continue;
            }
            ImageBlock imageBlock = isBase64
                    ? ImageBlock.builder()
                      .source(new Base64Source(FileConst.MEDIA_TYPE, image))
                      .build()
                    : ImageBlock.builder()
                      .source(new URLSource(image))
                      .build();
            blocks.add(imageBlock);
        }
        return new UserMessage(blocks);
    }

    /**
     * 将 AgentScope AgentEvent 转换为 SSE 事件并发送。
     * <p>
     * 遵循 AgentScope 2.0 官方文档的事件处理模式，使用 instanceof 按事件类型分别处理。
     * 同时通过 {@link ChatSessionRecorder} 累积对话全链路信息，在关键节点异步落库。
     * </p>
     * <p>
     * <b>记录时机</b>：
     * <ul>
     *   <li>{@link ThinkingBlockDeltaEvent} — 累积思考内容</li>
     *   <li>{@link ThinkingBlockEndEvent} — 保存思考过程摘要</li>
     *   <li>{@link ToolCallStartEvent} — 记录工具调用开始时间</li>
     *   <li>{@link ToolCallDeltaEvent} — 累积工具入参 arguments</li>
     *   <li>{@link ToolResultTextDeltaEvent} — 累积工具出参 result</li>
     *   <li>{@link ToolResultEndEvent} — 保存工具调用完整记录（含耗时）</li>
     *   <li>{@link TextBlockDeltaEvent} — 累积最终回复内容</li>
     *   <li>{@link ModelCallEndEvent} — 累积 Token 消耗</li>
     * </ul>
     * </p>
     *
     * @param emitter   SSE 发射器
     * @param sessionId 会话 ID
     * @param event     AgentScope 事件
     * @param recorder  会话级别记录容器
     * @throws IOException SSE 发送异常
     */
    private void forwardAgentEvent(SseEmitter emitter, String sessionId, AgentEvent event,
                                   ChatSessionRecorder recorder) throws IOException {
        // 独立处理 Token 累积（不影响后续 instanceof 转发链）
        if (event instanceof ModelCallEndEvent mce) {
            accumulateTokenUsage(mce, recorder);
        }

        // 按官方文档的 instanceof 模式分别处理各类事件
        if (event instanceof TextBlockDeltaEvent delta) {
            // 流式文本片段：只转发增量文本，构造轻量 JSON（最热点路径，必须轻量）
            if (delta.getDelta() != null) {
                recorder.finalReplyContent.append(delta.getDelta());
            }
            TextBlockDeltaEventBO eventBO = TextBlockDeltaEventBO.builder()
                    .type(AgentEventEnum.TEXT_DELTA.getDesc())
                    .sessionId(sessionId)
                    .delta(delta.getDelta())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof TextBlockEndEvent end) {
            // 文本块完成：标记一次完整文本输出结束
            TextBlockEndEventBO eventBO = TextBlockEndEventBO.builder()
                    .type(AgentEventEnum.TEXT_END.getDesc())
                    .sessionId(sessionId)
                    .blockId(end.getBlockId())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ThinkingBlockStartEvent tb) {
            // 思考过程开始：通知前端开始渲染推理过程
            ThinkingBlockStartEventBO eventBO = ThinkingBlockStartEventBO.builder()
                    .type(AgentEventEnum.THINKING_START.getDesc())
                    .sessionId(sessionId)
                    .replyId(tb.getReplyId())
                    .blockId(tb.getBlockId())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ThinkingBlockDeltaEvent tb) {
            // 思考过程增量：转发增量文本，前端累加渲染推理过程
            if (tb.getDelta() != null) {
                recorder.thinkingContent.append(tb.getDelta());
            }
            ThinkingBlockDeltaEventBO eventBO = ThinkingBlockDeltaEventBO.builder()
                    .type(AgentEventEnum.THINKING_DELTA.getDesc())
                    .sessionId(sessionId)
                    .replyId(tb.getReplyId())
                    .blockId(tb.getBlockId())
                    .delta(tb.getDelta())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ThinkingBlockEndEvent tb) {
            // 思考过程结束：标记一次推理过程完成
            ThinkingBlockEndEventBO eventBO = ThinkingBlockEndEventBO.builder()
                    .type(AgentEventEnum.THINKING_END.getDesc())
                    .sessionId(sessionId)
                    .replyId(tb.getReplyId())
                    .blockId(tb.getBlockId())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

            // 异步保存本次思考过程摘要（一次 ReAct 迭代可能产生多次思考，分别落库）
            if (recorder.thinkingContent.length() > 0) {
                chatRecordService.saveThinkingMessage(sessionId, recorder.thinkingContent.toString());
                recorder.thinkingContent.setLength(0);
            }

        } else if (event instanceof ToolCallStartEvent tc) {
            // 工具调用开始：通知前端正在调用哪个工具
            // 注意：ToolCallStartEvent 不携带入参，入参通过后续 ToolCallDeltaEvent 流式推送
            String toolCallId = tc.getToolCallId();
            if (toolCallId != null) {
                recorder.toolCallStartTimes.put(toolCallId, System.currentTimeMillis());
                recorder.toolCallArguments.put(toolCallId, new StringBuilder());
                recorder.toolCallResults.put(toolCallId, new StringBuilder());
            }
            ToolCallStartEventBO eventBO = ToolCallStartEventBO.builder()
                    .type(AgentEventEnum.TOOL_CALL_START.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(toolCallId)
                    .toolName(tc.getToolCallName())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolCallDeltaEvent tc) {
            // 工具调用入参增量：转发 arguments JSON 片段，前端累加得到完整入参
            if (tc.getToolCallId() != null && tc.getDelta() != null) {
                StringBuilder args = recorder.toolCallArguments.get(tc.getToolCallId());
                if (args != null) {
                    args.append(tc.getDelta());
                }
            }
            ToolCallDeltaEventBO eventBO = ToolCallDeltaEventBO.builder()
                    .type(AgentEventEnum.TOOL_CALL_DELTA.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(tc.getToolCallId())
                    .toolName(tc.getToolCallName())
                    .delta(tc.getDelta())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolCallEndEvent tc) {
            // 工具调用参数构造完成
            // 注意：完整 arguments JSON 需前端通过 ToolCallDeltaEvent 累加获得
            ToolCallEndEventBO eventBO = ToolCallEndEventBO.builder()
                    .type(AgentEventEnum.TOOL_CALL_END.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(tc.getToolCallId())
                    .toolName(tc.getToolCallName())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolResultStartEvent tr) {
            // 工具开始执行
            ToolResultStartEventBO eventBO = ToolResultStartEventBO.builder()
                    .type(AgentEventEnum.TOOL_RESULT_START.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(tr.getToolCallId())
                    .toolName(tr.getToolCallName())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolResultTextDeltaEvent tr) {
            // 工具结果文本增量：转发结果内容片段，前端累加得到完整 result
            if (tr.getToolCallId() != null && tr.getDelta() != null) {
                StringBuilder result = recorder.toolCallResults.get(tr.getToolCallId());
                if (result != null) {
                    result.append(tr.getDelta());
                }
            }
            ToolResultTextDeltaEventBO eventBO = ToolResultTextDeltaEventBO.builder()
                    .type(AgentEventEnum.TOOL_RESULT_TEXT_DELTA.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(tr.getToolCallId())
                    .toolName(tr.getToolCallName())
                    .delta(tr.getDelta())
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

        } else if (event instanceof ToolResultEndEvent tr) {
            // 工具执行完成：转发执行状态（SUCCESS/ERROR等）
            // 注意：完整 result 内容需前端通过 ToolResultTextDeltaEvent 累加获得
            String toolCallId = tr.getToolCallId();
            String state = tr.getState() != null ? tr.getState().name() : "UNKNOWN";
            ToolResultEndEventBO eventBO = ToolResultEndEventBO.builder()
                    .type(AgentEventEnum.TOOL_RESULT_END.getDesc())
                    .sessionId(sessionId)
                    .toolCallId(toolCallId)
                    .toolName(tr.getToolCallName())
                    .state(state)
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));

            // 异步保存工具调用完整记录（入参/出参/状态/耗时）
            saveToolCallRecord(sessionId, tr, state, recorder);

        } else if (event instanceof AgentEndEvent end) {
            // Agent 完全结束（含记忆整合后）：关闭 SSE
            // 注意：AgentEndEvent 在记忆整合之后才触发，此时 SSE 才最终关闭
            log.info("[Chat] 收到 AgentEndEvent，关闭SSE: sessionId={}", sessionId);
            // AgentEndEvent 作为最终兜底，如果前面没有提前关闭，这里关闭
            // 当前实现不提前关闭，等 AgentEndEvent 统一关闭，保证消息完整性

        } else {
            // 其他事件（AgentStartEvent/ModelCallStartEvent/ModelCallEndEvent 等）
            // 这些事件频率低且非热点，可全量序列化
            AgentOtherEventBO eventBO = AgentOtherEventBO.builder()
                    .type(event.getClass().getSimpleName())
                    .sessionId(sessionId)
                    .eventType(event.getType() != null ? event.getType().name() : "UNKNOWN")
                    .build();
            emitter.send(SseEmitter.event().data(toJson(eventBO)));
        }

        log.debug("[SSE] 转发Agent事件: sessionId={}, eventType={}",
                sessionId, event.getClass().getSimpleName());
    }

    /**
     * 从 {@link ModelCallEndEvent} 中累积 Token 消耗到会话记录容器。
     * <p>
     * 一次 ReAct 循环可能触发多次模型调用（每次迭代一次），此处逐次累加，
     * 最终在 doOnComplete 中统一落库。
     * </p>
     *
     * @param event    模型调用结束事件
     * @param recorder 会话记录容器
     */
    private void accumulateTokenUsage(ModelCallEndEvent event, ChatSessionRecorder recorder) {
        try {
            ChatUsage usage = event.getUsage();
            if (usage != null) {
                recorder.totalInputTokens.addAndGet(usage.getInputTokens());
                recorder.totalOutputTokens.addAndGet(usage.getOutputTokens());
            }
        } catch (Exception e) {
            log.debug("[Chat] 获取Token使用量失败: {}", e.getMessage());
        }
    }

    /**
     * 异步保存工具调用完整记录。
     * <p>
     * 从会话记录容器中取出该工具调用的开始时间、累积入参和出参，
     * 计算执行耗时后通过 {@link ChatRecordService} 异步落库。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param tr        工具执行结束事件
     * @param state     执行状态字符串
     * @param recorder  会话记录容器
     */
    private void saveToolCallRecord(String sessionId, ToolResultEndEvent tr, String state,
                                    ChatSessionRecorder recorder) {
        String toolCallId = tr.getToolCallId();
        if (toolCallId == null) {
            return;
        }
        Long startTime = recorder.toolCallStartTimes.remove(toolCallId);
        StringBuilder argsBuilder = recorder.toolCallArguments.remove(toolCallId);
        StringBuilder resultBuilder = recorder.toolCallResults.remove(toolCallId);
        String arguments = argsBuilder != null ? argsBuilder.toString() : null;
        String result = resultBuilder != null ? resultBuilder.toString() : null;
        long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0L;

        chatRecordService.saveToolCall(sessionId, toolCallId, tr.getToolCallName(),
                arguments, result, state, durationMs);
    }

    // ==================== SSE 事件发送工具方法 ====================

    /**
     * 发送 SSE 事件（JSON 格式）。
     *
     * @param emitter   SSE 发射器
     * @param type      事件类型
     * @param sessionId 会话 ID
     * @param fields    额外字段
     * @throws IOException SSE 发送异常
     */
    private void sendEvent(SseEmitter emitter, String type, String sessionId, Map<String, Object> fields) throws IOException {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("sessionId", sessionId);
        event.put("timestamp", System.currentTimeMillis());
        if (fields != null) {
            event.putAll(fields);
        }

        String json = toJson(event);
        emitter.send(SseEmitter.event().data(json));
        log.debug("[SSE] 发送事件: type={}, sessionId={}", type, sessionId);
    }

    /**
     * 发送 SSE 结束标记 [DONE]。
     *
     * @param emitter SSE 发射器
     */
    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException e) {
            log.warn("[SSE] 发送 [DONE] 失败: {}", e.getMessage());
        }
    }

    /**
     * 对象转 JSON 字符串（使用 Jackson，性能远优于 org.json）。
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("[Chat] JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    // ==================== 会话级别记录容器 ====================

    /**
     * 会话级别对话记录容器。
     * <p>
     * 在一次 {@link #streamEvents} 调用周期内累积对话全链路信息，
     * 包括 LLM 思考内容、最终回复、工具调用入参/出参/开始时间、Token 消耗。
     * 所有可变字段均使用线程安全容器（{@link StringBuilder} 在单线程 Reactive 流中安全，
     * {@link ConcurrentHashMap} 和 {@link AtomicLong} 保证多事件线程安全）。
     * </p>
     *
     * @author zqs
     * @since 2.0.0
     */
    private static final class ChatSessionRecorder {

        /**
         * 会话开始时间（用于计算总耗时）
         */
        private final long sessionStartTime = System.currentTimeMillis();

        /**
         * 当前思考块内容累积器（ThinkingBlockDeltaEvent 累加，ThinkingBlockEndEvent 落库后清空）
         */
        private final StringBuilder thinkingContent = new StringBuilder();

        /**
         * 最终回复内容累积器（TextBlockDeltaEvent 累加）
         */
        private final StringBuilder finalReplyContent = new StringBuilder();

        /**
         * 工具调用开始时间映射：toolCallId → 开始时间戳（毫秒）
         */
        private final Map<String, Long> toolCallStartTimes = new ConcurrentHashMap<>();

        /**
         * 工具调用入参累积映射：toolCallId → arguments JSON 片段累积器
         */
        private final Map<String, StringBuilder> toolCallArguments = new ConcurrentHashMap<>();

        /**
         * 工具调用出参累积映射：toolCallId → result 文本累积器
         */
        private final Map<String, StringBuilder> toolCallResults = new ConcurrentHashMap<>();

        /**
         * 累积输入 Token 总数
         */
        private final AtomicLong totalInputTokens = new AtomicLong(0);

        /**
         * 累积输出 Token 总数
         */
        private final AtomicLong totalOutputTokens = new AtomicLong(0);
    }
}
