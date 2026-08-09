import { useState, useEffect } from 'react';
import { CheckCircle2, ShoppingCart, Loader2 } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import { getCartList, type CartListData } from '../api/client';
import CartListPage from './CartListPage';

interface CartAddResultData {
  cartItemId?: string;
  skuId?: string;
  quantity?: number;
  success?: boolean;
  productName?: string;
}

/**
 * 加购成功结果页
 *
 * 接收 data={cartItemId, skuId, quantity, success}，展示加购成功提示。
 * 加购成功后自动调用 getCartList() 获取购物车列表，渲染 CartListPage。
 */
export default function CartAddResultPage({ data }: RoutePageProps) {
  const payload = data as unknown as CartAddResultData;
  const success = payload.success !== false;
  const quantity = payload.quantity ?? 1;

  // 加购成功后自动加载购物车列表
  const [cartListData, setCartListData] = useState<CartListData | null>(null);
  const [loadingCart, setLoadingCart] = useState(false);
  const [loadError, setLoadError] = useState<string | undefined>();

  useEffect(() => {
    if (!success) return;
    let cancelled = false;
    const controller = new AbortController();
    setLoadingCart(true);
    getCartList(controller.signal)
      .then(res => {
        if (cancelled) return;
        if (res.success && res.data) {
          setCartListData(res.data);
        } else {
          setLoadError(res.message || '获取购物车列表失败');
        }
      })
      .catch(e => {
        if (!cancelled) {
          setLoadError(e instanceof Error ? e.message : String(e));
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingCart(false);
      });
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [success]);

  // 加购失败：展示失败提示
  if (!success) {
    return (
      <div className="animate-fade-in flex flex-col items-center justify-center py-10 text-center">
        <div className="rounded-2xl p-5 border bg-red-500/10 text-red-400 border-red-500/30">
          <CheckCircle2 size={44} />
        </div>
        <h3 className="text-lg font-semibold gradient-text mb-2 mt-5">加购失败</h3>
      </div>
    );
  }

  // 加购成功且购物车列表加载中：展示加载状态
  if (loadingCart) {
    return (
      <div className="animate-fade-in flex flex-col items-center justify-center py-10 text-center">
        <div className="rounded-2xl p-5 border bg-neon-green/10 text-neon-green border-neon-green/30 shadow-[0_0_16px_rgba(57,255,20,0.25)]">
          <CheckCircle2 size={44} />
        </div>
        <h3 className="text-lg font-semibold gradient-text mb-2 mt-5">已加入购物车</h3>
        <p className="text-sm text-slate-300">
          {payload.productName ? `${payload.productName} · ` : ''}数量 ×{quantity}
        </p>
        <div className="mt-4 flex items-center gap-2 text-xs text-slate-400">
          <Loader2 size={14} className="animate-spin text-neon-purple" />
          <span>正在加载购物车列表...</span>
        </div>
      </div>
    );
  }

  // 加购成功且购物车列表加载失败：展示成功提示 + 错误信息
  if (loadError && !cartListData) {
    return (
      <div className="animate-fade-in flex flex-col items-center justify-center py-10 text-center">
        <div className="rounded-2xl p-5 border bg-neon-green/10 text-neon-green border-neon-green/30 shadow-[0_0_16px_rgba(57,255,20,0.25)]">
          <CheckCircle2 size={44} />
        </div>
        <h3 className="text-lg font-semibold gradient-text mb-2 mt-5">已加入购物车</h3>
        <p className="text-sm text-slate-300">
          {payload.productName ? `${payload.productName} · ` : ''}数量 ×{quantity}
        </p>
        <p className="mt-3 text-xs text-amber-400">{loadError}</p>
        <div className="mt-4 flex items-center gap-1.5 text-[11px] text-slate-500 font-mono">
          <ShoppingCart size={11} className="text-neon-purple" />
          <span>可继续选购其他商品</span>
        </div>
      </div>
    );
  }

  // 加购成功且购物车列表已加载：渲染购物车列表
  return (
    <div className="animate-fade-in space-y-3">
      {/* 成功提示条 */}
      <div className="flex items-center gap-2 text-xs text-neon-green bg-neon-green/5 border border-neon-green/30 rounded-lg px-3 py-2">
        <CheckCircle2 size={14} />
        <span>
          已加入购物车
          {payload.productName ? `（${payload.productName} ×${quantity}）` : ''}
        </span>
      </div>
      {/* 购物车列表 */}
      <CartListPage data={(cartListData ?? {}) as unknown as Record<string, unknown>} />
    </div>
  );
}
