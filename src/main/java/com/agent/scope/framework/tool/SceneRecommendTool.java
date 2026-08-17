package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.service.DeviceContextService;
import com.agent.scope.framework.service.SceneRecommendService;
import com.agent.scope.framework.vo.SceneRecommendVO;
import com.agent.scope.framework.vo.ToolResultVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景推荐 LLM 工具。
 *
 * <p>当用户主动询问"有什么场景""推荐个场景"时，LLM 通过 ReAct 推理调用此工具。
 * 工具返回推荐方案列表（含场景名、效果描述、匹配设备、具体动作），
 * LLM 将方案展示给用户，用户确认后才调用 create_scene_from_template 创建场景。</p>
 *
 * <p>交互流程：</p>
 * <ol>
 *   <li>用户："有什么场景推荐？"</li>
 *   <li>LLM 调用 recommend_scene → 返回推荐方案</li>
 *   <li>LLM 回复："为您推荐以下场景：1.观影模式（灯光调暗...）2.睡眠模式（...），要创建哪个？"</li>
 *   <li>用户："创建观影模式"</li>
 *   <li>LLM 调用 create_scene_from_template → 创建场景</li>
 * </ol>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneRecommendTool extends AbstractTool {

    /**
     * 场景推荐结果路由（前端 SceneRecommendPage 据此渲染推荐卡片）
     */
    private static final String ROUTE_SCENE_RECOMMEND = "/scene/recommend";

    /**
     * 场景推荐成功播报文本
     */
    private static final String BROADCAST_SCENE_RECOMMEND = "为您推荐以下场景方案";

    /**
     * 场景推荐引擎
     */
    private final SceneRecommendService sceneRecommendService;

    /**
     * 设备上下文服务（读取 Redis 缓存的设备列表）
     */
    private final DeviceContextService deviceContextService;

    /**
     * 推荐场景方案。
     *
     * <p>根据用户已有设备组合匹配场景模板，返回推荐方案列表。
     * 推荐结果仅展示给用户，不执行设备控制。用户确认后调用 create_scene_from_template 创建场景。</p>
     *
     * @param runtimeContext 运行时上下文（自动注入，含 houseId）
     * @return 工具结果 VO（data 为推荐方案列表）
     */
    @Tool(name = "recommend_scene",
            description = """
                    根据用户已有设备推荐场景方案。用户问'有什么场景''推荐个场景''有什么场景推荐'时调用此工具。
                    返回推荐方案列表（含场景名、效果描述、匹配的设备列表、每个设备的具体控制动作）。
                    重要：调用此工具后必须将推荐方案展示给用户，等待用户确认后再调用 create_scene_from_template 创建场景。
                    禁止跳过用户确认直接创建场景。
                    """,
            readOnly = true)
    public ToolResultVO recommendScene(RuntimeContext runtimeContext) {
        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        String houseId = sessionContext.getHouseId();
        log.info("[SceneRecommendTool] 推荐场景: userId={}, houseId={}",
                sessionContext.getUserId(), houseId);

        if (houseId == null || houseId.isBlank()) {
            return ToolResultVO.failure(400, "当前房屋 ID 为空，无法推荐场景");
        }

        // 从 Redis 缓存读取设备列表
        List<DeviceContextService.DeviceBrief> devices = deviceContextService.getCachedDeviceBriefs(houseId);
        if (devices.isEmpty()) {
            return ToolResultVO.failure(404, "设备列表为空，请先调用 query_device_list 获取设备列表后再推荐场景");
        }

        List<SceneRecommendVO> recommends = sceneRecommendService.recommend(devices);
        if (recommends.isEmpty()) {
            return ToolResultVO.success("当前设备组合暂无匹配的场景模板", List.of());
        }

        // 构建展示文本，引导 LLM 将推荐方案展示给用户
        StringBuilder displayText = new StringBuilder("为您找到以下场景推荐方案：\n");
        for (int i = 0; i < recommends.size(); i++) {
            SceneRecommendVO rec = recommends.get(i);
            displayText.append(i + 1).append(". ").append(rec.getSceneName())
                    .append("（").append(rec.getDescription()).append("）\n");
            for (SceneRecommendVO.DeviceActionDetail action : rec.getDeviceActions()) {
                displayText.append("   - ").append(action.getDeviceName())
                        .append(": ").append(action.getActionSummary()).append("\n");
            }
        }
        displayText.append("\n请将以上方案展示给用户，询问用户要创建哪个场景。用户确认后调用 create_scene_from_template 创建。");

        // 包装为前端 SceneRecommendPage 期望的 {recommends: [...]} 结构，并携带路由
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("recommends", recommends);
        return ToolResultVO.success(displayText.toString(), resultData,
                ROUTE_SCENE_RECOMMEND, BROADCAST_SCENE_RECOMMEND);
    }
}
