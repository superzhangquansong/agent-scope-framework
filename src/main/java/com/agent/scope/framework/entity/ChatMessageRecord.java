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
 * 对话消息记录实体。
 * <p>
 * 记录一次对话中产生的每一条消息，包含用户输入、LLM 思考过程摘要、LLM 最终回复。
 * 通过 {@link #role} 字段区分消息来源：
 * <ul>
 *   <li>{@code user}：用户输入消息</li>
 *   <li>{@code thinking}：LLM 思考过程摘要（推理内容）</li>
 *   <li>{@code assistant}：LLM 最终回复</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message_record")
public class ChatMessageRecord implements Serializable {

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
     * 用户 ID
     */
    private String userId;

    /**
     * 家庭 ID（业务隔离用，可为空）
     */
    private String houseId;

    /**
     * 消息角色：user / thinking / assistant
     */
    private String role;

    /**
     * 消息内容（用户原文 / 思考摘要 / 最终回复）
     */
    private String content;

    /**
     * 消息产生的时间戳（毫秒）
     */
    private Long messageTimestamp;

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
