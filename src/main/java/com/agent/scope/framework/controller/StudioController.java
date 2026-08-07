package com.agent.scope.framework.controller;

import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.entity.ChatMessageRecord;
import com.agent.scope.framework.entity.ModelCallRecord;
import com.agent.scope.framework.entity.TokenUsageRecord;
import com.agent.scope.framework.entity.ToolCallRecord;
import com.agent.scope.framework.mapper.ChatMessageRecordMapper;
import com.agent.scope.framework.mapper.ModelCallRecordMapper;
import com.agent.scope.framework.mapper.TokenUsageRecordMapper;
import com.agent.scope.framework.mapper.ToolCallRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AgentScope Studio 可视化调试控制器（特性四十）。
 * <p>
 * 通过 HTTP 端点暴露已落库的调试数据，供 AgentScope Studio 可视化界面读取与回放，
 * 支持对话消息回放、工具调用链路追踪、模型调用明细查看及 Token 消耗统计分析。
 * </p>
 * <p>
 * <b>接口列表</b>：
 * <ul>
 *   <li>GET /api/studio/sessions/{sessionId}/messages - 查询会话消息记录（限 50 条）</li>
 *   <li>GET /api/studio/sessions/{sessionId}/tool-calls - 查询会话工具调用记录（限 50 条）</li>
 *   <li>GET /api/studio/sessions/{sessionId}/model-calls - 查询会话模型调用记录（限 50 条）</li>
 *   <li>GET /api/studio/sessions/{sessionId}/token-usage - 查询会话 Token 消耗明细（限 20 条）</li>
 *   <li>GET /api/studio/token-summary - 聚合统计全部会话 Token 消耗</li>
 * </ul>
 * </p>
 * <p>
 * 仅在配置 {@code scope.agentscope.advanced.studio-enabled=true} 时生效，
 * 生产环境可按需关闭以避免暴露内部调试数据。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/studio")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "studio-enabled", havingValue = "true")
public class StudioController {

    /**
     * 对话消息记录 Mapper
     */
    private final ChatMessageRecordMapper chatMessageRecordMapper;

    /**
     * 工具调用记录 Mapper
     */
    private final ToolCallRecordMapper toolCallRecordMapper;

    /**
     * 模型调用记录 Mapper
     */
    private final ModelCallRecordMapper modelCallRecordMapper;

    /**
     * Token 消耗记录 Mapper
     */
    private final TokenUsageRecordMapper tokenUsageRecordMapper;

    /**
     * 查询指定会话的对话消息记录。
     * <p>
     * 按 sessionId 过滤，按主键 id 倒序返回最近 50 条消息，包括用户输入、
     * LLM 思考过程摘要与最终回复，供 Studio 进行对话回放。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 包含状态码与消息列表的响应 Map
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> listMessages(@PathVariable String sessionId) {
        log.info("[Studio] 查询会话消息记录: sessionId={}", sessionId);

        LambdaQueryWrapper<ChatMessageRecord> wrapper = new LambdaQueryWrapper<ChatMessageRecord>()
                .eq(ChatMessageRecord::getSessionId, sessionId)
                .orderByDesc(ChatMessageRecord::getId)
                .last("LIMIT 50");
        List<ChatMessageRecord> list = chatMessageRecordMapper.selectList(wrapper);

        return Map.of(
                BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK,
                BusinessConst.RESPONSE_KEY_DATA, list
        );
    }

    /**
     * 查询指定会话的工具调用记录。
     * <p>
     * 按 sessionId 过滤，按主键 id 倒序返回最近 50 条工具调用记录，
     * 包含工具名、入参、出参、执行状态与耗时，供 Studio 进行调用链路追踪与可视化。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 包含状态码与工具调用列表的响应 Map
     */
    @GetMapping("/sessions/{sessionId}/tool-calls")
    public Map<String, Object> listToolCalls(@PathVariable String sessionId) {
        log.info("[Studio] 查询会话工具调用记录: sessionId={}", sessionId);

        LambdaQueryWrapper<ToolCallRecord> wrapper = new LambdaQueryWrapper<ToolCallRecord>()
                .eq(ToolCallRecord::getSessionId, sessionId)
                .orderByDesc(ToolCallRecord::getId)
                .last("LIMIT 50");
        List<ToolCallRecord> list = toolCallRecordMapper.selectList(wrapper);

        return Map.of(
                BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK,
                BusinessConst.RESPONSE_KEY_DATA, list
        );
    }

    /**
     * 查询指定会话的模型调用记录。
     * <p>
     * 按 sessionId 过滤，按主键 id 倒序返回最近 50 条模型调用记录，
     * 包含回复 ID、输出内容、Token 消耗、模型名称与耗时，供 Studio 进行
     * 全链路问题定位与成本核算。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 包含状态码与模型调用列表的响应 Map
     */
    @GetMapping("/sessions/{sessionId}/model-calls")
    public Map<String, Object> listModelCalls(@PathVariable String sessionId) {
        log.info("[Studio] 查询会话模型调用记录: sessionId={}", sessionId);

        LambdaQueryWrapper<ModelCallRecord> wrapper = new LambdaQueryWrapper<ModelCallRecord>()
                .eq(ModelCallRecord::getSessionId, sessionId)
                .orderByDesc(ModelCallRecord::getId)
                .last("LIMIT 50");
        List<ModelCallRecord> list = modelCallRecordMapper.selectList(wrapper);

        return Map.of(
                BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK,
                BusinessConst.RESPONSE_KEY_DATA, list
        );
    }

    /**
     * 查询指定会话的 Token 消耗明细。
     * <p>
     * 按 sessionId 过滤，按主键 id 倒序返回最近 20 条 Token 消耗记录，
     * 包含输入 Token、输出 Token、总 Token 数及模型名称，供 Studio 进行
     * 单会话维度的 Token 用量分析。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 包含状态码与 Token 消耗列表的响应 Map
     */
    @GetMapping("/sessions/{sessionId}/token-usage")
    public Map<String, Object> listTokenUsage(@PathVariable String sessionId) {
        log.info("[Studio] 查询会话 Token 消耗明细: sessionId={}", sessionId);

        LambdaQueryWrapper<TokenUsageRecord> wrapper = new LambdaQueryWrapper<TokenUsageRecord>()
                .eq(TokenUsageRecord::getSessionId, sessionId)
                .orderByDesc(TokenUsageRecord::getId)
                .last("LIMIT 20");
        List<TokenUsageRecord> list = tokenUsageRecordMapper.selectList(wrapper);

        return Map.of(
                BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK,
                BusinessConst.RESPONSE_KEY_DATA, list
        );
    }

    /**
     * 聚合统计全部会话的 Token 消耗。
     * <p>
     * 查询所有 Token 消耗记录，汇总输入 Token 总量、输出 Token 总量、合计 Token 总量，
     * 并统计涉及的会话数与记录数，供 Studio 展示全局 Token 消耗概览。
     * </p>
     * <p>
     * 注意：本接口会加载全量 Token 消耗记录到内存中进行聚合，适用于调试环境的
     * 数据规模；生产环境如数据量较大建议改用 SQL 聚合查询。
     * </p>
     *
     * @return 包含状态码与 Token 汇总信息的响应 Map
     */
    @GetMapping("/token-summary")
    public Map<String, Object> tokenSummary() {
        log.info("[Studio] 聚合统计全部会话 Token 消耗");

        List<TokenUsageRecord> records = tokenUsageRecordMapper.selectList(new LambdaQueryWrapper<>());

        // 汇总输入 Token（过滤空值，避免 NPE）
        long totalInputTokens = records.stream()
                .filter(r -> r.getInputTokens() != null)
                .mapToLong(TokenUsageRecord::getInputTokens)
                .sum();

        // 汇总输出 Token（过滤空值，避免 NPE）
        long totalOutputTokens = records.stream()
                .filter(r -> r.getOutputTokens() != null)
                .mapToLong(TokenUsageRecord::getOutputTokens)
                .sum();

        // 统计涉及的会话数（按 sessionId 去重）
        long sessionCount = records.stream()
                .map(TokenUsageRecord::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        Map<String, Object> summary = new HashMap<>(8);
        summary.put("totalInputTokens", totalInputTokens);
        summary.put("totalOutputTokens", totalOutputTokens);
        summary.put("totalTokens", totalInputTokens + totalOutputTokens);
        summary.put("sessionCount", sessionCount);
        summary.put("recordCount", records.size());

        Map<String, Object> result = new HashMap<>(4);
        result.put(BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK);
        result.put(BusinessConst.RESPONSE_KEY_DATA, summary);
        return result;
    }
}
