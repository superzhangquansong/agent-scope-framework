package com.agent.scope.framework.bo.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具二进制数据增量事件 BO（对应 AgentScope {@code ToolResultDataDeltaEvent}）。
 * <p>工具返回二进制数据（如图片）时推送增量，前端按 toolCallId 累积后渲染。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ToolResultDataDeltaEventBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SSE 事件类型（固定 "tool_result_data_delta"） */
    private String type;
    /** 会话 ID */
    private String sessionId;
    /** 对应工具调用的 ID */
    private String toolCallId;
    /** MIME 类型（如 "image/png"） */
    private String mediaType;
    /** 增量 base64 编码数据 */
    private String data;
    /** 数据 URL（可选，工具直接提供 URL 时使用） */
    private String url;
}
