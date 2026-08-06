package com.agent.scope.framework.controller;

import com.agent.scope.framework.dto.ChatStreamDTO;
import com.agent.scope.framework.dto.PermissionConfirmDTO;
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
 *   <li>POST /api/chat/stream - SSE 流式接收响应</li>
 *   <li>POST /api/chat/confirm - 权限确认（HITL 人机交互，敏感工具调用审批）</li>
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

    /**
     * 权限确认（HITL 人机交互）。
     *
     * <p>当敏感工具调用（如 batch_control_device）被权限系统拦截时，Agent 暂停执行，
     * 前端收到 {@code permission_ask} SSE 事件后展示确认界面。
     * 用户确认/拒绝后，调用此接口恢复 Agent 执行。</p>
     *
     * @param dto        权限确认请求
     * @param authHeader Authorization 头
     * @return SSE 事件流（恢复执行后的后续事件）
     */
    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirm(@Validated @RequestBody PermissionConfirmDTO dto,
                              @RequestHeader(value = "Authorization", required = true) String authHeader
    ) {
        log.info("[Chat] 权限确认: sessionId={}, userId={}, confirms={}",
                dto.getSessionId(), dto.getUserId(),
                dto.getConfirms() != null ? dto.getConfirms().size() : 0);

        String accessToken = extractAccessToken(authHeader);
        dto.setAccessToken(accessToken);

        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT);
        chatService.confirmAndResume(dto, emitter);

        return emitter;
    }
}