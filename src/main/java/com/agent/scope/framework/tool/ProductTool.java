package com.agent.scope.framework.tool;

import com.agent.scope.framework.client.HdlApiClient;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.vo.ToolResultVO;
import io.agentscope.core.agent.RuntimeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 产品检索工具。
 * <p>
 * 提供产品搜索与产品详情查询能力。通过 {@link HdlApiClient} 调用云端商城服务接口，
 * 使用 SessionContext 中的 accessToken 进行鉴权。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTool extends AbstractTool {

    /**
     * 云端 API 客户端
     */
    private final HdlApiClient hdlApiClient;

    /**
     * 搜索产品。
     * <p>
     * 根据产品名称关键词搜索云端商城产品列表。
     * routePath=/product/list，前端据此渲染产品列表页。
     * </p>
     *
     * @param sessionContext 会话上下文（框架自动注入）
     * @param productName    产品名称关键词
     * @return 工具执行结果，data 为产品列表
     */
    @Tool(description = "根据产品名称搜索商城产品列表")
    public ToolResultVO search_product(RuntimeContext runtimeContext,
                                       @ToolParam(description = "产品名称关键词") String productName) {
        SessionContext sessionContext =runtimeContext.get(CTX_KEY_SESSION_CONTEXT);
        if (sessionContext == null || sessionContext.getAccessToken() == null) {
            ToolResultVO fail = ToolResultVO.fail("用户未登录，请先完成账号登录");
            recordResult("search_product", fail);
            return fail;
        }
        if (productName == null || productName.isBlank()) {
            ToolResultVO fail = ToolResultVO.fail("产品名称不能为空");
            recordResult("search_product", fail);
            return fail;
        }

        // 调用云端 API 搜索产品
        Map<String, Object> searchResult = hdlApiClient.searchProduct(
                sessionContext.getAccessToken(), productName);

        ToolResultVO result = ToolResultVO.success(searchResult, "产品搜索完成");
        result.setRoutePath("/product/list");
        recordResult("search_product", result);
        return result;
    }

    /**
     * 查询产品详情。
     * <p>
     * 根据产品 ID 查询产品详细信息，包含 SKU 品号与配件信息。
     * </p>
     *
     * @param sessionContext 会话上下文（框架自动注入）
     * @param productId      产品 ID
     * @return 工具执行结果，data 为产品详情
     */
    @Tool(description = "根据产品ID查询产品详细信息，包含SKU品号与配件信息")
    public ToolResultVO query_product_detail(RuntimeContext runtimeContext,
                                             @ToolParam(description = "产品ID") String productId) {
        SessionContext sessionContext =runtimeContext.get(CTX_KEY_SESSION_CONTEXT);
        if (sessionContext == null || sessionContext.getAccessToken() == null) {
            ToolResultVO fail = ToolResultVO.fail("用户未登录，请先完成账号登录");
            recordResult("query_product_detail", fail);
            return fail;
        }
        if (productId == null || productId.isBlank()) {
            ToolResultVO fail = ToolResultVO.fail("产品ID不能为空");
            recordResult("query_product_detail", fail);
            return fail;
        }

        // 调用云端 API 获取产品详情
        Map<String, Object> detail = hdlApiClient.getProductDetail(
                sessionContext.getAccessToken(), productId, null);

        ToolResultVO result = ToolResultVO.success(detail, "产品详情查询完成");
        result.setRoutePath("/product/detail");
        recordResult("query_product_detail", result);
        return result;
    }
}
