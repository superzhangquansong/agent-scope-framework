package com.agent.scope.framework.handler;

import com.agent.scope.framework.bo.event.PermissionAskEventBO;
import com.agent.scope.framework.bo.event.PermissionAskEventBO.ToolCallInfo;
import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.dto.PermissionConfirmDTO;
import com.agent.scope.framework.enums.AgentEventEnum;
import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.ChatService;
import com.agent.scope.framework.service.PendingConfirmationService;
import com.agent.scope.framework.service.SessionManager;
import io.agentscope.core.event.RequireUserConfirmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.agent.scope.framework.constant.BusinessConst.*;

/**
 * 权限确认请求事件处理器（HITL 人机交互）。
 * <p>对应 ChatService.forwardAgentEvent 原有 RequireUserConfirmEvent 分支：当敏感工具调用被权限系统拦截为 ASK
 * 决策时，Agent 暂停执行，缓存待确认的 ToolUseBlock 到 Redis、标记会话为权限暂停状态、推送 permission_ask
 * 事件（内部）+ permission_paused 事件（前端）。</p>
 * <p>当 scope.agentscope.permission.enabled=false 时，不发送 permission_paused 到前端，直接调用
 * {@link ChatService#confirmAndResume} 自动批准所有工具调用并恢复 Agent 执行。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class RequireUserConfirmHandler implements AgentEventHandler<RequireUserConfirmEvent> {

    private final PendingConfirmationService pendingConfirmationService;
    private final AgentScopeProperties properties;
    private final SessionManager sessionManager;

    /**
     * ChatService，使用 @Lazy 避免循环依赖：
     * ChatService → AgentEventHandlerRegistry → RequireUserConfirmHandler → ChatService
     */
    @Lazy
    @Autowired
    private ChatService chatService;

    public RequireUserConfirmHandler(PendingConfirmationService pendingConfirmationService,
                                     AgentScopeProperties properties,
                                     SessionManager sessionManager) {
        this.pendingConfirmationService = pendingConfirmationService;
        this.properties = properties;
        this.sessionManager = sessionManager;
    }

    @Override
    public Class<RequireUserConfirmEvent> getEventType() {
        return RequireUserConfirmEvent.class;
    }

    @Override
    public void handle(EventContext ctx, RequireUserConfirmEvent ruc) throws Exception {
        String sessionId = ctx.getSessionId();

        // 权限已禁用：自动批准所有工具调用，跳过前端确认流程
        if (properties.getPermission() != null && !properties.getPermission().isEnabled()) {
            log.info("[Handler] 权限已禁用，自动批准: sessionId={}, tools={}",
                    sessionId,
                    ruc.getToolCalls().stream().map(tcb -> tcb.getName()).toList());

            pendingConfirmationService.cachePendingConfirmations(
                    sessionId, ruc.getToolCalls(), ctx.getRecorder().toolCallArguments);

            // 标记 autoConfirmed，防止 streamEvents 的 doOnComplete 关闭 emitter
            ctx.getRecorder().autoConfirmed = true;

            // 构建自动批准 DTO，从当前会话补全凭证
            PermissionConfirmDTO dto = new PermissionConfirmDTO();
            dto.setSessionId(sessionId);
            dto.setUserId(ctx.getUserId());

            UserSession session = sessionManager.findByLoginName(ctx.getUserId());
            if (session != null) {
                dto.setAccessToken(session.getHdlAccessToken());
                dto.setHouseId(session.getCurrentHomeId());
            } else {
                log.warn("[Handler] 自动批准未找到会话: userId={}", ctx.getUserId());
            }

            // 直接恢复 Agent 执行，不发送 permission_paused 到前端
            chatService.confirmAndResume(dto, ctx.getEmitter());
            return;
        }

        log.info("[Handler] 权限确认请求: sessionId={}, replyId={}, toolCalls={}",
                sessionId, ruc.getReplyId(),
                ruc.getToolCalls().stream().map(tcb -> tcb.getName()
                        + "(input=" + (tcb.getInput() != null ? "有" : "无") + ")").toList());

        // 缓存到 Redis，传入 recorder.toolCallArguments 作为入参回退来源
        pendingConfirmationService.cachePendingConfirmations(
                sessionId, ruc.getToolCalls(), ctx.getRecorder().toolCallArguments);

        // 标记会话为权限暂停状态，doOnComplete 时据此区分正常结束与暂停
        ctx.getRecorder().permissionPaused = true;

        // 从 Redis 加载完整数据（含回退解析后的 input），构建前端展示用的 ToolCallInfo 列表
        List<ToolCallInfo> toolCallInfos = pendingConfirmationService.loadPendingConfirmations(sessionId);

        // 发送 permission_ask 事件（内部事件，供日志/审计使用）
        PermissionAskEventBO askEventBO = PermissionAskEventBO.builder()
                .type(AgentEventEnum.PERMISSION_ASK.getDesc())
                .sessionId(sessionId)
                .replyId(ruc.getReplyId())
                .toolCalls(toolCallInfos)
                .build();
        ctx.sendEventBo(askEventBO);

        // 同时发送 permission_paused 事件（前端监听此事件，展示确认弹框）
        SseEmitter emitter = ctx.getEmitter();
        try {
            List<String> cnToolNames = toolCallInfos.stream()
                    .map(tc -> TOOL_NAME_CN.getOrDefault(tc.getToolName(), tc.getToolName()))
                    .distinct()
                    .toList();
            String cnTools = String.join("、", cnToolNames);
            String message = "即将执行 " + cnTools + " 操作，请确认";
            emitter.send(SseEmitter.event()
                    .name(SSE_EVENT_PERMISSION_PAUSED)
                    .data(ctx.toJson(Map.of(
                            "message", message,
                            "toolCalls", toolCallInfos))));
            log.info("[Handler] 已发送 permission_paused 事件: sessionId={}, toolCount={}, tools={}",
                    sessionId, toolCallInfos.size(), cnTools);
        } catch (IOException e) {
            log.warn("[Handler] 发送 permission_paused 失败: {}", e.getMessage());
        }
    }
}
