package com.agent.scope.framework.controller;

import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.SessionManager;
import com.agent.scope.framework.vo.ToolResultVO;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记忆管理 REST 控制器。
 *
 * <p>基于 AgentScope 2.0 的 {@link AgentStateStore}（Redis 分布式存储）实现，
 * 对话历史和压缩摘要直接从 AgentState 读取，无需手写 MySQL 记忆表。</p>
 *
 * <p>AgentScope 2.0 已废弃 1.0 的 Memory 接口（InMemoryMemory/LongTermMemory），
 * 改用 AgentState.getContext()（对话历史）+ AgentState.getSummary()（压缩摘要）
 * + MEMORY.md（长期记忆，由 MemoryFlushMiddleware 自动维护）。</p>
 *
 * <p>因此 save/compress 接口无需手动调用——压缩由 CompactionMiddleware 在
 * triggerMessages 阈值触发时自动执行，事实写入由 MemoryFlushMiddleware 在
 * flushBeforeCompact=true 时自动完成。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    /** AgentState 在 store 中的 key（与 HarnessAgent 默认一致） */
    private static final String STATE_KEY = "agent_state";

    private final SessionManager sessionManager;
    private final AgentStateStore agentStateStore;

    /**
     * 从 sessionToken 解析 userId（用于 AgentStateStore 寻址）。
     * AgentStateStore 按 (userId, sessionId) 二元组分区，userId 即用户登录名。
     */
    private String resolveUserId(String sessionToken) {
        UserSession session = sessionManager.getSession(sessionToken);
        return (session != null && session.isLoggedIn()) ? session.getLoginName() : null;
    }

    /**
     * 获取对话历史。
     * <p>
     * 从 Redis AgentStateStore 加载 AgentState，返回 context（Msg 列表）。
     * <b>sessionId 可选</b>：不传时聚合该用户所有会话的历史（登录选房阶段前端尚无
     * sessionId，此时应返回全部历史；传了则仅返回指定会话）。
     * </p>
     *
     * @param sessionToken 会话令牌（X-Session-Token 头）
     * @param sessionId    AgentScope 会话 ID（查询参数，可选）
     * @param limit        最多返回的消息条数（默认 100）
     */
    @GetMapping("/chat-history")
    public ToolResultVO chatHistory(@RequestHeader("X-Session-Token") String sessionToken,
                                     @RequestParam(value = "sessionId", required = false) String sessionId,
                                     @RequestParam(value = "limit", defaultValue = "100") int limit) {
        String userId = resolveUserId(sessionToken);
        if (userId == null) {
            return ToolResultVO.failure(401, "未登录或会话已过期");
        }

        JSONArray chatHistory = new JSONArray();

        // 未指定 sessionId：聚合该用户所有会话的历史（按会话 ID 排序保证顺序稳定）
        if (sessionId == null || sessionId.isBlank()) {
            Set<String> sessionIds = agentStateStore.listSessionIds(userId);
            List<String> sortedIds = new ArrayList<>(sessionIds);
            java.util.Collections.sort(sortedIds);
            int totalCount = 0;
            for (String sid : sortedIds) {
                var stateOpt = agentStateStore.get(userId, sid, STATE_KEY, AgentState.class);
                if (stateOpt.isEmpty()) {
                    continue;
                }
                for (Msg msg : stateOpt.get().getContext()) {
                    chatHistory.add(toHistoryItem(msg, sid));
                    totalCount++;
                }
            }
            log.info("[MemoryController] 聚合全部会话历史: userId={}, sessions={}, totalCount={}",
                    userId, sortedIds.size(), totalCount);
            // 按 limit 截取最近的消息（取尾部，最近的最重要）
            int fromIndex = Math.max(0, chatHistory.size() - limit);
            JSONArray recent = new JSONArray();
            for (int i = fromIndex; i < chatHistory.size(); i++) {
                recent.add(chatHistory.get(i));
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chatHistory", recent);
            data.put("count", recent.size());
            return ToolResultVO.success("查询成功", data, null, null);
        }

        // 指定 sessionId：仅返回该会话历史
        var stateOpt = agentStateStore.get(userId, sessionId, STATE_KEY, AgentState.class);
        if (stateOpt.isEmpty()) {
            log.info("[MemoryController] 会话状态不存在: userId={}, sessionId={}", userId, sessionId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chatHistory", List.of());
            data.put("count", 0);
            return ToolResultVO.success("会话无历史记录", data, null, null);
        }

        List<Msg> context = stateOpt.get().getContext();
        // 按 limit 截取最近的消息（取尾部，最近的最重要）
        int fromIndex = Math.max(0, context.size() - limit);
        List<Msg> recent = context.subList(fromIndex, context.size());
        for (Msg msg : recent) {
            chatHistory.add(toHistoryItem(msg, sessionId));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chatHistory", chatHistory);
        data.put("count", chatHistory.size());
        data.put("sessionId", sessionId);
        return ToolResultVO.success("查询成功", data, null, null);
    }

    /**
     * 将 AgentScope Msg 转换为前端可读的历史记录 JSON。
     * <p>MsgRole 为大写枚举（USER/ASSISTANT/SYSTEM/TOOL），前端仅识别
     * "user"/"assistant"，故 USER → "user"，其余角色统一映射为 "assistant"。</p>
     *
     * @param msg       AgentScope 消息
     * @param sessionId 所属会话 ID
     * @return 历史记录 JSON 对象
     */
    private JSONObject toHistoryItem(Msg msg, String sessionId) {
        JSONObject item = new JSONObject();
        String role = msg.getRole() != null ? msg.getRole().name() : "unknown";
        item.put("role", MsgRole.USER.name().equals(role) ? "user" : "assistant");
        // getTextContent() 提取文本内容，工具调用/结果等通过 ContentBlock 体现
        String text = msg.getTextContent();
        item.put("content", text != null ? text : "");
        item.put("sessionId", sessionId);
        return item;
    }

    /**
     * 获取用户所有会话的记忆历史（按会话维度汇总摘要）。
     * <p>通过 AgentStateStore.listSessionIds(userId) 列出所有会话，
     * 每个会话取 summary（压缩摘要）和消息条数。</p>
     */
    @GetMapping("/history")
    public ToolResultVO history(@RequestHeader("X-Session-Token") String sessionToken) {
        String userId = resolveUserId(sessionToken);
        if (userId == null) {
            return ToolResultVO.failure(401, "未登录或会话已过期");
        }

        Set<String> sessionIds = agentStateStore.listSessionIds(userId);
        List<Map<String, Object>> historyList = new ArrayList<>();
        for (String sid : sessionIds) {
            var stateOpt = agentStateStore.get(userId, sid, STATE_KEY, AgentState.class);
            if (stateOpt.isPresent()) {
                AgentState state = stateOpt.get();
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("sessionId", sid);
                entry.put("summary", state.getSummary() != null ? state.getSummary() : "");
                entry.put("messageCount", state.getContext().size());
                entry.put("currentIter", state.getCurIter());
                historyList.add(entry);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("history", historyList);
        data.put("count", historyList.size());
        return ToolResultVO.success("查询成功", data, null, null);
    }

    /**
     * 获取压缩摘要。
     * <p>摘要由 CompactionMiddleware 在 triggerMessages 阈值触发时自动生成，
     * 存入 AgentState.getSummary() 字段。<b>sessionId 可选</b>：
     * 不传时返回该用户全部会话的摘要拼接，传了则仅返回指定会话摘要。</p>
     */
    @GetMapping("/summary")
    public ToolResultVO summary(@RequestHeader("X-Session-Token") String sessionToken,
                                @RequestParam(value = "sessionId", required = false) String sessionId) {
        String userId = resolveUserId(sessionToken);
        if (userId == null) {
            return ToolResultVO.failure(401, "未登录或会话已过期");
        }

        StringBuilder summaryBuilder = new StringBuilder();
        int totalMessages = 0;
        int sessionCount = 0;

        // 未指定 sessionId：聚合该用户所有会话的摘要
        if (sessionId == null || sessionId.isBlank()) {
            Set<String> sessionIds = agentStateStore.listSessionIds(userId);
            List<String> sortedIds = new ArrayList<>(sessionIds);
            java.util.Collections.sort(sortedIds);
            for (String sid : sortedIds) {
                var stateOpt = agentStateStore.get(userId, sid, STATE_KEY, AgentState.class);
                if (stateOpt.isEmpty()) {
                    continue;
                }
                AgentState state = stateOpt.get();
                if (state.getSummary() != null && !state.getSummary().isBlank()) {
                    summaryBuilder.append("【会话 ").append(sid).append("】\n")
                            .append(state.getSummary()).append("\n\n");
                }
                totalMessages += state.getContext().size();
                sessionCount++;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("summary", summaryBuilder.toString());
            data.put("messageCount", totalMessages);
            data.put("sessionCount", sessionCount);
            return ToolResultVO.success("查询成功", data, null, null);
        }

        // 指定 sessionId：仅返回该会话摘要
        var stateOpt = agentStateStore.get(userId, sessionId, STATE_KEY, AgentState.class);
        String summaryText = "";
        int messageCount = 0;
        if (stateOpt.isPresent()) {
            AgentState state = stateOpt.get();
            summaryText = state.getSummary() != null ? state.getSummary() : "";
            messageCount = state.getContext().size();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summaryText);
        data.put("messageCount", messageCount);
        data.put("sessionId", sessionId);
        return ToolResultVO.success("查询成功", data, null, null);
    }

    /**
     * 手动保存记忆（已由 MemoryFlushMiddleware 自动完成）。
     * <p>AgentScope 2.0 中事实写入由 MemoryFlushMiddleware 在压缩前自动 flush 到
     * memory/*.md 日流水账，无需手动调用。此接口保留兼容前端，返回自动完成状态。</p>
     */
    @PostMapping("/save")
    public ToolResultVO saveMemory(@RequestHeader("X-Session-Token") String sessionToken) {
        log.info("[MemoryController] save 请求：记忆已由 MemoryFlushMiddleware 自动维护，无需手动保存");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("autoManaged", true);
        data.put("message", "记忆已由 MemoryFlushMiddleware 自动维护，无需手动保存");
        return ToolResultVO.success("记忆已自动保存", data, null, null);
    }

    /**
     * 手动压缩记忆（已由 CompactionMiddleware 自动完成）。
     * <p>压缩由 CompactionMiddleware 在 triggerMessages/triggerTokens 阈值触发时
     * 自动执行，无需手动调用。此接口保留兼容前端，返回自动完成状态。</p>
     */
    @PostMapping("/compress")
    public ToolResultVO compressMemory(@RequestHeader("X-Session-Token") String sessionToken,
                                       @RequestParam(value = "sessionId", required = false) String sessionId) {
        log.info("[MemoryController] compress 请求：压缩已由 CompactionMiddleware 自动执行，无需手动触发");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("autoManaged", true);
        data.put("triggerMessages", "由 scope.agentscope.memory.trigger-messages 配置驱动");
        data.put("message", "压缩已由 CompactionMiddleware 自动执行，无需手动触发");
        return ToolResultVO.success("压缩已自动完成", data, null, null);
    }

    /**
     * 清除指定会话的记忆（删除 AgentState）。
     * <p>从 Redis 删除 (userId, sessionId) 对应的 AgentState，
     * 下次对话将从空白状态重新开始。</p>
     *
     * @param sessionToken 会话令牌
     * @param sessionId    要清除的 AgentScope 会话 ID（查询参数，为空则清除当前用户所有会话）
     */
    @DeleteMapping("/clear")
    public ToolResultVO clearMemory(@RequestHeader("X-Session-Token") String sessionToken,
                                    @RequestParam(value = "sessionId", required = false) String sessionId) {
        String userId = resolveUserId(sessionToken);
        if (userId == null) {
            return ToolResultVO.failure(401, "未登录或会话已过期");
        }

        int clearedCount;
        if (sessionId != null && !sessionId.isEmpty()) {
            // 清除指定会话
            agentStateStore.delete(userId, sessionId);
            clearedCount = 1;
            log.info("[MemoryController] 清除会话记忆: userId={}, sessionId={}", userId, sessionId);
        } else {
            // 清除该用户所有会话
            Set<String> allSessions = agentStateStore.listSessionIds(userId);
            for (String sid : allSessions) {
                agentStateStore.delete(userId, sid);
            }
            clearedCount = allSessions.size();
            log.info("[MemoryController] 清除用户全部会话记忆: userId={}, count={}", userId, clearedCount);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cleared", true);
        data.put("clearedCount", clearedCount);
        return ToolResultVO.success("清除成功", data, null, null);
    }
}
