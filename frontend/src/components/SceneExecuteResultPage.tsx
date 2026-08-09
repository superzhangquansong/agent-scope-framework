import { CheckCircle2, Play } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';

interface SceneExecuteResultData {
  sceneId?: string;
  sceneName?: string;
  success?: boolean;
}

/**
 * 场景执行结果页
 *
 * 接收 data={sceneId, sceneName, success}，展示场景执行成功提示。
 */
export default function SceneExecuteResultPage({ data }: RoutePageProps) {
  const payload = data as unknown as SceneExecuteResultData;
  const success = payload.success !== false;
  const sceneName = payload.sceneName ?? '未命名场景';

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
        {success ? `已执行场景「${sceneName}」` : '场景执行失败'}
      </h3>

      {success ? (
        <p className="text-sm text-slate-300">
          场景下所有关联设备的动作已触发
        </p>
      ) : (
        <p className="text-sm text-slate-400">请检查设备状态后重试</p>
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
        <Play size={11} className="text-neon-purple" />
        <span>执行指令已下发</span>
      </div>
    </div>
  );
}
