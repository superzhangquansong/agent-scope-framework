package com.agent.scope.framework.mapper;

import com.agent.scope.framework.entity.ModelCallRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 模型调用记录 Mapper 接口。
 * <p>
 * 基于 MyBatis-Plus {@link BaseMapper} 提供基础 CRUD 能力，
 * 用于持久化每次 LLM 模型调用的输出内容与 Token 消耗。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface ModelCallRecordMapper extends BaseMapper<ModelCallRecord> {
}
