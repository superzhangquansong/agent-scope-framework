import { CheckCircle2, Layers } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';

interface SceneDeleteResultData {
  sceneId?: string;
  success?: boolean;
}

/**
 * 场景删除结果页
 *
 * 接收 data={sceneId, success}，展示场景删除成功提示。
 */
export default function SceneDeleteResultPage({ data }: RoutePageProps) {
  const payload = data as unknown as SceneDeleteResultData;
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

      <h3 className="text-lg font-semibold gradient-text mb-2">
        {success ? '已删除场景' : '删除场景失败'}
      </h3>

      {success ? (
        <p className="text-sm text-slate-300">场景已从列表中移除</p>
      ) : (
        <p className="text-sm text-slate-400">请确认场景是否存在后重试</p>
      )}

      {/* 场景 ID 元数据 */}
      {payload.sceneId && (
        <div className="mt-4">
          <span className="rounded-full glass-panel px-2.5 py-0.5 text-[11px] font-mono text-neon-cyan border border-neon-cyan/30">
            sceneId: {payload.sceneId}
          </span>
        </div>
      )}

      <div className="mt-6 flex items-center gap-1.5 text-[11px] text-slate-500 font-mono">
        <Layers size={11} className="text-neon-purple" />
        <span>操作已完成</span>
      </div>
    </div>
  );
}
