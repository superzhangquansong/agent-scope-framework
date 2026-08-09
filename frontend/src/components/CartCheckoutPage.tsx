import { CheckCircle2, ShoppingCart } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';

interface CartCheckoutData {
  orderId?: string;
  totalAmount?: string | number;
  success?: boolean;
}

/**
 * 下单结果页
 *
 * 接收 data={orderId, totalAmount, success}，展示订单号、总金额与下单状态。
 */
export default function CartCheckoutPage({ data }: RoutePageProps) {
  const payload = data as unknown as CartCheckoutData;
  const success = payload.success !== false;

  return (
    <div className="animate-fade-in flex flex-col items-center justify-center py-10 text-center">
      {/* 状态图标 */}
      <div className="relative mb-5">
        <div className={`rounded-2xl p-5 border ${
          success
            ? 'bg-neon-green/10 text-neon-green border-neon-green/30 shadow-[0_0_16px_rgba(57,255,20,0.25)]'
            : 'bg-red-500/10 text-red-400 border-red-500/30'
        }`}>
          <CheckCircle2 size={44} />
        </div>
      </div>

      <h3 className="text-lg font-semibold gradient-text mb-1">
        {success ? '下单成功' : '下单失败'}
      </h3>
      <p className="text-[11px] text-slate-500 font-mono mb-5">
        {success ? '订单已提交，请等待发货' : '请稍后重试'}
      </p>

      {/* 订单信息卡片 */}
      <div className="glass-strong rounded-xl p-4 card-shadow w-full max-w-sm space-y-3">
        {payload.orderId && (
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs text-slate-400 font-mono">订单号</span>
            <span className="text-sm neon-text-cyan font-mono truncate">{payload.orderId}</span>
          </div>
        )}
        {payload.totalAmount != null && (
          <div className="flex items-center justify-between gap-3 pt-2 border-t border-neon-purple/15">
            <span className="text-xs text-slate-400 font-mono">总金额</span>
            <span className="text-lg font-bold neon-text-purple">¥{String(payload.totalAmount)}</span>
          </div>
        )}
      </div>

      <div className="mt-6 flex items-center gap-1.5 text-[11px] text-slate-500 font-mono">
        <ShoppingCart size={11} className="text-neon-purple" />
        <span>感谢您的购买</span>
      </div>
    </div>
  );
}
