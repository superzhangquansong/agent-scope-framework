package com.agent.scope.framework.mapper;

import com.agent.scope.framework.entity.ChatMessageRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 对话消息记录 Mapper 接口。
 * <p>
 * 基于 MyBatis-Plus {@link BaseMapper} 提供基础 CRUD 能力，
 * 用于持久化用户消息、LLM 思考摘要与最终回复。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface ChatMessageRecordMapper extends BaseMapper<ChatMessageRecord> {
}
