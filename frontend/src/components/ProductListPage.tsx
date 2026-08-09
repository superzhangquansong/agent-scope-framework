import { useState, useEffect, useMemo } from 'react';
import { Package, ChevronLeft, ChevronRight, Info, RefreshCw } from 'lucide-react';
import ImageCarousel from './ImageCarousel';
import ProductDetailPage from './ProductDetailPage';
import { getProductDetail, searchProduct, type ProductDetailData } from '../api/client';
import type { RoutePageProps } from './DynamicPage';
import { pickPrice } from '../shared/utils';

/** 产品列表每页条数（前端客户端分页） */
const PAGE_SIZE = 6;

/** 产品数据结构（含价格候选字段） */
interface Product {
  productId?: string;
  /** HDL 原始字段名 productNameCn */
  productNameCn?: string;
  /** 兼容字段 productName（前端契约字段名） */
  productName?: string;
  productModel?: string;
  productImage?: string;
  /** HDL 原始字段名 productImageUlr：产品图片 URL（mallList 返回） */
  productImageUlr?: string;
  /** HDL 原始字段名 images：List<String> 原始 URL 字符串数组 */
  images?: unknown[];
  /**
   * HDL 原始字段名 imagesList：List<ProductImages> 对象数组
   * 也可能是字符串数组（兼容场景）
   */
  imagesList?: unknown[];
  /** 兼容字段 productImages（前端契约字段名） */
  productImages?: unknown[];
  price?: string | number;
  marketPrice?: string | number;
  unifiedPrice?: string | number;
  channelPrice?: string | number;
  /** HDL 原始字段名 unitName：产品单位（如"个"/"套"） */
  unitName?: string;
  /** HDL 原始字段名 protocolTypeName：协议类型名称 */
  protocolTypeName?: string;
  /** HDL 原始字段名 brandNameCn：品牌名称 */
  brandNameCn?: string;
  /** HDL 原始字段名 supplierName：供应商名称 */
  supplierName?: string;
  /** SPK 标签 */
  spk?: string;
  protocol?: string;
  categoryName?: string;
  skuCount?: number;
  [k: string]: unknown;
}

/** 产品列表数据结构 */
interface ProductListData {
  /** SSE 通道外层字段名 products（behaviors.yml 映射） */
  products?: Product[];
  /** 兼容字段 list（HDL 原始外层字段名） */
  list?: Product[];
  total?: number;
  pageNo?: number;
  pageSize?: number;
  /** 搜索关键词（精简报文模式：后端 searchProductList 返回 {keyword: "方悦"}） */
  keyword?: string;
}

/**
 * 产品列表页
 *
 * - 接收 data={products, total}，渲染产品卡片网格
 * - 每个卡片：ImageCarousel（80x80）+ productName + productModel + productId（小字灰色）+ price
 * - 价格为 0 或缺失时显示"询价"
 * - 点击卡片调用 getProductDetail REST API，内联展示 ProductDetailPage
 * - 前端客户端分页（6 条/页）
 * - 展示 SPK 标签 / 类目标签
 */
export default function ProductListPage({ data }: RoutePageProps) {
  const payload = data as unknown as ProductListData;
  // SSE 报文携带的产品列表（精简报文模式下为空，需通过 REST API 补全）
  // 注意：payload.products / payload.list 可能为 null、undefined 或非数组值，
  // 必须用 Array.isArray 严格校验，否则后续 slice 调用会抛出 "allProducts.slice is not a function"
  const initialProducts = useMemo(() => {
    const candidates = [payload.products, payload.list];
    for (const c of candidates) {
      if (Array.isArray(c)) return c;
    }
    return [];
  }, [payload.products, payload.list]);
  // SSE 报文携带的搜索关键词（后端 searchProductList 返回 {keyword: "方悦"}）
  const keyword = useMemo(() => (payload.keyword as string) ?? '', [payload.keyword]);

  // 本地产品列表状态（REST 拉取后更新）
  const [products, setProducts] = useState<Product[]>(initialProducts);
  const [total, setTotal] = useState<number>(payload.total ?? initialProducts.length);
  // 标记是否正在通过 REST 拉取产品列表
  const [loadingList, setLoadingList] = useState<boolean>(initialProducts.length === 0);

  /**
   * 当 SSE 报文未携带 products 时，通过 /api/product/search REST 接口拉取产品列表。
   * 精简报文模式：后端仅返回 routePath=/product/list + keyword，由前端自行查询产品数据。
   */
  useEffect(() => {
    if (initialProducts.length > 0) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        // 使用后端返回的 keyword 调用 REST API 搜索产品
        const res = await searchProduct({ productName: keyword });
        if (cancelled) return;
        if (res.success && res.data) {
          // HDL /crm-wisdom/distributors/product/mallList 返回 {list: [...]} 结构
          // 注意：dataObj.list 可能为 null、undefined 或非数组值，
          // 必须用 Array.isArray 校验，避免 setProducts 接收非数组导致 slice 报错
          const dataObj = res.data as Record<string, unknown>;
          const list = Array.isArray(dataObj.list) ? (dataObj.list as Product[]) : [];
          setProducts(list);
          setTotal(list.length);
        }
      } catch {
        // 静默失败，保留空列表
      } finally {
        if (!cancelled) setLoadingList(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const allProducts = products;

  // 前端客户端分页
  const totalPages = Math.max(1, Math.ceil(allProducts.length / PAGE_SIZE));
  const [page, setPage] = useState(1);
  const [pageInput, setPageInput] = useState('1');

  // 数据变化时重置页码（如切换查询关键词后产品列表更新）
  useEffect(() => {
    setPage(1);
    setPageInput('1');
  }, [allProducts]);

  const currentPage = Math.min(page, totalPages);
  const start = (currentPage - 1) * PAGE_SIZE;
  const pageItems = allProducts.slice(start, start + PAGE_SIZE);

  const goToPage = (p: number) => {
    const target = Math.min(Math.max(1, p), totalPages);
    if (target === currentPage) return;
    setPage(target);
    setPageInput(String(target));
  };

  // 内联产品详情
  const [inlineDetail, setInlineDetail] = useState<ProductDetailData | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [detailError, setDetailError] = useState<string | undefined>();

  /** 点击产品卡片：调用 getProductDetail REST API 内联展示详情 */
  const handleClickProduct = async (p: Product) => {
    if (loadingDetail) return;
    const productId = p.productId;
    if (!productId) return;
    setLoadingDetail(true);
    setDetailError(undefined);
    try {
      const res = await getProductDetail({ productId });
      if (res.success && res.data) {
        // 合并原始产品信息（确保 productNameCn/productModel 等字段存在）
        const detail: ProductDetailData = {
          ...(res.data as ProductDetailData),
          productId: (res.data as ProductDetailData).productId ?? productId,
          productNameCn: (res.data as ProductDetailData).productNameCn ?? p.productNameCn,
          productModel: (res.data as ProductDetailData).productModel ?? p.productModel,
        };
        setInlineDetail(detail);
      } else {
        setDetailError(res.message || '获取产品详情失败');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setDetailError(`获取产品详情失败：${msg}`);
    } finally {
      setLoadingDetail(false);
    }
  };

  // 内联展示产品详情时，直接渲染 ProductDetailPage
  if (inlineDetail) {
    return (
      <div className="animate-fade-in space-y-3">
        <ProductDetailPage data={inlineDetail} onBack={() => setInlineDetail(null)} />
      </div>
    );
  }

  return (
    <div className="animate-fade-in space-y-4">
      {/* 标题栏 */}
      <div className="flex items-center gap-2 text-sm">
        <Package size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">产品列表</span>
        <span className="text-xs text-slate-400 font-mono">共 {total} 个</span>
      </div>

      {/* 详情加载错误提示 */}
      {detailError && (
        <div className="text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded px-2.5 py-1.5">
          {detailError}
        </div>
      )}

      {allProducts.length === 0 ? (
        <div className="glass rounded-xl px-4 py-8 text-center text-sm text-slate-400">
          {loadingList ? (
            <span className="flex items-center justify-center gap-2">
              <RefreshCw size={14} className="animate-spin text-cyan-400" />
              正在查询产品...
            </span>
          ) : (
            '暂无产品数据'
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-2.5">
          {pageItems.map((p, i) => {
            // 图片提取：优先级 productImageUlr > images/productImages > imagesList > productImage
            // - productImageUlr：HDL mallList 顶层字段（字符串 URL）
            // - images/productImages：字符串数组（SSE 链路或 HDL images 字段）
            // - imagesList：可能是对象数组 [{productImageUlr, ...}]（REST 链路 HDL 原始格式）或字符串数组
            // - productImage：单图回退
            let imgs: string[] = [];
            if (p.productImageUlr) {
              imgs = [p.productImageUlr];
            } else {
              const rawImgList = p.images ?? p.imagesList ?? p.productImages;
              if (Array.isArray(rawImgList) && rawImgList.length > 0) {
                imgs = rawImgList.map((item) =>
                  typeof item === 'string' ? item : (item as Record<string, unknown>)?.productImageUlr as string ?? '',
                ).filter(Boolean);
              } else if (p.productImage) {
                imgs = [p.productImage];
              }
            }
            const priceStr = pickPrice(p);
            const productName = p.productNameCn ?? p.productName;
            const protocolLabel = p.protocol ?? p.protocolTypeName;
            return (
              <button
                key={p.productId ?? i}
                onClick={() => handleClickProduct(p)}
                disabled={loadingDetail || !p.productId}
                className="group glass rounded-xl p-2.5 hover-neon card-shadow flex gap-3 animate-card-in border border-neon-purple/15 text-left transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                <ImageCarousel
                  images={imgs}
                  alt={productName ?? '产品图片'}
                  className="w-[80px] h-[80px] rounded-lg flex-shrink-0 border border-neon-purple/15"
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-2">
                    <h5 className="text-sm font-medium text-slate-100 truncate group-hover:text-neon-cyan transition-colors">
                      {productName ?? '未命名产品'}
                    </h5>
                    <span className="text-sm neon-text-purple font-semibold flex-shrink-0">
                      {priceStr ? `¥${priceStr}` : '询价'}
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-400 font-mono mt-0.5">
                    型号：{p.productModel || '—'}
                  </p>
                  {/* 品牌 + 单位 + 供应商 */}
                  <p className="text-[10px] text-slate-500 mt-0.5 truncate">
                    {p.brandNameCn ? `品牌：${p.brandNameCn}` : ''}
                    {p.unitName ? ` ｜ 单位：${p.unitName}` : ''}
                    {p.supplierName ? ` ｜ 供应商：${p.supplierName}` : ''}
                  </p>
                  {/* SPK 标签 / 类目标签 / 协议标签 / SKU 数量 */}
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {p.skuCount != null && p.skuCount > 0 && (
                      <span className="rounded bg-neon-cyan/10 px-1.5 py-0.5 text-[10px] text-neon-cyan border border-neon-cyan/20 font-mono">
                        {p.skuCount} 个品号
                      </span>
                    )}
                    {p.spk && (
                      <span className="rounded bg-neon-purple/10 px-1.5 py-0.5 text-[10px] text-neon-purple border border-neon-purple/20 font-mono">
                        {p.spk}
                      </span>
                    )}
                    {protocolLabel && (
                      <span className="rounded bg-slate-700/40 px-1.5 py-0.5 text-[10px] text-slate-300 border border-slate-600/30 font-mono">
                        {protocolLabel}
                      </span>
                    )}
                    {p.categoryName && (
                      <span className="rounded bg-slate-700/40 px-1.5 py-0.5 text-[10px] text-slate-300 border border-slate-600/30 font-mono">
                        {p.categoryName}
                      </span>
                    )}
                  </div>
                </div>
                {/* 右侧详情图标 */}
                <div className="flex-shrink-0 text-slate-500 group-hover:text-neon-cyan transition-colors self-center">
                  {loadingDetail ? <RefreshCw size={14} className="animate-spin" /> : <Info size={14} />}
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* 分页控件（仅多于一页时展示） */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 pt-2 text-xs text-slate-300">
          <button
            onClick={() => goToPage(currentPage - 1)}
            disabled={currentPage <= 1}
            className="glass-panel rounded-lg p-1.5 text-neon-cyan hover-neon disabled:opacity-40 disabled:cursor-not-allowed transition-all"
          >
            <ChevronLeft size={14} />
          </button>
          <span className="tabular-nums font-mono">
            第 {currentPage} / {totalPages} 页
          </span>
          <input
            type="text"
            value={pageInput}
            onChange={(e) => setPageInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                const p = parseInt(pageInput, 10);
                if (!isNaN(p)) goToPage(p);
              }
            }}
            className="w-12 text-center rounded-md glass-panel border border-neon-purple/15 px-1 py-1 text-xs focus:outline-none focus:border-neon-purple/50 text-slate-200 font-mono"
            placeholder={String(currentPage)}
          />
          <button
            onClick={() => { const p = parseInt(pageInput, 10); if (!isNaN(p)) goToPage(p); }}
            className="px-2 py-1 rounded-md glass-panel border border-neon-purple/15 text-slate-300 hover-neon disabled:opacity-40"
          >
            跳转
          </button>
          <button
            onClick={() => goToPage(currentPage + 1)}
            disabled={currentPage >= totalPages}
            className="glass-panel rounded-lg p-1.5 text-neon-cyan hover-neon disabled:opacity-40 disabled:cursor-not-allowed transition-all"
          >
            <ChevronRight size={14} />
          </button>
        </div>
      )}

      {/* 底部提示 */}
      <p className="text-xs text-slate-500 text-center">
        点击产品查看品号规格
      </p>
    </div>
  );
}
