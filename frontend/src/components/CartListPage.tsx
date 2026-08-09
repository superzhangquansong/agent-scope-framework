import { useState } from 'react';
import { ShoppingCart, ChevronLeft, ChevronRight, Box } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';

/** 前端分页每页条数 */
const PAGE_SIZE = 5;

interface CartItem {
  /** HDL 原始字段名 shoppingCartsId */
  shoppingCartsId?: string;
  /** 兼容字段 cartItemId（前端契约字段名） */
  cartItemId?: string;
  skuId?: string;
  productId?: string;
  /** HDL 原始字段名 productNameCn */
  productNameCn?: string;
  /** 兼容字段 productName（前端契约字段名） */
  productName?: string;
  productModel?: string;
  /** HDL 原始字段名 skuNum */
  skuNum?: number | string;
  /** 兼容字段 quantity（前端契约字段名） */
  quantity?: number | string;
  price?: number | string;
  unitPrice?: number | string;
  marketPrice?: number | string;
  totalPrice?: number | string;
  skuImage?: string;
  erpNo?: string;
  unitName?: string;
  /** HDL 原始字段名 specsData */
  specsData?: string;
  /** 兼容字段 specsDesc（前端契约字段名） */
  specsDesc?: string;
  stock?: number | string;
  supplierName?: string;
  [k: string]: unknown;
}

interface CartListData {
  /** CartListVo 外层字段名 items（SSE 和 REST 通道一致） */
  items?: CartItem[];
  /** 兼容字段 list（HDL 原始外层字段名） */
  list?: CartItem[];
  /** CartListVo 外层字段名 total */
  total?: number;
  /** 兼容字段 totalCount（HDL 原始外层字段名） */
  totalCount?: number;
  totalAmount?: number | string;
  totalPage?: string;
  pageNo?: string;
  pageSize?: string;
}

/**
 * 购物车列表页
 *
 * 接收 data={items, total, totalAmount, totalPage, pageNo, pageSize}，渲染购物车商品列表 + 底部总金额。
 * 前端分页 5 条/页（基于当前 items 数组），玻璃态卡片样式。
 * 每项展示：商品图、名称、型号、品号(erpNo)、规格(specsDesc)、单价、数量、单位、小计(totalPrice)。
 */
export default function CartListPage({ data }: RoutePageProps) {
  const payload = data as unknown as CartListData;
  const items = payload.items ?? payload.list ?? [];
  const total = payload.total ?? payload.totalCount ?? items.length;
  const totalAmount = payload.totalAmount;

  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const start = (currentPage - 1) * PAGE_SIZE;
  const pageItems = items.slice(start, start + PAGE_SIZE);

  return (
    <div className="animate-fade-in space-y-4">
      {/* 标题栏 */}
      <div className="flex items-center gap-2 text-sm">
        <ShoppingCart size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">购物车</span>
        <span className="text-xs text-slate-400 font-mono">共 {total} 件 · 第 {currentPage}/{totalPages} 页</span>
      </div>

      {items.length === 0 ? (
        <div className="glass rounded-xl px-4 py-8 text-center text-sm text-slate-400">
          购物车为空
        </div>
      ) : (
        <div className="space-y-2.5">
          {pageItems.map((item, i) => {
            const cartItemId = item.shoppingCartsId ?? item.cartItemId;
            const productName = item.productNameCn ?? item.productName;
            const quantity = Number(item.skuNum ?? item.quantity ?? 1);
            const specsData = item.specsData ?? item.specsDesc;
            const price = item.price ?? item.unitPrice;
            const itemTotal = item.totalPrice != null
              ? Number(item.totalPrice)
              : (price != null ? Number(price) * quantity : undefined);

            return (
              <div
                key={cartItemId ?? i}
                className="glass rounded-xl p-3 hover-neon card-shadow flex items-center gap-3 animate-card-in"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                {/* 商品图 */}
                <div className="flex-shrink-0 w-16 h-16 rounded-lg bg-neon-purple/10 border border-neon-purple/20 flex items-center justify-center text-neon-purple overflow-hidden">
                  {item.skuImage ? (
                    <img src={item.skuImage} alt="" className="w-full h-full object-cover rounded-lg" />
                  ) : (
                    <Box size={24} />
                  )}
                </div>
                {/* 商品信息 */}
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-slate-100 truncate">
                    {productName ?? '未命名商品'}
                  </div>
                  {/* 型号 + 品号 */}
                  <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                    {item.productModel && (
                      <span className="text-[11px] text-slate-400 font-mono">
                        型号：{String(item.productModel)}
                      </span>
                    )}
                    {item.erpNo && (
                      <span className="text-[11px] text-neon-cyan/80 font-mono">
                        品号：{String(item.erpNo)}
                      </span>
                    )}
                  </div>
                  {/* 规格描述 */}
                  {specsData && (
                    <div className="text-[11px] text-slate-500 font-mono mt-0.5 truncate">
                      规格：{String(specsData)}
                    </div>
                  )}
                  {/* 价格 + 数量 + 单位 + 小计 */}
                  <div className="flex items-center justify-between mt-1.5">
                    <div className="flex items-center gap-2">
                      <span className="text-sm neon-text-purple font-semibold">
                        ¥{price != null ? String(price) : '-'}
                      </span>
                      {item.marketPrice != null && Number(item.marketPrice) !== Number(price) && (
                        <span className="text-[10px] text-slate-500 line-through font-mono">
                          ¥{String(item.marketPrice)}
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2 text-[11px] text-slate-400 font-mono">
                      <span>×{quantity}{item.unitName ? ` ${item.unitName}` : ''}</span>
                      {itemTotal != null && (
                        <span className="text-neon-amber font-semibold">
                          小计 ¥{String(itemTotal)}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 底部总金额 */}
      {totalAmount != null && (
        <div className="glass-strong rounded-xl p-4 card-shadow flex items-center justify-between sticky bottom-2">
          <span className="text-xs text-slate-300 font-mono">合计金额</span>
          <span className="text-xl font-bold neon-text-purple">¥{String(totalAmount)}</span>
        </div>
      )}

      {/* 分页控件 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 pt-2">
          <button
            onClick={() => setPage(p => Math.max(1, p - 1))}
            disabled={currentPage <= 1}
            className="glass-panel rounded-lg p-2 text-neon-cyan hover-neon disabled:opacity-40 disabled:cursor-not-allowed transition-all"
          >
            <ChevronLeft size={16} />
          </button>
          <span className="text-xs text-slate-300 font-mono">
            {currentPage} / {totalPages}
          </span>
          <button
            onClick={() => setPage(p => Math.min(totalPages, p + 1))}
            disabled={currentPage >= totalPages}
            className="glass-panel rounded-lg p-2 text-neon-cyan hover-neon disabled:opacity-40 disabled:cursor-not-allowed transition-all"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      )}
    </div>
  );
}
