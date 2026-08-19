package com.agent.scope.framework.mapper;

import com.agent.scope.framework.entity.TaskQueueRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 任务队列记录 Mapper 接口。
 * <p>
 * 基于 MyBatis-Plus {@link BaseMapper} 提供基础 CRUD，
 * 持久化异步任务到 MySQL {@code task_queue_record} 表。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface TaskQueueRecordMapper extends BaseMapper<TaskQueueRecord> {
}
