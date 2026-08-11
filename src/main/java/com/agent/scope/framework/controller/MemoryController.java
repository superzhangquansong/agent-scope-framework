package com.agent.scope.framework.controller;

import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.SessionManager;
import com.agent.scope.framework.vo.ToolResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆管理 REST 控制器（桩实现）。
 *
 * <p>前端历史对话列表、记忆压缩等接口，当前返回空数据避免 404。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final SessionManager sessionManager;

    private String resolveUserId(String sessionToken) {
        UserSession session = sessionManager.getSession(sessionToken);
        return (session != null && session.isLoggedIn()) ? session.getLoginName() : null;
    }

    @GetMapping("/chat-history")
    public ToolResultVO chatHistory(@RequestHeader("X-Session-Token") String sessionToken,
                                     @RequestParam(value = "limit", defaultValue = "100") int limit) {
        String userId = resolveUserId(sessionToken);
        if (userId == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chatHistory", List.of());
        data.put("count", 0);
        return ToolResultVO.success("查询成功", data, null, null);
    }

    @GetMapping("/history")
    public ToolResultVO history(@RequestHeader("X-Session-Token") String sessionToken) {
        String userId = resolveUserId(sessionToken);
        if (userId == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("history", List.of());
        data.put("count", 0);
        return ToolResultVO.success("查询成功", data, null, null);
    }

    @GetMapping("/summary")
    public ToolResultVO summary(@RequestHeader("X-Session-Token") String sessionToken) {
        String userId = resolveUserId(sessionToken);
        if (userId == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", "");
        return ToolResultVO.success("查询成功", data, null, null);
    }

    @PostMapping("/save")
    public ToolResultVO saveMemory(@RequestHeader("X-Session-Token") String sessionToken) {
        return ToolResultVO.success("保存成功", Map.of(), null, null);
    }

    @PostMapping("/compress")
    public ToolResultVO compressMemory(@RequestHeader("X-Session-Token") String sessionToken) {
        return ToolResultVO.success("压缩完成", Map.of("compressed", true), null, null);
    }

    @DeleteMapping("/clear")
    public ToolResultVO clearMemory(@RequestHeader("X-Session-Token") String sessionToken) {
        return ToolResultVO.success("清除成功", Map.of("cleared", true), null, null);
    }
}
