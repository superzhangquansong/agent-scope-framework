package com.agent.scope.framework.hdl;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.FloorPlanPort;
import com.agent.scope.framework.vo.ToolResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 户型图分析服务实现（简化版）。
 *
 * <p>当前为桩实现，返回"功能尚未配置"提示。
 * 完整实现需调用 DashScope qwen-vl-max 视觉模型分析户型图，
 * 搜索 HDL 产品，应用预算约束，分配坐标位置。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
public class FloorPlanServiceImpl implements FloorPlanPort {

    @Override
    public ToolResultVO analyzeFloorPlan(List<ImageInfo> images, String userRequirement,
                                          SessionContext sessionContext) {
        log.info("[FloorPlanService] 户型图分析功能尚未完整配置, userId={}, requirement={}",
                sessionContext.getUserId(), userRequirement);
        return ToolResultVO.failure(501,
                "户型图分析功能尚未配置，需要配置 DashScope API Key 和 MinIO 存储服务后才能使用");
    }
}
