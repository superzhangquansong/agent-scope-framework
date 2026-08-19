package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提前停止请求事件 BO（对应 AgentScope {@code RequestStopEvent}）。
 * <p>中间件或工具发起提前停止请求时推送，前端据此感知非正常结束。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RequestStopEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "request_stop"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 停止原因描述（中间件/工具发起的停止理由） */
    private String reason;
}
