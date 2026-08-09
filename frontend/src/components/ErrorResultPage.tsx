import { AlertTriangle, RefreshCw } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import { sanitizeErrorMessage } from '../shared/utils';

interface ErrorResultData {
  errorMessage?: string;
  message?: string;
}

/**
 * 错误结果提示页
 *
 * 接收 data={errorMessage}，展示错误提示（已脱敏）。
 */
export default function ErrorResultPage({ data }: RoutePageProps) {
  const payload = data as unknown as ErrorResultData;
  const rawMessage = payload.errorMessage ?? payload.message ?? '处理失败';
  const errorMessage = sanitizeErrorMessage(rawMessage);

  return (
    <div className="animate-fade-in flex flex-col items-center justify-center py-10 text-center">
      <div className="relative mb-4">
        <div className="rounded-2xl bg-red-500/10 p-5 text-red-400 border border-red-500/30 shadow-[0_0_16px_rgba(239,68,68,0.25)]">
          <AlertTriangle size={40} />
        </div>
      </div>
      <p className="text-sm text-red-300 max-w-md">{errorMessage}</p>
      <button
        onClick={() => window.location.reload()}
        className="mt-4 shimmer-btn rounded-lg glass-panel px-4 py-2 text-xs text-neon-cyan hover-neon flex items-center gap-1.5 transition-all"
      >
        <RefreshCw size={12} />
        重试
      </button>
    </div>
  );
}
