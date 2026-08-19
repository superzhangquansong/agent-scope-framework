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
 * 外部执行请求事件 BO（对应 AgentScope {@code RequireExternalExecutionEvent}）。
 * <p>Agent 暂停等待外部系统执行工具时推送，前端据此展示"等待外部执行"状态，
 * 并可触发外部系统回调接口将执行结果回传。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RequireExternalExecutionEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "require_external_execution"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 回复消息 ID */
    private String replyId;
    /** 待外部执行的工具调用列表（含 toolCallId、toolName、input） */
    private List<Map<String, Object>> toolCalls;
}
