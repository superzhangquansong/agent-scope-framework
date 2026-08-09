import { Layers, Play, Cpu, ChevronRight } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';

interface SceneDevice {
  deviceId?: string;
  deviceName?: string;
  actions?: string | string[] | Record<string, unknown>;
  [k: string]: unknown;
}

interface SceneDetailData {
  sceneId?: string;
  sceneName?: string;
  sceneType?: string;
  devices?: SceneDevice[];
}

/**
 * 场景详情页
 *
 * 接收 data={sceneId, sceneName, sceneType, devices}，渲染场景信息 + 设备动作列表。
 */
export default function SceneDetailPage({ data }: RoutePageProps) {
  const payload = data as unknown as SceneDetailData;
  const devices = payload.devices ?? [];

  return (
    <div className="animate-fade-in space-y-4">
      {/* 标题栏 */}
      <div className="flex items-center gap-2 text-sm">
        <Layers size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">场景详情</span>
      </div>

      {/* 场景概要卡片 */}
      <div className="glass-strong rounded-xl p-4 card-shadow">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-neon-purple/10 p-3 text-neon-purple flex-shrink-0">
            <Play size={22} />
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-base font-semibold text-slate-100 truncate">
              {payload.sceneName ?? '未命名场景'}
            </div>
            <div className="flex flex-wrap items-center gap-2 mt-1 text-[11px] font-mono text-slate-400">
              {payload.sceneId && <span>ID: {payload.sceneId}</span>}
              {payload.sceneType && (
                <span className="rounded-full glass-panel px-2 py-0.5 text-neon-cyan border border-neon-cyan/30">
                  {String(payload.sceneType)}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 设备动作列表 */}
      <div className="glass rounded-xl p-4 card-shadow">
        <div className="flex items-center gap-2 mb-3 text-xs text-slate-300 font-mono">
          <Cpu size={12} className="text-neon-cyan" />
          <span>关联设备</span>
          <span className="text-slate-500">({devices.length})</span>
        </div>
        {devices.length === 0 ? (
          <div className="text-center text-sm text-slate-500 py-4">暂无关联设备</div>
        ) : (
          <div className="space-y-2">
            {devices.map((d, i) => (
              <div
                key={d.deviceId ?? i}
                className="glass-panel rounded-lg px-3 py-2.5 hover-neon transition-all animate-card-in"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                <div className="flex items-center gap-2">
                  <Cpu size={12} className="text-neon-purple flex-shrink-0" />
                  <span className="text-sm text-slate-100 font-medium truncate flex-1">
                    {d.deviceName ?? '未命名设备'}
                  </span>
                  {d.deviceId && (
                    <span className="text-[10px] text-slate-500 font-mono truncate">
                      {d.deviceId}
                    </span>
                  )}
                </div>
                {d.actions != null && (
                  <div className="mt-1.5 flex items-start gap-1.5 text-[11px] text-slate-400">
                    <ChevronRight size={11} className="text-neon-purple mt-0.5 flex-shrink-0" />
                    <span className="leading-relaxed">{formatActions(d.actions)}</span>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/** 格式化 actions 字段，兼容字符串/数组/对象 */
function formatActions(actions: unknown): string {
  if (actions == null) return '-';
  if (typeof actions === 'string') return actions;
  if (Array.isArray(actions)) return actions.map(a => String(a)).join('，');
  if (typeof actions === 'object') {
    try {
      return Object.entries(actions as Record<string, unknown>)
        .map(([k, v]) => `${k}: ${String(v)}`)
        .join('，');
    } catch {
      return JSON.stringify(actions);
    }
  }
  return String(actions);
}
