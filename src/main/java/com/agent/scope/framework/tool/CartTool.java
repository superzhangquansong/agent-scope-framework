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
 * 购物车工具。
 *
 * <p>封装 HDL 商城购物车的加入和查询能力。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartTool extends AbstractTool {

    /** HDL 业务 API 端口 */
    private final HdlApiPort hdlApiPort;

    /**
     * 加入购物车。
     *
     * @param skuId          SKU ID
     * @param productId      产品 ID
     * @param quantity       购买数量
     * @param erpNo          ERP 编号（可选）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO
     */
    @Tool(name = "add_to_cart",
            description = "将指定 SKU 加入 HDL 商城购物车。"
                    + "使用场景：用户说'加入购物车'、'买 2 个方悦'、'加入 1 个调光灯'时调用。"
                    + "参数来源要求：skuId 和 productId 必须来自 search_product 或 query_product_detail 的返回结果。"
                    + "禁止事项：禁止编造 skuId 或 productId。",
            concurrencySafe = false)
    public ToolResultVO addToCart(
            @ToolParam(name = "skuId", required = true,
                    description = "SKU ID。示例：SKU12345。从query_product_detail返回结果获取") String skuId,
            @ToolParam(name = "productId", required = true,
                    description = "产品ID。示例：12345。从query_product_detail返回结果获取") String productId,
            @ToolParam(name = "quantity", required = true,
                    description = "购买数量（1-99）。示例：1") int quantity,
            @ToolParam(name = "erpNo", required = false,
                    description = "ERP编号，可选") String erpNo,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[CartTool] 加入购物车: skuId={}, productId={}, quantity={}, userId={}",
                skuId, productId, quantity, sessionContext.getUserId());
        return hdlApiPort.addCart(skuId, productId, quantity, erpNo, sessionContext);
    }

    /**
     * 查询购物车列表。
     *
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO
     */
    @Tool(name = "query_cart_list",
            description = "查询 HDL 商城购物车列表。返回购物车中所有商品信息（含产品名、数量、价格等）。"
                    + "使用场景：用户说'看看购物车'、'购物车有什么'时调用。",
            readOnly = true)
    public ToolResultVO queryCartList(RuntimeContext runtimeContext) {
        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[CartTool] 查询购物车列表: userId={}", sessionContext.getUserId());
        return hdlApiPort.queryCartList(sessionContext);
    }
}
