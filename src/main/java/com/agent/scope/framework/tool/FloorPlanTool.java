//package com.agent.scope.framework.tool;
//
//import com.agent.scope.framework.constant.BusinessConst;
//import com.agent.scope.framework.context.SessionContext;
//import com.agent.scope.framework.hdl.ImageInfo;
//import com.agent.scope.framework.hdl.port.FloorPlanPort;
//import com.agent.scope.framework.vo.ToolResultVO;
//import io.agentscope.core.agent.RuntimeContext;
//import io.agentscope.core.tool.Tool;
//import io.agentscope.core.tool.ToolParam;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
///**
// * 户型图方案推荐工具（特性 3：ReAct 智能体与工具调用）。
// *
// * <p>封装户型图分析与产品推荐能力为 AgentScope 2.0 的 @Tool 方法，
// * 由 LLM 通过 ReAct 推理循环自主调用。</p>
// *
// * <p><b>调用链路</b>：</p>
// * <ol>
// *   <li>用户上传户型图图片 + 输入需求文本 → /api/chat/stream 接口</li>
// *   <li>ChatService 构建多模态 Msg（图片 DataBlock + 文本），
// *       同时将图片注入 SessionContext.images</li>
// *   <li>HarnessAgent 的 LLM 看到图片，识别为户型图 + 用户问产品方案</li>
// *   <li>LLM 调用本工具的 analyze_floor_plan 方法</li>
// *   <li>本工具从 SessionContext 获取用户上传的图片（ImageInfo 列表），
// *       调用 FloorPlanPort 完成完整的户型图分析 + 产品推荐 + 预算约束</li>
// *   <li>返回 ToolResultVO（routePath=/floor-plan/result），前端渲染户型图 + 设备标注</li>
// * </ol>
// *
// * <p><b>图片获取方式</b>：LLM 无法通过 @ToolParam 传递图片数据（Base64 太大），
// * 因此图片由 ChatService 注入到 SessionContext.images 中，
// * 本工具直接从上下文获取，LLM 只需传递 userRequirement 文本参数。</p>
// *
// * @author zqs
// * @since 2.0.0
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class FloorPlanTool extends AbstractTool {
//
//    /** 户型图方案端口（六边形架构端口，FloorPlanServiceImpl 实现） */
//    private final FloorPlanPort floorPlanPort;
//
//    /**
//     * 分析户型图并生成产品推荐方案。
//     *
//     * <p>本工具完成以下流程：
//     * <ol>
//     *   <li>从 SessionContext 获取用户上传的户型图图片（Base64 编码）</li>
//     *   <li>调用 FloorPlanPort 上传图片到 MinIO、调用 qwen-vl-max 识别房间、
//     *       搜索 HDL 产品、应用预算约束、分配坐标位置</li>
//     *   <li>返回包含完整方案数据的 ToolResultVO（routePath=/floor-plan/result）</li>
//     * </ol>
//     *
//     * <p><b>前端渲染</b>：routePath=/floor-plan/result 时，前端 DynamicPage 渲染
//     * FloorPlanPage 组件，展示原户型图图片 + 设备标注（XY 坐标）+ 清单下载 + 加购按钮。</p>
//     *
//     * @param userRequirement 用户自定义需求文本（如"客厅、厨房、卧室分别挑选智能设备，总价不高于10万"）
//     * @param runtimeContext  运行时上下文（自动注入，含会话信息和用户上传的图片）
//     * @return 工具结果 VO（data 为 FloorPlanVO，含 rooms/products/positions/totalPrice）
//     */
//    @Tool(name = "analyze_floor_plan",
//            description = "分析户型图并生成智能家居产品推荐方案。"
//                    + "使用场景：用户上传户型图图片并要求生成产品方案时调用此工具。"
//                    + "功能：识别户型图中的房间（客厅/卧室/厨房等）→ 根据用户需求推荐 HDL 智能设备 → "
//                    + "应用预算约束 → 为每个设备分配户型图坐标位置 → 生成可下载的方案清单。"
//                    + "注意：图片数据从会话上下文自动获取，无需在参数中传递图片，只需传递用户需求文本。",
//            readOnly = true)
//    public ToolResultVO analyzeFloorPlan(
//            @ToolParam(name = "userRequirement", required = true,
//                    description = "用户的原始需求文本，必须原样传递用户输入，禁止改写、扩写或编造需求。"
//                            + "例如用户说'总价不高于八千'就传'总价不高于八千'，不得改为'总价不高于10万'。"
//                            + "包含房间选择、产品类别偏好、预算约束等信息") String userRequirement,
//            RuntimeContext runtimeContext) {
//
//        SessionContext sessionContext = resolveSessionContext(runtimeContext);
//
//        // 从会话上下文获取用户上传的图片（由 ChatService 注入）
//        List<ImageInfo> images = sessionContext.getImages();
//        if (images == null || images.isEmpty()) {
//            log.warn("[FloorPlanTool] 会话上下文中无图片数据，无法分析户型图");
//            return ToolResultVO.failure(BusinessConst.CODE_PARAM_MISSING,
//                    BusinessConst.MSG_FLOOR_PLAN_NO_IMAGE);
//        }
//
//        log.info("[FloorPlanTool] 开始分析户型图: imageCount={}, requirement={}, userId={}",
//                images.size(), userRequirement, sessionContext.getUserId());
//
//        // 调用端口实现（FloorPlanServiceImpl）完成完整的户型图分析 + 产品推荐
//        ToolResultVO result = floorPlanPort.analyzeFloorPlan(images, userRequirement, sessionContext);
//
//        log.info("[FloorPlanTool] 户型图分析完成: success={}, routePath={}",
//                result.isSuccess(), result.getRoutePath());
//        return result;
//    }
//}
