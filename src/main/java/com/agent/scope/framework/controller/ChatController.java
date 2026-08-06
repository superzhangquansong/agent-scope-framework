package com.agent.scope.framework.controller;

import com.agent.scope.framework.dto.ChatStreamDTO;
import com.agent.scope.framework.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.agent.scope.framework.constant.BusinessConst.SSE_EMITTER_TIMEOUT;
import static com.agent.scope.framework.utils.ChatUtils.extractAccessToken;

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
@Validated
public class ChatController {

    /**
     * 核心聊天服务
     */
    private final ChatService chatService;


    /**
     * SSE 流式接收响应。
     *
     * @return SSE 事件流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Validated @RequestBody ChatStreamDTO dto,
                             @RequestHeader(value = "Authorization", required = true) String authHeader
    ) {
        log.info("[Chat] SSE 流连接: sessionId={}, userId={}, houseId={}", dto.getSessionId(), dto.getUserId(), dto.getHouseId());

        // 从 Authorization 头提取 accessToken
        String accessToken = extractAccessToken(authHeader);
        dto.setAccessToken(accessToken);

        // 创建 SSE Emitter，超时时间 5 分钟
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT);

        // 调用聊天服务流式输出事件
        chatService.streamEvents(dto, emitter);

        return emitter;
    }
}