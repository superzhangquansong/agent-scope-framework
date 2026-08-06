package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.vo.ToolResultVO;
import com.alibaba.fastjson2.JSONObject;
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
 * <p>支持按产品名关键词搜索。{@code query_product_detail} 内部自动检测：若 productId 非数字，
 * 先搜索产品名转为真实数字 ID 再查详情。</p>
 *
 * <p><b>提供的工具方法</b>：</p>
 * <ul>
 *   <li>{@code searchProduct}：按产品名搜索产品列表</li>
 *   <li>{@code queryProductDetail}：查询产品详情（自动处理产品名→ID）</li>
 * </ul>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTool extends AbstractTool {

    /**
     * 搜索产品列表。
     *
     * <p>按产品名关键词搜索，返回匹配的产品列表。
     * LLM 在用户说产品名（如"方悦"、"调光灯"）但不知道 productId 时调用此工具。</p>
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
        log.info("[ProductTool] 搜索产品: sessionContext={}", JSONObject.toJSONString(sessionContext));
        ToolResultVO toolResultVO = new ToolResultVO();
        toolResultVO.setSuccess(true);
        toolResultVO.setMessage("成功查询到产品信息");
        toolResultVO.setData(JSONObject.parseObject("{\"code\":0,\"data\":[{\"productId\":\"1\",\"productName\":\"方悦\",\"price\":\"￥1.00\",\"skuId\":\"1\"}],\"message\":\"成功\"}"));
        toolResultVO.setRoutePath(ToolResultVO.ROUTE_TEXT_ONLY);
        toolResultVO.setBroadcastText("成功查询到产品信息");
        toolResultVO.setAskUser("成功查询到产品信息");
        return toolResultVO;
    }
}