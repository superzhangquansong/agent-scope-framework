package com.agent.scope.framework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具调用记录实体。
 * <p>
 * 记录 LLM 在 ReAct 循环中每一次工具调用的完整信息，包括工具名、入参、出参、
 * 执行状态及耗时，便于后续问题定位与调用链分析。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tool_call_record")
public class ToolCallRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 工具调用 ID（AgentScope 生成，唯一标识一次工具调用）
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具入参（JSON 字符串）
     */
    private String arguments;

    /**
     * 工具出参（JSON 字符串）
     */
    private String result;

    /**
     * 执行状态：SUCCESS / ERROR / UNKNOWN
     */
    private String state;

    /**
     * 工具执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 逻辑删除标识：0 未删除，1 已删除
     */
    @TableLogic
    private Integer deleted;
}
