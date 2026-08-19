package com.agent.scope.framework.controller;

import com.agent.scope.framework.annotation.Auditable;
import com.agent.scope.framework.dto.ChatStreamDTO;
import com.agent.scope.framework.dto.PermissionConfirmDTO;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import com.agent.scope.framework.hdl.HdlIotService;
import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.ChatService;
import com.agent.scope.framework.service.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Semaphore;

import static com.agent.scope.framework.constant.BusinessConst.SSE_EMITTER_TIMEOUT;
import static com.agent.scope.framework.utils.ChatUtils.extractAccessToken;

/**
 * 聊天控制器。
 * <p>
 * 支持两种认证方式：
 * <ul>
 *   <li>Authorization: Bearer {jwt} — 从 JWT 提取 user/accessToken</li>
 *   <li>X-Session-Token — HDL 登录会话，从 SessionManager 解析 UserSession</li>
 * </ul>
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Validated
public class ChatController {

    /**
     * SSE 并发连接数上限
     */
    private static final int MAX_CONCURRENT_SSE = 500;

    /**
     * SSE 并发连接信号量
     */
    private static final Semaphore SSE_SEMAPHORE = new Semaphore(MAX_CONCURRENT_SSE);

    private final ChatService chatService;

    private final SessionManager sessionManager;

    private final HdlIotService hdlIotService;


    /**
     * SSE 流式接收响应。
     *
     * @return SSE 事件流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Validated @RequestBody ChatStreamDTO dto,
                             @RequestHeader(value = "Authorization", required = false) String authHeader,
                             @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        if (!SSE_SEMAPHORE.tryAcquire()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "SSE并发连接数已达上限: " + MAX_CONCURRENT_SSE);
        }

        // 1. 优先从 X-Session-Token 解析用户上下文（HDL 登录流程）
        if (sessionToken != null && !sessionToken.isEmpty()) {
            UserSession session = sessionManager.getSession(sessionToken);
            if (session != null && session.isLoggedIn()) {
                // 访问令牌过期时自动刷新（SSE 流启动前同步处理，避免流中 HDL API 401）
                session = refreshTokenIfNeeded(session);
                // 注入用户信息和 hdlAccessToken
                dto.setUserId(session.getLoginName());
                dto.setAccessToken(session.getHdlAccessToken());
                if (dto.getHouseId() == null && session.getCurrentHomeId() != null) {
                    dto.setHouseId(session.getCurrentHomeId());
                }
                log.info("[Chat] 从 X-Session-Token 解析用户: loginName={}, houseId={}",
                        session.getLoginName(), dto.getHouseId());
            } else {
                log.warn("[Chat] X-Session-Token 无效或已过期: token={}", sessionToken);
            }
        }

        // 2. 兜底：从 Authorization 头提取 accessToken
        if (dto.getAccessToken() == null && authHeader != null) {
            dto.setAccessToken(extractAccessToken(authHeader));
        }

        // 3. userId 由 Controller 从 Session 注入，前端可不传，此处编程校验
        if (dto.getUserId() == null || dto.getUserId().isEmpty()) {
            SSE_SEMAPHORE.release();
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户信息不能为空，请先登录");
        }

        log.info("[Chat] SSE 流连接: sessionId={}, userId={}, houseId={}",
                dto.getSessionId(), dto.getUserId(), dto.getHouseId());

        // 创建 SSE Emitter，超时时间 5 分钟
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT);
        // 通过 emitter 回调在流结束/超时/异常时释放信号量
        emitter.onCompletion(SSE_SEMAPHORE::release);
        emitter.onTimeout(SSE_SEMAPHORE::release);
        emitter.onError(e -> SSE_SEMAPHORE.release());

        try {
            // 调用聊天服务流式输出事件
            chatService.streamEvents(dto, emitter);
        } catch (RuntimeException e) {
            // 同步阶段失败时释放信号量（异步结束由 emitter 回调释放）
            SSE_SEMAPHORE.release();
            throw e;
        }

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
    @Auditable(action = "PERMISSION_CONFIRM", target = "HITL权限确认")
    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirm(@Validated @RequestBody PermissionConfirmDTO dto,
                              @RequestHeader(value = "Authorization", required = false) String authHeader,
                              @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        if (!SSE_SEMAPHORE.tryAcquire()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "SSE并发连接数已达上限: " + MAX_CONCURRENT_SSE);
        }

        // 1. 优先从 X-Session-Token 解析用户上下文（HDL 登录流程）
        if (sessionToken != null && !sessionToken.isEmpty()) {
            UserSession session = sessionManager.getSession(sessionToken);
            if (session != null && session.isLoggedIn()) {
                // 访问令牌过期时自动刷新（SSE 流启动前同步处理）
                session = refreshTokenIfNeeded(session);
                // 注入 userId 和 hdlAccessToken（若 DTO 中未传入）
                if (dto.getUserId() == null || dto.getUserId().isEmpty()) {
                    dto.setUserId(session.getLoginName());
                }
                if (dto.getAccessToken() == null) {
                    dto.setAccessToken(session.getHdlAccessToken());
                }
                if (dto.getHouseId() == null && session.getCurrentHomeId() != null) {
                    dto.setHouseId(session.getCurrentHomeId());
                }
                log.info("[Chat] 权限确认-从 X-Session-Token 解析用户: loginName={}, houseId={}",
                        session.getLoginName(), dto.getHouseId());
            }
        }

        // 2. 兜底：从 Authorization 头提取 accessToken
        if (dto.getAccessToken() == null && authHeader != null) {
            dto.setAccessToken(extractAccessToken(authHeader));
        }

        // 3. userId 必填校验
        if (dto.getUserId() == null || dto.getUserId().isEmpty()) {
            SSE_SEMAPHORE.release();
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户信息不能为空，请先登录");
        }

        log.info("[Chat] 权限确认: sessionId={}, userId={}, confirms={}",
                dto.getSessionId(), dto.getUserId(),
                dto.getConfirms() != null ? dto.getConfirms().size() : 0);

        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT);
        // 通过 emitter 回调在流结束/超时/异常时释放信号量
        emitter.onCompletion(SSE_SEMAPHORE::release);
        emitter.onTimeout(SSE_SEMAPHORE::release);
        emitter.onError(e -> SSE_SEMAPHORE.release());

        try {
            chatService.confirmAndResume(dto, emitter);
        } catch (RuntimeException e) {
            // 同步阶段失败时释放信号量（异步结束由 emitter 回调释放）
            SSE_SEMAPHORE.release();
            throw e;
        }

        return emitter;
    }

    /**
     * 检查并刷新过期的 HDL 访问令牌。
     *
     * <p>ChatController 因支持双重认证（X-Session-Token 或 Authorization 头），无法使用
     * {@link com.agent.scope.framework.annotation.ValidToken} + {@link com.agent.scope.framework.config.TokenAspect}
     * 的 AOP 拦截方式，改为在方法内同步调用本方法完成令牌刷新。</p>
     *
     * <p>刷新逻辑与 TokenAspect 一致：
     * <ol>
     *   <li>访问令牌未过期 → 原样返回</li>
     *   <li>访问令牌过期、刷新令牌未过期 → 调用 HDL 刷新接口，保存 session 后返回</li>
     *   <li>刷新令牌也过期 → 抛出 SESSION_REFRESH_TOKEN_EXPIRED，由 GlobalExceptionHandler 返回 401</li>
     * </ol>
     * </p>
     *
     * @param session 当前用户会话
     * @return 令牌有效（原样或刷新后）的 session
     * @throws BusinessException 刷新令牌过期时抛出 SESSION_REFRESH_TOKEN_EXPIRED
     */
    private UserSession refreshTokenIfNeeded(UserSession session) {
        // 访问令牌未过期，直接返回
        if (!session.isHdlAccessTokenExpired()) {
            return session;
        }
        log.info("[Chat] 访问令牌已过期，尝试刷新: loginName={}", session.getLoginName());

        // 刷新令牌也过期 → 通知前端重新登录
        if (session.isHdlRefreshTokenExpired()) {
            log.warn("[Chat] 刷新令牌已过期，需重新登录: loginName={}", session.getLoginName());
            session.clearToken();
            sessionManager.save(session);
            throw new BusinessException(ErrorCode.SESSION_REFRESH_TOKEN_EXPIRED);
        }

        // 调用 HDL 刷新接口
        HdlIotService.LoginResult refreshResult = hdlIotService.refreshToken(session);
        if (!refreshResult.isSuccess()) {
            log.warn("[Chat] 刷新令牌失败: loginName={}, msg={}",
                    session.getLoginName(), refreshResult.getMsg());
            session.clearToken();
            sessionManager.save(session);
            throw new BusinessException(ErrorCode.SESSION_REFRESH_TOKEN_EXPIRED, refreshResult.getMsg());
        }

        // 刷新成功 → 持久化新 session
        sessionManager.save(session);
        log.info("[Chat] 令牌刷新成功: loginName={}", session.getLoginName());
        return session;
    }
}