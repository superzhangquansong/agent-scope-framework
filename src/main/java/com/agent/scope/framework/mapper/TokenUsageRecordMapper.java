package com.agent.scope.framework.mapper;

import com.agent.scope.framework.entity.TokenUsageRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * Token 消耗记录 Mapper 接口。
 * <p>
 * 基于 MyBatis-Plus {@link BaseMapper} 提供基础 CRUD 能力，
 * 用于持久化每次会话的 Token 消耗明细。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface TokenUsageRecordMapper extends BaseMapper<TokenUsageRecord> {
}
