package com.agent.scope.framework.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 权限确认请求 DTO（HITL 人机交互）。
 *
 * <p>用户在前端确认敏感工具调用后，通过此 DTO 提交确认结果。
 * 每个待确认的工具调用对应一个 {@link ConfirmItem}，用户可选择允许或拒绝。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
public class PermissionConfirmDTO {

    /** 会话 ID */
    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    /** 用户 ID */
    @NotBlank(message = "userId 不能为空")
    private String userId;

    /** 家庭 ID（可为空） */
    private String houseId;

    /** 访问令牌 */
    private String accessToken;

    /**
     * 用户消息（自然语言确认/拒绝）。
     * <p>当前端不传 {@link #confirms} 数组时，可通过此字段发送"继续"/"取消"等自然语言。
     * ChatService 会检测拒绝关键词（取消/拒绝/不要等）来判断 allowed。</p>
     */
    private String userMessage;

    /** 确认项列表（每个待确认的工具调用一项） */
    private List<ConfirmItem> confirms;

    /**
     * 单个工具调用的确认项。
     */
    @Data
    public static class ConfirmItem {
        /** 工具调用 ID（来自 PermissionAskEventBO.toolCalls[].toolCallId） */
        private String toolCallId;

        /** 工具名称 */
        private String toolName;

        /** 是否允许执行 */
        private boolean allowed;
    }
}
