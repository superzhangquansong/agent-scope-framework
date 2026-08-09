package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.vo.ToolResultVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 产品工具。
 *
 * <p>封装 HDL 商城产品查询能力。用户询问产品详情、规格、配件、价格时，
 * LLM 通过本工具获取产品信息。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTool extends AbstractTool {

    /** HDL 业务 API 端口 */
    private final HdlApiPort hdlApiPort;

    /**
     * 搜索产品列表。
     *
     * @param productName    产品名称或关键词
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 为产品列表 JSON）
     */
    @Tool(name = "search_product",
            description = "搜索 HDL 商城产品列表。按产品名关键词搜索，返回匹配的产品列表（含 productId、产品名、价格、SKU 等）。"
                    + "使用场景：用户询问产品价格、产品推荐、按关键词搜索产品时调用此工具。"
                    + "例如'方悦怎么卖'、'砚价格'、'调光灯多少钱'、'有什么面板'等含价格/产品名关键词的查询。"
                    + "参数来源要求：productName 从用户输入提取。"
                    + "禁止事项：禁止编造 productId，加入购物车前必须先通过本工具或 query_product_detail 获取真实 skuId。",
            readOnly = true)
    public ToolResultVO searchProduct(
            @ToolParam(name = "productName", required = true,
                    description = "产品名称或关键词。示例：Lite RGB、调光面板、智能开关。从用户输入提取") String productName,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[ProductTool] 搜索产品: productName={}, userId={}",
                productName, sessionContext.getUserId());
        return hdlApiPort.searchProductList(productName, sessionContext);
    }

    /**
     * 查询产品详情。
     *
     * @param productId      产品 ID 或产品名
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 为产品详情 JSON）
     */
    @Tool(name = "query_product_detail",
            description = "查询 HDL 商城单个产品详情。传入产品 ID 或产品名均可。若传入产品名（如'方悦'），系统自动搜索并转换为真实 productId 后查询详情。返回产品名称、SKU 品号列表（含规格）、配件列表、产品参数等。"
                    + "使用场景：用户明确询问某个具体产品的详细规格、配件清单、技术参数时调用（需已知 productId 或产品名）。"
                    + "注意：产品价格查询请用 search_product 工具（返回列表含价格），不要用本工具。"
                    + "参数来源要求：productId 优先从 search_product 返回结果获取，也可直接传入产品名由系统自动转换。"
                    + "禁止事项：禁止编造 skuId，加入购物车所需的 skuId 必须来自本工具返回的 skuList。",
            readOnly = true)
    public ToolResultVO queryProductDetail(
            @ToolParam(name = "productId", required = true,
                    description = "产品ID。示例：12345。数字ID或中文名均可，从search_product返回结果获取") String productId,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[ProductTool] 查询产品详情: productId={}, userId={}",
                productId, sessionContext.getUserId());
        return hdlApiPort.queryProductDetail(productId, sessionContext);
    }
}
