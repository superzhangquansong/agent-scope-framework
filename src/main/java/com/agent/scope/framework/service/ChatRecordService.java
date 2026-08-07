package com.agent.scope.framework.service;

import com.agent.scope.framework.entity.ChatMessageRecord;
import com.agent.scope.framework.entity.ModelCallRecord;
import com.agent.scope.framework.entity.TokenUsageRecord;
import com.agent.scope.framework.entity.ToolCallRecord;
import com.agent.scope.framework.mapper.ChatMessageRecordMapper;
import com.agent.scope.framework.mapper.ModelCallRecordMapper;
import com.agent.scope.framework.mapper.TokenUsageRecordMapper;
import com.agent.scope.framework.mapper.ToolCallRecordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
@EnableScheduling
public class ChatRecordService {

    /**
     * Jackson ObjectMapper（线程安全，静态复用）。
     * <p>用于补偿记录的 JSON 序列化与反序列化。</p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 补偿文件目录（按天滚动）
     */
    private static final String COMPENSATION_DIR = "/tmp/agentscope-workspace/compensation";

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
    private final ModelCallRecordMapper modelCallRecordMapper;

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
            compensateToFile("saveUserMessage", buildCompensationData(
                    "sessionId", sessionId,
                    "userId", userId,
                    "houseId", houseId,
                    "content", content,
                    "timestamp", System.currentTimeMillis()));
        }
    }

    /**
     * 异步保存 LLM 思考过程摘要。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param content   思考过程内容摘要
     */
    @Async
    public void saveThinkingMessage(String sessionId, String userId, String content) {
        try {
            ChatMessageRecord record = ChatMessageRecord.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .role(ROLE_THINKING)
                    .content(content)
                    .messageTimestamp(System.currentTimeMillis())
                    .createTime(LocalDateTime.now())
                    .build();
            chatMessageRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[ChatRecord] 保存思考过程失败: sessionId={}, error={}", sessionId, e.getMessage());
            compensateToFile("saveThinkingMessage", buildCompensationData(
                    "sessionId", sessionId,
                    "userId", userId,
                    "content", content,
                    "timestamp", System.currentTimeMillis()));
        }
    }

    /**
     * 异步保存 LLM 最终回复。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param content   最终回复内容
     */
    @Async
    public void saveAssistantMessage(String sessionId, String userId, String content) {
        try {
            ChatMessageRecord record = ChatMessageRecord.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .role(ROLE_ASSISTANT)
                    .content(content)
                    .messageTimestamp(System.currentTimeMillis())
                    .createTime(LocalDateTime.now())
                    .build();
            chatMessageRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[ChatRecord] 保存最终回复失败: sessionId={}, error={}", sessionId, e.getMessage());
            compensateToFile("saveAssistantMessage", buildCompensationData(
                    "sessionId", sessionId,
                    "userId", userId,
                    "content", content,
                    "timestamp", System.currentTimeMillis()));
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
            compensateToFile("saveToolCall", buildCompensationData(
                    "sessionId", sessionId,
                    "toolCallId", toolCallId,
                    "toolName", toolName,
                    "arguments", arguments,
                    "result", result,
                    "state", state,
                    "durationMs", durationMs,
                    "timestamp", System.currentTimeMillis()));
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
            compensateToFile("saveTokenUsage", buildCompensationData(
                    "sessionId", sessionId,
                    "inputTokens", inputTokens,
                    "outputTokens", outputTokens,
                    "modelName", modelName,
                    "timestamp", System.currentTimeMillis()));
        }
    }

    /**
     * 异步保存单次模型调用记录。
     * <p>
     * 记录 ReAct 循环中每一次 LLM 调用的输出内容、Token 消耗与耗时，
     * 通过 {@code replyId} 与事件流关联，便于全链路调用链分析。
     * </p>
     *
     * @param sessionId      会话 ID
     * @param replyId        回复 ID（关联 ModelCallStart/End 事件）
     * @param outputContent  模型调用输出内容（累积的文本/思考/工具调用片段）
     * @param inputTokens    输入 Token 数
     * @param outputTokens   输出 Token 数
     * @param cachedTokens   缓存命中 Token 数
     * @param modelName      模型名称
     * @param durationMs     调用耗时（毫秒）
     */
    @Async
    public void saveModelCall(String sessionId, String replyId, String outputContent,
                              int inputTokens, int outputTokens, int cachedTokens,
                              String modelName, long durationMs) {
        try {
            ModelCallRecord record = ModelCallRecord.builder()
                    .sessionId(sessionId)
                    .replyId(replyId)
                    .outputContent(outputContent)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(inputTokens + outputTokens)
                    .cachedTokens(cachedTokens)
                    .modelName(modelName)
                    .durationMs(durationMs)
                    .createTime(LocalDateTime.now())
                    .build();
            modelCallRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[ChatRecord] 保存模型调用记录失败: sessionId={}, replyId={}, error={}",
                    sessionId, replyId, e.getMessage());
            compensateToFile("saveModelCall", buildCompensationData(
                    "sessionId", sessionId,
                    "replyId", replyId,
                    "outputContent", outputContent,
                    "inputTokens", inputTokens,
                    "outputTokens", outputTokens,
                    "cachedTokens", cachedTokens,
                    "modelName", modelName,
                    "durationMs", durationMs,
                    "timestamp", System.currentTimeMillis()));
        }
    }

    // ==================== 失败补偿机制（P1-10）====================

    /**
     * 将失败的操作和数据以 JSON 格式写入本地补偿文件（按天滚动，追加写入）。
     * <p>
     * 补偿文件路径：{@link #COMPENSATION_DIR}/compensation-{date}.log
     * 每行一条 JSON 记录：{"operation":"...","data":{...},"timestamp":...}
     * </p>
     *
     * @param operation 操作名称（如 saveUserMessage）
     * @param data      操作参数的 JSON 字符串
     */
    private void compensateToFile(String operation, String data) {
        try {
            Path dir = Paths.get(COMPENSATION_DIR);
            Files.createDirectories(dir);
            String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path file = dir.resolve("compensation-" + date + ".log");
            // 构建补偿记录：{"operation":"...","data":{...},"timestamp":...}
            ObjectNode entry = OBJECT_MAPPER.createObjectNode();
            entry.put("operation", operation);
            entry.set("data", OBJECT_MAPPER.readTree(data));
            entry.put("timestamp", System.currentTimeMillis());
            // 追加写入（按天滚动文件）
            try (FileWriter writer = new FileWriter(file.toFile(), StandardCharsets.UTF_8, true)) {
                writer.write(OBJECT_MAPPER.writeValueAsString(entry));
                writer.write(System.lineSeparator());
            }
            log.info("[ChatRecord] 失败操作已写入补偿文件: operation={}, file={}", operation, file.getFileName());
        } catch (Exception e) {
            log.error("[ChatRecord] 写入补偿文件失败: operation={}", operation, e);
        }
    }

    /**
     * 构建补偿数据 JSON 字符串（可变参数，键值对交替）。
     *
     * @param kv 键值对（String key, Object value 交替）
     * @return JSON 字符串
     */
    private String buildCompensationData(Object... kv) {
        try {
            Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < kv.length; i += 2) {
                map.put((String) kv[i], kv[i + 1]);
            }
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.error("[ChatRecord] 构建补偿数据失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 定时扫描补偿文件并重试落库（每 5 分钟执行一次，P1-10）。
     * <p>重试成功的记录从补偿文件中删除，失败的记录保留等待下次重试。</p>
     */
    @Scheduled(fixedDelay = 300000)
    public void retryCompensations() {
        File dir = new File(COMPENSATION_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.startsWith("compensation-") && name.endsWith(".log"));
        if (files == null || files.length == 0) {
            return;
        }
        log.info("[ChatRecord] 开始扫描补偿文件重试落库: fileCount={}", files.length);
        for (File file : files) {
            retryCompensationFile(file);
        }
    }

    /**
     * 重试单个补偿文件中的所有记录。
     *
     * @param file 补偿文件
     */
    private void retryCompensationFile(File file) {
        List<String> remainingLines = new ArrayList<>();
        int successCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode node = OBJECT_MAPPER.readTree(line);
                    String operation = node.get("operation").asText();
                    JsonNode data = node.get("data");
                    if (retryOperation(operation, data)) {
                        successCount++;
                    } else {
                        // 重试失败，保留记录等待下次重试
                        remainingLines.add(line);
                    }
                } catch (Exception e) {
                    log.warn("[ChatRecord] 解析补偿记录失败，保留: file={}, error={}", file.getName(), e.getMessage());
                    remainingLines.add(line);
                }
            }
        } catch (IOException e) {
            log.warn("[ChatRecord] 读取补偿文件失败: {}", file.getName(), e);
            return;
        }
        // 重写文件，仅保留重试失败的记录
        rewriteCompensationFile(file, remainingLines);
        log.info("[ChatRecord] 补偿文件处理完成: file={}, success={}, remaining={}",
                file.getName(), successCount, remainingLines.size());
    }

    /**
     * 重写补偿文件，仅保留重试失败的记录；全部成功则删除文件。
     *
     * @param file  补偿文件
     * @param lines 保留的记录行
     */
    private void rewriteCompensationFile(File file, List<String> lines) {
        try {
            if (lines.isEmpty()) {
                // 所有记录重试成功，删除补偿文件
                if (file.delete()) {
                    log.info("[ChatRecord] 补偿文件所有记录已重试成功，已删除: {}", file.getName());
                }
            } else {
                // 重写文件，仅保留失败的记录
                try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, false)) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.write(System.lineSeparator());
                    }
                }
            }
        } catch (IOException e) {
            log.error("[ChatRecord] 重写补偿文件失败: {}", file.getName(), e);
        }
    }

    /**
     * 重试单个补偿操作（直接调用 Mapper，不触发 @Async 与补偿，避免循环）。
     *
     * @param operation 操作名称
     * @param data      操作参数 JSON
     * @return true=重试成功，false=重试失败
     */
    private boolean retryOperation(String operation, JsonNode data) {
        try {
            switch (operation) {
                case "saveUserMessage" -> chatMessageRecordMapper.insert(ChatMessageRecord.builder()
                        .sessionId(data.get("sessionId").asText())
                        .userId(data.hasNonNull("userId") ? data.get("userId").asText() : null)
                        .houseId(data.hasNonNull("houseId") ? data.get("houseId").asText() : null)
                        .role(ROLE_USER)
                        .content(data.get("content").asText())
                        .messageTimestamp(data.hasNonNull("timestamp") ? data.get("timestamp").asLong() : System.currentTimeMillis())
                        .createTime(LocalDateTime.now())
                        .build());
                case "saveThinkingMessage" -> chatMessageRecordMapper.insert(ChatMessageRecord.builder()
                        .sessionId(data.get("sessionId").asText())
                        .userId(data.hasNonNull("userId") ? data.get("userId").asText() : null)
                        .role(ROLE_THINKING)
                        .content(data.get("content").asText())
                        .messageTimestamp(data.hasNonNull("timestamp") ? data.get("timestamp").asLong() : System.currentTimeMillis())
                        .createTime(LocalDateTime.now())
                        .build());
                case "saveAssistantMessage" -> chatMessageRecordMapper.insert(ChatMessageRecord.builder()
                        .sessionId(data.get("sessionId").asText())
                        .userId(data.hasNonNull("userId") ? data.get("userId").asText() : null)
                        .role(ROLE_ASSISTANT)
                        .content(data.get("content").asText())
                        .messageTimestamp(data.hasNonNull("timestamp") ? data.get("timestamp").asLong() : System.currentTimeMillis())
                        .createTime(LocalDateTime.now())
                        .build());
                case "saveToolCall" -> toolCallRecordMapper.insert(ToolCallRecord.builder()
                        .sessionId(data.get("sessionId").asText())
                        .toolCallId(data.get("toolCallId").asText())
                        .toolName(data.get("toolName").asText())
                        .arguments(data.hasNonNull("arguments") ? data.get("arguments").asText() : null)
                        .result(data.hasNonNull("result") ? data.get("result").asText() : null)
                        .state(data.get("state").asText())
                        .durationMs(data.get("durationMs").asLong())
                        .createTime(LocalDateTime.now())
                        .build());
                case "saveTokenUsage" -> {
                    int inputTokens = data.get("inputTokens").asInt();
                    int outputTokens = data.get("outputTokens").asInt();
                    tokenUsageRecordMapper.insert(TokenUsageRecord.builder()
                            .sessionId(data.get("sessionId").asText())
                            .inputTokens(inputTokens)
                            .outputTokens(outputTokens)
                            .totalTokens(inputTokens + outputTokens)
                            .modelName(data.get("modelName").asText())
                            .createTime(LocalDateTime.now())
                            .build());
                }
                case "saveModelCall" -> {
                    int inputTokens = data.get("inputTokens").asInt();
                    int outputTokens = data.get("outputTokens").asInt();
                    modelCallRecordMapper.insert(ModelCallRecord.builder()
                            .sessionId(data.get("sessionId").asText())
                            .replyId(data.get("replyId").asText())
                            .outputContent(data.get("outputContent").asText())
                            .inputTokens(inputTokens)
                            .outputTokens(outputTokens)
                            .totalTokens(inputTokens + outputTokens)
                            .cachedTokens(data.get("cachedTokens").asInt())
                            .modelName(data.get("modelName").asText())
                            .durationMs(data.get("durationMs").asLong())
                            .createTime(LocalDateTime.now())
                            .build());
                }
                default -> log.warn("[ChatRecord] 未知补偿操作，跳过: {}", operation);
            }
            log.info("[ChatRecord] 补偿重试成功: operation={}", operation);
            return true;
        } catch (Exception e) {
            log.warn("[ChatRecord] 补偿重试失败: operation={}, error={}", operation, e.getMessage());
            return false;
        }
    }
}
