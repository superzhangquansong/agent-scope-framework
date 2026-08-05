package com.agent.scope.framework.controller;

import com.agent.scope.framework.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天控制器。
 * <p>
 * 提供会话创建、消息发送与 SSE 流式接收接口。
 * </p>
 * <p>
 * <b>接口列表</b>：
 * <ul>
 *   <li>POST /api/chat/session/create - 创建会话（JSON 请求体）</li>
 *   <li>POST /api/chat/message - 发送消息（JSON 请求体，缓存供流式消费）</li>
 *   <li>GET /api/chat/stream - SSE 流式接收响应</li>
 * </ul>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    /**
     * Bearer Token 前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 核心聊天服务
     */
    private final ChatService chatService;


    /**
     * SSE 流式接收响应。
     * <p>
     * 返回 Server-Sent Events 流，客户端通过 EventSource 订阅。
     * 事件类型包括：start、thinking、answer、tool_call、done、error。
     * </p>
     *
     * @param sessionId  会话 ID
     * @param userId     用户 ID
     * @param houseId    房屋 ID
     * @param authHeader Authorization 请求头（格式：Bearer {accessToken}）
     * @return SSE 事件流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String sessionId,
            @RequestParam String userId,
            @RequestParam String houseId,
            @RequestParam String userMessage,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        log.info("[Chat] SSE 流连接: sessionId={}, userId={}, houseId={}", sessionId, userId, houseId);

        // 从 Authorization 头提取 accessToken
        String accessToken = extractAccessToken(authHeader);

        // 创建 SSE Emitter，超时时间 5 分钟
        SseEmitter emitter = new SseEmitter(300_000L);

        // 调用聊天服务流式输出事件
        chatService.streamEvents(sessionId, userId, houseId, accessToken, userMessage, emitter);

        return emitter;
    }

    /**
     * 从 Authorization 请求头中提取 accessToken。
     * <p>
     * 支持 {@code Bearer {token}} 格式，自动去除前缀。
     * </p>
     *
     * @param authHeader Authorization 请求头原始值
     * @return accessToken，不存在时返回 null
     */
    private String extractAccessToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return authHeader.trim();
    }
}