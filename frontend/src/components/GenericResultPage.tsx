import { CheckCircle2 } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';

interface GenericResultData {
  result?: unknown;
  [k: string]: unknown;
}

/**
 * 通用结果展示页
 *
 * 接收 data={result}，result 为字符串时直接展示；
 * result 为 null/对象/数组时展示友好提示，避免向用户暴露后端内部数据结构。
 */
export default function GenericResultPage({ data }: RoutePageProps) {
  const payload = data as unknown as GenericResultData;
  const result = payload.result;

  return (
    <div className="animate-fade-in space-y-3">
      <div className="flex items-center gap-2 text-sm">
        <CheckCircle2 size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">处理结果</span>
      </div>
      <div className="glass-strong rounded-xl p-4 card-shadow">
        {result == null ? (
          <p className="text-sm text-slate-300">操作已完成</p>
        ) : typeof result === 'string' ? (
          <p className="text-sm text-slate-100 whitespace-pre-wrap leading-relaxed">{result}</p>
        ) : (
          <p className="text-sm text-slate-300">操作已完成</p>
        )}
      </div>
    </div>
  );
}
