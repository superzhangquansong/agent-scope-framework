/**
 * @file 产品工具函数
 *
 * 提供 HDL 产品价格解析函数 pickPrice，
 * 用于从产品/SKU 的多个价格候选字段中按优先级选取有效价格。
 *
 * 来源：从 ProductListPage.tsx 和 ProductDetailPage.tsx 中提取的重复函数实现（两处实现完全一致）。
 */

/**
 * 含价格候选字段的对象结构。
 *
 * HDL 产品/SKU 接口返回的价格字段包含 4 个候选，
 * 不同接口（mallList / getMallProductInfo）和不同链路（REST / SSE）字段名可能不一致，
 * 因此统一通过本接口描述所有可能的价格字段。
 */
export interface PriceCandidate {
  /** 市场价（最高优先级） */
  marketPrice?: string | number;
  /** 统一价 */
  unifiedPrice?: string | number;
  /** 普通价格 */
  price?: string | number;
  /** 渠道价（最低优先级） */
  channelPrice?: string | number;
}

/**
 * 解析价格：按优先级 marketPrice > unifiedPrice > price > channelPrice 选取第一个有效价格。
 *
 * 「有效」定义：
 *  - 非 null 且非空字符串
 *  - 可转为数字且大于 0
 *
 * 全部候选都为 0/null/空时返回空字符串（前端展示「询价」）。
 *
 * 使用示例：
 * ```ts
 * const price = pickPrice(product); // '99.00' 或 ''
 * {price ? `¥${price}` : '询价'}
 * ```
 *
 * @param p 含价格候选字段的对象（产品或 SKU）
 * @returns 价格字符串（如 '99.00'）；无有效价格时返回空字符串
 */
export function pickPrice(p: PriceCandidate): string {
  const candidates = [p.marketPrice, p.unifiedPrice, p.price, p.channelPrice];
  for (const c of candidates) {
    if (c == null || c === '') continue;
    const n = Number(c);
    if (!isNaN(n) && n > 0) return String(c);
  }
  return '';
}
