import { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Package, ShoppingCart, Plus, Minus, ArrowLeft, RefreshCw, CheckCircle2, Info,
} from 'lucide-react';
import ImageCarousel from './ImageCarousel';
import CartListPage from './CartListPage';
import { addToCart, getCartList, type ProductDetailData, type ProductSku } from '../api/client';
import { pickPrice } from '../shared/utils';

/** 数量选择器最大值 */
const MAX_QUANTITY = 9999;
/** 数量选择器最小值 */
const MIN_QUANTITY = 1;
/** SKU 上架状态值（1=上架） */
const SHELVES_STATUS_ON = 1;

interface ProductDetailPageProps {
  /** 产品详情数据（由 getProductDetail 返回的 data，或路由直接传入） */
  data: Record<string, unknown> | ProductDetailData;
  /** 返回产品列表的回调（内联展示时使用） */
  onBack?: () => void;
}

/**
 * 产品详情页（含 SKU 列表）
 *
 * - 顶部：产品图片（ImageCarousel 120x120）+ productName + productModel + productId + 底价
 * - SKU 列表：每个 SKU 项含 SKU 图片（80x80）+ erpNo + specsDesc + price + 数量选择器 + 加购按钮
 * - 加购按钮调用 addToCart({skuId, productId, quantity}) REST API
 * - 支持接收 onBack 回调返回产品列表
 */
export default function ProductDetailPage({ data, onBack }: ProductDetailPageProps) {
  const payload = data as ProductDetailData;
  // 兼容 REST（productSkuList，HDL 原始字段名）和 SSE（skus，前端契约字段名）
  const skuList = useMemo(
    () => payload.productSkuList ?? (payload.skus as ProductSku[] | undefined) ?? [],
    [payload.productSkuList, payload.skus],
  );
  // 产品多图：兼容多种数据来源
  // - images：REST 链路 HDL 原始字段，字符串数组
  // - productImages：SSE 链路重命名字段，字符串数组
  // - imagesList：REST 链路 HDL 原始字段，可能是对象数组 [{productImageUlr, ...}] 或字符串数组
  // - productImage：单图回退
  const headerImgs = useMemo(() => {
    // 优先从 images（HDL 原始字符串数组）或 productImages（SSE 重命名）取
    const rawImgs = payload.images ?? payload.productImages;
    if (Array.isArray(rawImgs) && rawImgs.length > 0) {
      // 如果元素是字符串，直接使用；如果是对象，提取 productImageUlr 字段
      return rawImgs.map((item) =>
        typeof item === 'string' ? item : (item as Record<string, unknown>)?.productImageUlr as string ?? '',
      ).filter(Boolean);
    }
    // 回退到 imagesList（REST 链路 HDL 原始对象数组）
    if (Array.isArray(payload.imagesList) && payload.imagesList.length > 0) {
      return payload.imagesList.map((item) =>
        typeof item === 'string' ? item : (item as Record<string, unknown>)?.productImageUlr as string ?? '',
      ).filter(Boolean);
    }
    // 最终回退到单图
    return payload.productImage ? [payload.productImage] : [];
  }, [payload.images, payload.imagesList, payload.productImages, payload.productImage]);

  // 兼容 REST（productNameCn）和 SSE（productName）
  const productName = payload.productNameCn ?? (payload.productName as string | undefined);
  // 兼容 SSE（protocol）和 REST（protocolTypeName）
  const protocol = payload.protocol ?? (payload.protocolTypeName as string | undefined);
  const basePrice = pickPrice(payload);

  // 加购成功后切换到购物车列表视图
  const [cartViewData, setCartViewData] = useState<Record<string, unknown> | null>(null);
  const [loadingCart, setLoadingCart] = useState(false);

  /** 加购成功回调：调用 getCartList 获取购物车列表并切换视图 */
  const handleAddedToCart = async () => {
    setLoadingCart(true);
    try {
      const res = await getCartList();
      if (res.success && res.data) {
        setCartViewData(res.data as unknown as Record<string, unknown>);
      }
    } catch {
      // 忽略错误，仍停留在详情页
    } finally {
      setLoadingCart(false);
    }
  };

  // 加购成功后切换到购物车列表视图（顶部带返回产品详情按钮）
  if (cartViewData) {
    return (
      <div className="animate-fade-in space-y-3">
        {/* 成功提示条 */}
        <div className="flex items-center gap-2 text-xs text-neon-green bg-neon-green/5 border border-neon-green/30 rounded-lg px-3 py-2">
          <CheckCircle2 size={14} />
          <span>已加入购物车</span>
        </div>
        {/* 返回产品详情按钮 */}
        <button
          onClick={() => setCartViewData(null)}
          className="shimmer-btn w-full rounded-lg glass-panel border border-neon-purple/30 px-3 py-2 text-sm font-medium text-neon-cyan hover-neon transition-all flex items-center justify-center gap-1.5"
        >
          <ArrowLeft size={14} />
          返回产品详情
        </button>
        {/* 购物车列表 */}
        <CartListPage data={cartViewData} />
      </div>
    );
  }

  return (
    <div className="animate-fade-in space-y-4">
      {/* 标题栏 */}
      <div className="flex items-center gap-2 text-sm">
        <Package size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">产品详情</span>
        {loadingCart && (
          <span className="text-[11px] text-neon-purple flex items-center gap-1 ml-auto">
            <RefreshCw size={11} className="animate-spin" />
            加载购物车...
          </span>
        )}
      </div>

      {/* 顶部产品概要卡片 */}
      <div className="glass-strong rounded-xl p-4 card-shadow">
        <div className="flex items-start gap-3">
          <ImageCarousel
            images={headerImgs}
            alt={productName ?? '产品图片'}
            className="w-[120px] h-[120px] rounded-lg flex-shrink-0 border border-neon-purple/20"
          />
          <div className="flex-1 min-w-0">
            <h4 className="text-sm font-semibold text-slate-100 truncate">
              {productName ?? '未命名产品'}
            </h4>
            <p className="text-[11px] text-slate-400 font-mono mt-0.5">
              型号：{payload.productModel || '—'}
            </p>
            {payload.productId && (
              <p className="text-[10px] text-slate-500 font-mono mt-0.5 truncate">
                ID: {payload.productId}
              </p>
            )}
            <p className="text-[11px] text-slate-400 mt-1">
              {payload.supplierName ? `品牌：${payload.supplierName} ｜ ` : ''}
              {protocol ? `协议：${protocol} ｜ ` : ''}
              底价：
              <span className="neon-text-cyan font-semibold">
                {basePrice ? `¥${basePrice}` : '询价'}
              </span>
            </p>
          </div>
        </div>
      </div>

      {/* SKU 品号列表 */}
      <div className="glass rounded-xl p-4 card-shadow space-y-2">
        <div className="text-xs text-slate-300 font-mono flex items-center gap-1.5">
          <Info size={12} className="text-neon-cyan" />
          <span>品号规格</span>
          <span className="text-slate-500">（共 {skuList.length} 个）</span>
        </div>
        {skuList.length === 0 ? (
          <div className="text-center text-sm text-slate-500 py-4">暂无品号</div>
        ) : (
          <div className="space-y-2">
            {skuList.map((sku, i) => (
              <SkuItem
                key={sku.skuId ?? sku.erpNo ?? i}
                sku={sku}
                productId={payload.productId}
                unitName={payload.unitName}
                onAddedToCart={handleAddedToCart}
              />
            ))}
          </div>
        )}
      </div>

      {/* 返回按钮 */}
      {onBack && (
        <button
          onClick={onBack}
          className="shimmer-btn w-full rounded-lg glass-panel border border-neon-purple/30 px-3 py-2.5 text-sm font-medium text-neon-cyan hover-neon transition-all flex items-center justify-center gap-1.5"
        >
          <ArrowLeft size={14} />
          返回产品列表
        </button>
      )}
    </div>
  );
}

/** SKU 单项（含数量选择与加购按钮） */
function SkuItem({ sku, productId, unitName, onAddedToCart }: {
  sku: ProductSku;
  productId?: string;
  unitName?: string;
  /** 加购成功后的回调（父组件可据此切换到购物车列表视图） */
  onAddedToCart?: () => void;
}) {
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | undefined>();

  const adjust = (d: number) => setQty(q => Math.min(MAX_QUANTITY, Math.max(MIN_QUANTITY, q + d)));
  // SKU 多图：优先 images（HDL 原始字段），回退 skuImages，再回退 skuImage
  // - images：HDL getMallProductInfo 返回的 SKU 图片 URL 字符串数组
  // - skuImages：兼容字段（前端契约字段名）
  // - skuImage：单图回退
  const imgs = useMemo(() => {
    const rawImgs = sku.images ?? sku.skuImages;
    if (Array.isArray(rawImgs) && rawImgs.length > 0) {
      return rawImgs.map((item) =>
        typeof item === 'string' ? item : (item as Record<string, unknown>)?.productImageUlr as string ?? '',
      ).filter(Boolean);
    }
    return sku.skuImage ? [sku.skuImage] : [];
  }, [sku.images, sku.skuImages, sku.skuImage]);
  const priceStr = pickPrice(sku);
  // 配件列表：优先 accessoriesProductList（HDL 原始字段），回退 accessoriesList
  const accessories = sku.accessoriesProductList ?? sku.accessoriesList ?? [];

  /** 加购：调用 addToCart REST API，成功后通知父组件切换到购物车列表 */
  const handleAddToCart = async () => {
    if (adding || !sku.skuId || !productId) return;
    setAdding(true);
    setError(undefined);
    try {
      const res = await addToCart({
        skuId: sku.skuId,
        productId,
        quantity: qty,
        erpNo: sku.erpNo,
      });
      if (res.success) {
        setAdded(true);
        // 加购成功后立即通知父组件切换到购物车列表视图
        if (onAddedToCart) {
          onAddedToCart();
        } else {
          // 无父组件回调时，2 秒后重置按钮状态
          setTimeout(() => setAdded(false), 2000);
        }
      } else {
        setError(res.message || '加购失败');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(`加购失败：${msg}`);
    } finally {
      setAdding(false);
    }
  };

  return (
    <div className="rounded-lg glass-panel border border-neon-purple/15 p-2.5 hover-neon transition-all">
      <div className="flex items-start gap-2">
        <ImageCarousel
          images={imgs}
          alt={sku.erpNo ?? 'sku'}
          className="w-[80px] h-[80px] rounded-lg flex-shrink-0 border border-neon-purple/15"
        />
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-xs font-mono text-slate-200">品号：{sku.erpNo || '—'}</span>
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono border ${
                  sku.shelvesStatus === SHELVES_STATUS_ON
                    ? 'text-neon-green border-neon-green/30 bg-neon-green/5'
                    : 'text-slate-400 border-slate-500/30 bg-slate-500/5'
                }`}>
                  {sku.shelvesStatusText || (sku.shelvesStatus === SHELVES_STATUS_ON ? '上架' : '下架')}
                </span>
              </div>
              <p className="text-[11px] text-slate-400 mt-0.5 line-clamp-2">
                规格：{sku.specsDesc || '默认'}
                {sku.stock != null && ` ｜ 库存：${sku.stock}`}
                {unitName && ` ｜ 单位：${unitName}`}
              </p>
            </div>
            <span className="text-sm neon-text-cyan font-semibold flex-shrink-0">
              {priceStr ? `¥${priceStr}` : '询价'}
            </span>
          </div>

          {/* 数量选择 + 加购按钮 */}
          <div className="mt-2 flex items-center gap-2">
            <div className="flex items-center gap-1">
              <button
                onClick={() => adjust(-1)}
                disabled={qty <= MIN_QUANTITY}
                className="w-6 h-6 flex items-center justify-center rounded glass-panel border border-neon-purple/20 text-slate-300 hover-neon disabled:opacity-40"
              >
                <Minus size={11} />
              </button>
              <span className="w-8 text-center text-xs font-medium text-slate-100 font-mono tabular-nums">{qty}</span>
              <button
                onClick={() => adjust(1)}
                disabled={qty >= MAX_QUANTITY}
                className="w-6 h-6 flex items-center justify-center rounded glass-panel border border-neon-purple/20 text-slate-300 hover-neon disabled:opacity-40"
              >
                <Plus size={11} />
              </button>
            </div>
            <button
              onClick={handleAddToCart}
              disabled={adding || !sku.skuId || !productId}
              className="flex-1 rounded-lg bg-gradient-to-r from-neon-purple to-neon-violet px-3 py-1.5 text-xs font-medium text-white shadow-neon-purple transition-all flex items-center justify-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed hover:shadow-glow-md"
            >
              {adding ? <RefreshCw size={12} className="animate-spin" /> : added ? <CheckCircle2 size={12} /> : <ShoppingCart size={12} />}
              {adding ? '加购中...' : added ? '已加购' : '加购'}
            </button>
          </div>

          {/* 加购结果反馈（淡入淡出） */}
          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="mt-1.5 text-[11px] text-red-400"
              >
                {error}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* 配件列表：展示 SKU 关联的配件产品信息 */}
      {accessories.length > 0 && (
        <div className="mt-2 pt-2 border-t border-neon-purple/10">
          <div className="flex items-center gap-1.5 text-[11px] text-slate-300 font-mono mb-1.5">
            <Package size={11} className="text-neon-cyan" />
            <span>配件（共 {accessories.length} 个）</span>
          </div>
          <div className="space-y-1">
            {accessories.map((acc, idx) => {
              // 配件信息提取：兼容多种字段名
              const accName = (acc.productNameCn ?? acc.productName ?? acc.name) as string | undefined;
              const accModel = (acc.productModel ?? acc.model) as string | undefined;
              const accErpNo = (acc.erpNo ?? acc.skuErpNo) as string | undefined;
              const accImg = (acc.productImageUlr ?? acc.productImage ?? acc.skuImage) as string | undefined;
              return (
                <div key={idx} className="flex items-center gap-2 rounded glass-panel border border-neon-purple/10 px-2 py-1">
                  {accImg && (
                    <img
                      src={accImg}
                      alt={accName ?? '配件'}
                      className="w-8 h-8 rounded object-cover flex-shrink-0 border border-neon-purple/10"
                    />
                  )}
                  <div className="flex-1 min-w-0">
                    <span className="text-[11px] text-slate-200 truncate block">
                      {accName ?? '未命名配件'}
                    </span>
                    <span className="text-[10px] text-slate-500 font-mono">
                      {accModel ? `型号：${accModel}` : ''}
                      {accErpNo ? ` ｜ 品号：${accErpNo}` : ''}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
