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
 * Token 消耗记录实体。
 * <p>
 * 记录一次对话会话的 Token 消耗情况，包括输入 Token、输出 Token、总 Token 数
 * 及所使用的模型名称，用于成本统计与用量分析。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("token_usage_record")
public class TokenUsageRecord implements Serializable {

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
     * 模型名称
     */
    private String modelName;

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
