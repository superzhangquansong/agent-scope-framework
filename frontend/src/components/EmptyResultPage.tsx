import { Inbox } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';

interface EmptyResultData {
  message?: string;
}

/**
 * 空结果提示页
 *
 * 接收 data={message}，展示空结果提示。
 */
export default function EmptyResultPage({ data }: RoutePageProps) {
  const payload = data as unknown as EmptyResultData;
  const message = payload.message ?? '暂无结果';

  return (
    <div className="animate-fade-in flex flex-col items-center justify-center py-10 text-center">
      <div className="relative mb-4">
        <div className="rounded-2xl bg-neon-purple/10 p-5 text-neon-purple border border-neon-purple/20">
          <Inbox size={40} />
        </div>
      </div>
      <p className="text-sm text-slate-300">{message}</p>
      <p className="text-[11px] text-slate-500 font-mono mt-2">没有匹配的数据可展示</p>
    </div>
  );
}
