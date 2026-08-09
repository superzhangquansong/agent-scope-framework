package com.agent.scope.framework.hdl.port;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.ImageInfo;
import com.agent.scope.framework.vo.ToolResultVO;

import java.util.List;

/**
 * 户型图分析端口接口。
 *
 * <p>封装户型图分析与产品推荐能力，由实现类调用视觉模型识别房间、
 * 搜索 HDL 产品、应用预算约束、分配坐标位置。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface FloorPlanPort {

    /**
     * 分析户型图并生成产品推荐方案。
     *
     * @param images         用户上传的户型图图片列表
     * @param userRequirement 用户需求文本
     * @param sessionContext  会话上下文
     * @return 工具结果 VO（data 为 FloorPlanVO，含 rooms/products/positions/totalPrice）
     */
    ToolResultVO analyzeFloorPlan(List<ImageInfo> images, String userRequirement,
                                   SessionContext sessionContext);
}
