package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 权限确认请求事件 BO（HITL 人机交互）。
 *
 * <p>当 Agent 调用敏感工具（如 batch_control_device）被权限系统拦截为 ASK 决策时，
 * Agent 会发射 {@link io.agentscope.core.event.RequireUserConfirmEvent}，
 * ChatService 将其转换为此 BO 通过 SSE 推送给前端。</p>
 *
 * <p>前端收到此事件后应展示确认界面，列出待确认的工具调用信息，
 * 用户点击"允许"或"拒绝"后，调用 {@code POST /api/chat/confirm} 接口恢复执行。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PermissionAskEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型：permission_ask */
    private String type;

    /** 会话 ID */
    private String sessionId;

    /** 回复 ID（关联 ModelCallStart/End 事件） */
    private String replyId;

    /** 待确认的工具调用列表 */
    private List<ToolCallInfo> toolCalls;

    /**
     * 工具调用信息（精简版，仅含前端展示所需字段）。
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ToolCallInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 工具调用 ID */
        private String toolCallId;

        /** 工具名称 */
        private String toolName;

        /** 工具入参（JSON Map） */
        private Map<String, Object> input;
    }
}
