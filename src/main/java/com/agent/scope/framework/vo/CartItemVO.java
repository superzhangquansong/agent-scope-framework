package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车项视图对象。
 * <p>
 * 对应云端购物车列表接口中单个商品项的返回数据结构。
 * 包含产品 ID、SKU ID、产品名称、数量、单价等核心字段。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class CartItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 产品 ID（大数转字符串） */
    private String productId;

    /** SKU ID（大数转字符串） */
    private String skuId;

    /** 产品名称 */
    private String productName;

    /** 购买数量 */
    private Integer quantity;

    /** 单价 */
    private BigDecimal price;

    /** 小计金额（单价 * 数量） */
    private BigDecimal subtotal;
}
