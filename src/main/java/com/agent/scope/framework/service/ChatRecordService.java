package com.agent.scope.framework.service;

import com.agent.scope.framework.entity.ChatMessageRecord;
import com.agent.scope.framework.entity.TokenUsageRecord;
import com.agent.scope.framework.entity.ToolCallRecord;
import com.agent.scope.framework.mapper.ChatMessageRecordMapper;
import com.agent.scope.framework.mapper.TokenUsageRecordMapper;
import com.agent.scope.framework.mapper.ToolCallRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 对话记录异步保存服务。
 * <p>
 * 负责将对话全链路信息（用户消息、LLM 思考过程、工具调用、最终回复、Token 消耗）
 * 异步落库到 MySQL，所有方法均标注 {@link Async}，在独立线程池中执行，
 * 不会阻塞 SSE 流式推送主流程。
 * </p>
 * <p>
 * <b>容错策略</b>：每个保存方法内部捕获所有异常并降级为日志输出，
 * 确保记录失败不会影响 Agent 主流程。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRecordService {

    /**
     * 消息角色：用户输入
     */
    private static final String ROLE_USER = "user";

    /**
     * 消息角色：LLM 思考过程
     */
    private static final String ROLE_THINKING = "thinking";

    /**
     * 消息角色：LLM 最终回复
     */
    private static final String ROLE_ASSISTANT = "assistant";

    private final ChatMessageRecordMapper chatMessageRecordMapper;
    private final ToolCallRecordMapper toolCallRecordMapper;
    private final TokenUsageRecordMapper tokenUsageRecordMapper;

    /**
     * 异步保存用户输入消息。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param houseId   家庭 ID（可为空）
     * @param content   用户消息原文
     */
    @Async
    public void saveUserMessage(String sessionId, String userId, String houseId, String content) {
        try {
            ChatMessageRecord record = ChatMessageRecord.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .houseId(houseId)
                    .role(ROLE_USER)
                    .content(content)
                    .messageTimestamp(System.currentTimeMillis())
                    .createTime(LocalDateTime.now())
                    .build();
            chatMessageRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[ChatRecord] 保存用户消息失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 异步保存 LLM 思考过程摘要。
     *
     * @param sessionId 会话 ID
     * @param content   思考过程内容摘要
     */
    @Async
    public void saveThinkingMessage(String sessionId, String content) {
        try {
            ChatMessageRecord record = ChatMessageRecord.builder()
                    .sessionId(sessionId)
                    .role(ROLE_THINKING)
                    .content(content)
                    .messageTimestamp(System.currentTimeMillis())
                    .createTime(LocalDateTime.now())
                    .build();
            chatMessageRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[ChatRecord] 保存思考过程失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 异步保存 LLM 最终回复。
     *
     * @param sessionId 会话 ID
     * @param content   最终回复内容
     */
    @Async
    public void saveAssistantMessage(String sessionId, String content) {
        try {
            ChatMessageRecord record = ChatMessageRecord.builder()
                    .sessionId(sessionId)
                    .role(ROLE_ASSISTANT)
                    .content(content)
                    .messageTimestamp(System.currentTimeMillis())
                    .createTime(LocalDateTime.now())
                    .build();
            chatMessageRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[ChatRecord] 保存最终回复失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 异步保存工具调用记录。
     *
     * @param sessionId   会话 ID
     * @param toolCallId  工具调用 ID
     * @param toolName    工具名称
     * @param arguments   工具入参（JSON 字符串）
     * @param result      工具出参（JSON 字符串）
     * @param state       执行状态（SUCCESS / ERROR / UNKNOWN）
     * @param durationMs  执行耗时（毫秒）
     */
    @Async
    public void saveToolCall(String sessionId, String toolCallId, String toolName,
                             String arguments, String result, String state, long durationMs) {
        try {
            ToolCallRecord record = ToolCallRecord.builder()
                    .sessionId(sessionId)
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .arguments(arguments)
                    .result(result)
                    .state(state)
                    .durationMs(durationMs)
                    .createTime(LocalDateTime.now())
                    .build();
            toolCallRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[ChatRecord] 保存工具调用记录失败: sessionId={}, toolCallId={}, error={}",
                    sessionId, toolCallId, e.getMessage());
        }
    }

    /**
     * 异步保存 Token 消耗记录。
     *
     * @param sessionId     会话 ID
     * @param inputTokens   输入 Token 数
     * @param outputTokens  输出 Token 数
     * @param modelName     模型名称
     */
    @Async
    public void saveTokenUsage(String sessionId, long inputTokens, long outputTokens, String modelName) {
        try {
            TokenUsageRecord record = TokenUsageRecord.builder()
                    .sessionId(sessionId)
                    .inputTokens((int) inputTokens)
                    .outputTokens((int) outputTokens)
                    .totalTokens((int) (inputTokens + outputTokens))
                    .modelName(modelName)
                    .createTime(LocalDateTime.now())
                    .build();
            tokenUsageRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[ChatRecord] 保存Token消耗失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
}
