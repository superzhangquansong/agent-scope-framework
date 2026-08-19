package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent 完成回复事件 BO（对应 AgentScope {@code AgentEndEvent}）。
 * <p>Agent 推理-执行循环完成时推送。注意：实际 {@code agent_end} SSE 事件
 * 由 ChatService.doOnComplete 携带回复内容发送，本 BO 仅用于 Handler 体系记录。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentEndEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "agent_end"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 回复消息 ID */
    private String replyId;
}
