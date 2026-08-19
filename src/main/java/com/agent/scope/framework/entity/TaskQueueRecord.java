package com.agent.scope.framework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务队列记录实体。
 * <p>
 * 对应 {@code task_queue_record} 表，持久化异步任务的完整信息：
 * 任务状态（PENDING/RUNNING/COMPLETED/FAILED/DEAD）、负载、重试次数、
 * 执行结果与错误信息。替代原 Redis List + Hash 方案，
 * 提供 ACK 级别的可靠性：任务落库后即使应用崩溃，重启后仍可恢复执行。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_queue_record")
public class TaskQueueRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务唯一 ID（UUID） */
    private String taskId;

    /** 关联会话 ID */
    private String sessionId;

    /** 用户 ID */
    private String userId;

    /** 任务类型（agent 表示通过 Agent 执行） */
    private String taskType;

    /** 任务载荷（JSON 字符串） */
    private String payload;

    /** 状态：PENDING/RUNNING/COMPLETED/FAILED/DEAD */
    private String status;

    /** 失败重试次数 */
    private Integer retryCount;

    /** 最大重试次数（超过标记为 DEAD） */
    private Integer maxRetry;

    /** 任务执行结果（仅 COMPLETED 状态有值） */
    private String result;

    /** 错误信息（仅 FAILED/DEAD 状态有值） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
