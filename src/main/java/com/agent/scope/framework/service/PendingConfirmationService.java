package com.agent.scope.framework.service;

import com.agent.scope.framework.bo.event.PermissionAskEventBO.ToolCallInfo;
import com.agent.scope.framework.constant.BusinessConst;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.agent.scope.framework.constant.BusinessConst.PENDING_CONFIRM_KEY_PREFIX;
import static com.agent.scope.framework.constant.BusinessConst.PENDING_CONFIRM_TTL_MINUTES;

/**
 * 待确认权限请求管理服务（HITL 人机交互）。
 * <p>
 * 集中管理 HITL 权限确认的 Redis 存取操作，消除 {@link ChatService} 与
 * {@code RequireUserConfirmHandler} 之间的重复代码。
 * </p>
 * <p>
 * 使用 {@link StringRedisTemplate}（Key/Value 均为 String 序列化），配合 {@link #OBJECT_MAPPER}
 * 手动序列化/反序列化 JSON，绕开 {@code GenericJackson2JsonRedisSerializer} 的类型 ID 约束问题。
 * </p>
 * <p>
 * Redis 中的待确认数据与 AgentScope RedisAgentStateStore 的 ASKING 状态同生命周期，
 * 服务重启后不丢失，确保"继续"等自然语言恢复消息能正确携带 {@code ConfirmResult} 元数据。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingConfirmationService {

    /**
     * Jackson ObjectMapper（线程安全，静态复用，避免每次创建）。
     * <p>用于 Redis 存取待确认权限请求时的 JSON 手动序列化/反序列化，
     * 绕开 {@code GenericJackson2JsonRedisSerializer} 的 {@code activateDefaultTyping(NON_FINAL)}
     * 类型 ID 要求（该要求会导致 {@code List<ToolCallInfo>} 反序列化失败）。</p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Redis 中待确认权限请求 JSON 的 TypeReference（用于 Jackson 反序列化）。
     */
    private static final TypeReference<List<ToolCallInfo>> PENDING_CONFIRM_TYPE_REF =
            new TypeReference<>() {};

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 将待确认的工具调用缓存到 Redis。
     * <p>
     * 当 {@code RequireUserConfirmEvent} 触发时调用。将 ToolUseBlock 列表转换为
     * {@link ToolCallInfo} 列表，使用 {@link #OBJECT_MAPPER} 序列化为 JSON 字符串
     * 存入 Redis，Key 为 {@code pending_confirm:{sessionId}}，TTL 30 分钟。
     * </p>
     * <p>关键修复：RequireUserConfirmEvent 中的 ToolUseBlock.getInput() 可能返回 null，
     * 此时从 recorder 的 toolCallArguments（ToolCallDeltaEvent 累积的入参 JSON）回退。</p>
     *
     * @param sessionId         会话 ID
     * @param toolCalls         待确认的 ToolUseBlock 列表（来自 RequireUserConfirmEvent）
     * @param fallbackArguments recorder 累积的入参回退来源（toolCallId → arguments JSON 片段）
     */
    public void cachePendingConfirmations(String sessionId, List<ToolUseBlock> toolCalls,
                                          Map<String, StringBuilder> fallbackArguments) {
        try {
            List<ToolCallInfo> infos = toolCalls.stream()
                    .map(tc -> {
                        Map<String, Object> input = tc.getInput();
                        // 关键修复：RequireUserConfirmEvent 中的 ToolUseBlock.getInput() 可能返回 null
                        // 此时从 recorder 的 toolCallArguments（ToolCallDeltaEvent 累积的入参 JSON）回退
                        if (input == null || input.isEmpty()) {
                            input = resolveFallbackInput(tc, fallbackArguments);
                        }
                        // 关键修复：ToolExecutor.executeCore() 使用 toolCall.getContent() 做参数校验，
                        // 而非 toolCall.getInput()。若 content 为 null，校验报
                        // "Schema validation error: argument 'content' is null"，工具执行失败。
                        // 此处从 ToolUseBlock.getContent() 获取原始 JSON 字符串；
                        // 若为 null（如 input 也是回退解析得到），则将 input 序列化为 JSON 作为 content。
                        String content = resolveContent(tc, input, fallbackArguments);
                        // AgentScope 2.0.0 的 ToolUseBlock 不存在 getSuggestedRules() 方法
                        // （官方文档描述的 API 与 2.0.0 实际发布版本不一致），此处传 null
                        return ToolCallInfo.builder()
                                .toolCallId(tc.getId())
                                .toolName(tc.getName())
                                .input(input)
                                .content(content)
                                .suggestedRules(null)
                                .build();
                    })
                    .toList();
            String key = PENDING_CONFIRM_KEY_PREFIX + sessionId;
            String json = OBJECT_MAPPER.writeValueAsString(infos);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofMinutes(PENDING_CONFIRM_TTL_MINUTES));
            log.info("[PendingConfirm] 待确认权限请求已缓存到 Redis: sessionId={}, toolCount={}, inputs={}",
                    sessionId, infos.size(),
                    infos.stream().map(i -> i.getToolCallId() + ":" + (i.getInput() != null ? "有入参" : "无入参")).toList());
        } catch (Exception e) {
            log.error("[PendingConfirm] 缓存待确认权限请求失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 从 Redis 加载待确认的工具调用信息。
     * <p>读取 Key {@code pending_confirm:{sessionId}} 的 JSON 字符串，使用 {@link #OBJECT_MAPPER}
     * 反序列化为 {@link ToolCallInfo} 列表。Key 不存在或过期时返回空列表。</p>
     *
     * @param sessionId 会话 ID
     * @return 待确认工具调用信息列表（可能为空，不会为 null）
     */
    public List<ToolCallInfo> loadPendingConfirmations(String sessionId) {
        try {
            String key = PENDING_CONFIRM_KEY_PREFIX + sessionId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return OBJECT_MAPPER.readValue(json, PENDING_CONFIRM_TYPE_REF);
        } catch (Exception e) {
            log.error("[PendingConfirm] 加载待确认权限请求失败: sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 清除 Redis 中的待确认权限请求。
     * <p>在以下场景调用：
     * <ul>
     *   <li>用户通过自然语言（继续/确认）恢复后</li>
     *   <li>用户通过 /api/chat/confirm API 恢复后</li>
     *   <li>用户拒绝执行后</li>
     * </ul>
     * </p>
     *
     * @param sessionId 会话 ID
     */
    public void clearPendingConfirmations(String sessionId) {
        try {
            stringRedisTemplate.delete(PENDING_CONFIRM_KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("[PendingConfirm] 清除待确认权限请求失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 解析工具入参的原始 JSON 字符串（content 字段）。
     * <p>AgentScope 2.0 的 {@code ToolExecutor.executeCore()} 使用
     * {@code toolCall.getContent()} 进行 JSON Schema 参数校验。
     * 若 content 为 null，校验会抛出
     * "Schema validation error: argument 'content' is null"，导致工具无法执行。</p>
     *
     * <p>解析优先级：</p>
     * <ol>
     *   <li>{@code tc.getContent()} —— LLM 生成工具调用时框架已设置的原始 JSON 字符串</li>
     *   <li>将已解析的 {@code input} Map 序列化为 JSON 字符串作为回退</li>
     *   <li>回退到 {@code fallbackArguments} 中累积的原始 JSON 片段</li>
     *   <li>以上均不可用时返回 "{}"（空 JSON 对象）</li>
     * </ol>
     *
     * @param tc                工具调用块
     * @param input             已解析的入参 Map（可能来自 tc.getInput() 或 fallback）
     * @param fallbackArguments recorder 累积的入参回退来源
     * @return 工具入参的 JSON 字符串，永不为 null
     */
    private String resolveContent(ToolUseBlock tc, Map<String, Object> input,
                                   Map<String, StringBuilder> fallbackArguments) {
        // 优先使用 ToolUseBlock 自带的 content（LLM 生成时已设置）
        String content = tc.getContent();
        if (content != null && !content.isBlank()) {
            return content;
        }

        // 回退1：将 input Map 序列化为 JSON
        if (input != null && !input.isEmpty()) {
            try {
                return OBJECT_MAPPER.writeValueAsString(input);
            } catch (Exception e) {
                log.warn("[PendingConfirm] 序列化 input 为 content 失败: toolCallId={}, error={}",
                        tc.getId(), e.getMessage());
            }
        }

        // 回退2：从 fallbackArguments 获取原始 JSON 片段
        if (fallbackArguments != null) {
            StringBuilder argsBuilder = fallbackArguments.get(tc.getId());
            if (argsBuilder != null && argsBuilder.length() > 0) {
                return argsBuilder.toString();
            }
        }

        // 兜底：空 JSON 对象（与 ToolCallsAccumulator.build() 的行为一致）
        log.warn("[PendingConfirm] content 和 input 均不可用，使用空 JSON: toolCallId={}, toolName={}",
                tc.getId(), tc.getName());
        return "{}";
    }

    /**
     * 从 fallbackArguments 回退解析工具入参。
     * <p>当 ToolUseBlock.getInput() 返回 null 或空 Map 时，从 recorder 累积的
     * ToolCallDeltaEvent 入参 JSON 片段中解析出入参 Map。</p>
     *
     * @param tc                工具调用块
     * @param fallbackArguments recorder 累积的入参回退来源
     * @return 解析出的入参 Map，解析失败或无回退数据时返回 null
     */
    private Map<String, Object> resolveFallbackInput(ToolUseBlock tc,
                                                      Map<String, StringBuilder> fallbackArguments) {
        StringBuilder argsBuilder = fallbackArguments != null ? fallbackArguments.get(tc.getId()) : null;
        if (argsBuilder != null && argsBuilder.length() > 0) {
            try {
                Map<String, Object> input = OBJECT_MAPPER.readValue(argsBuilder.toString(),
                        new TypeReference<Map<String, Object>>() {});
                log.info("[PendingConfirm] ToolUseBlock.input 为空，从 recorder 回退入参: toolCallId={}, input={}",
                        tc.getId(), input);
                return input;
            } catch (Exception e) {
                log.warn("[PendingConfirm] 解析回退入参失败: toolCallId={}, args={}",
                        tc.getId(), argsBuilder, e);
            }
        } else {
            log.warn("[PendingConfirm] ToolUseBlock.input 为空且无回退入参: toolCallId={}, toolName={}",
                    tc.getId(), tc.getName());
        }
        return null;
    }
}
