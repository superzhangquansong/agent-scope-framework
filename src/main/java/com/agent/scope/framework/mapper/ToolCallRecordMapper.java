package com.agent.scope.framework.mapper;

import com.agent.scope.framework.entity.ToolCallRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 工具调用记录 Mapper 接口。
 * <p>
 * 基于 MyBatis-Plus {@link BaseMapper} 提供基础 CRUD 能力，
 * 用于持久化 ReAct 循环中每一次工具调用的入参、出参、状态与耗时。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface ToolCallRecordMapper extends BaseMapper<ToolCallRecord> {
}
