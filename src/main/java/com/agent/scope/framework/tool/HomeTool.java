package com.agent.scope.framework.tool;

import com.agent.scope.framework.client.HdlApiClient;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.vo.ToolResultVO;
import io.agentscope.core.agent.RuntimeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 房屋管理工具。
 * <p>
 * 提供用户房屋列表查询能力。通过 {@link HdlApiClient} 调用云端房屋列表接口，
 * 使用 SessionContext 中的 accessToken 进行鉴权。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeTool extends AbstractTool {

    /**
     * 云端 API 客户端
     */
    private final HdlApiClient hdlApiClient;

    /**
     * 查询用户的房屋列表。
     * <p>
     * 从会话上下文获取 accessToken，调用云端房屋列表接口，
     * 返回用户所有房屋信息。routePath=/home/list，前端据此渲染房屋选择页。
     * </p>
     *
     * @param sessionContext 会话上下文（框架自动注入）
     * @return 工具执行结果，data 为房屋列表
     */
    @Tool(description = "查询用户的所有房屋列表，返回房屋ID、名称、类型、设备数量等信息")
    public ToolResultVO query_home_list(RuntimeContext runtimeContext) {
        SessionContext sessionContext =runtimeContext.get(CTX_KEY_SESSION_CONTEXT);
        if (sessionContext == null || sessionContext.getAccessToken() == null) {
            ToolResultVO fail = ToolResultVO.fail("用户未登录，请先完成账号登录");
            recordResult("query_home_list", fail);
            return fail;
        }

        // 调用云端 API 获取房屋列表
        List<Map<String, Object>> homeList = hdlApiClient.getHomeList(sessionContext.getAccessToken());

        ToolResultVO result = ToolResultVO.success(homeList,
                "查询到 " + (homeList != null ? homeList.size() : 0) + " 个房屋");
        result.setRoutePath("/home/list");
        result.setBroadcastText("已为您查询到房屋列表");
        recordResult("query_home_list", result);
        return result;
    }
}
