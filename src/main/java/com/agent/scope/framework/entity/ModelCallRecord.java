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
 * 模型调用记录实体。
 * <p>
 * 记录 ReAct 循环中每一次 LLM 模型调用的完整信息，通过 {@code replyId} 关联
 * {@link io.agentscope.core.event.ModelCallStartEvent} 与
 * {@link io.agentscope.core.event.ModelCallEndEvent}，累积该次调用的输出内容
 * （文本 / 思考 / 工具调用片段）及 Token 消耗，便于全链路问题定位与成本核算。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_call_record")
public class ModelCallRecord implements Serializable {

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
     * 回复 ID（AgentScope 生成，同一次模型调用内共享，关联 Start/End 事件）
     */
    private String replyId;

    /**
     * 模型调用输出内容（累积 TextBlock/ThinkingBlock/ToolCall 片段的汇总文本）
     */
    private String outputContent;

    /**
     * 输入 Token 数（prompt tokens）
     */
    private Integer inputTokens;

    /**
     * 输出 Token 数（completion tokens）
     */
    private Integer outputTokens;

    /**
     * 总 Token 数（input + output）
     */
    private Integer totalTokens;

    /**
     * 缓存命中 Token 数（prompt cache 命中部分，0 表示无缓存）
     */
    private Integer cachedTokens;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型调用耗时（毫秒，Start → End）
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
