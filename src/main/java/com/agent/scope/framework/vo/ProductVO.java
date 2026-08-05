package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 产品信息视图对象。
 * <p>
 * 对应云端商城产品搜索与产品详情接口的返回数据结构。
 * 包含产品 ID、名称、价格、SKU ID 等核心字段。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class ProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 产品 ID（大数转字符串） */
    private String productId;

    /** 产品名称 */
    private String productName;

    /** 产品价格 */
    private BigDecimal price;

    /** SKU ID（大数转字符串） */
    private String skuId;
}
