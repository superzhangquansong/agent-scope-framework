import { useState } from 'react';
import { Sparkles, CheckCircle2, Zap, Star, Loader2 } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import type { SceneRecommendItem } from '../api/client';

/**
 * 场景推荐结果页面（通过 DynamicPage 路由 scene-recommend 渲染）。
 *
 * <p>后端 SceneRecommendTool#recommendScene 调用成功后，SSE result 事件携带
 * routePath=/scene/recommend 和 {recommends: SceneRecommendVO[]} 数据，前端据此渲染本组件。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>展示推荐方案卡片：场景名 + 效果描述 + 每个设备的具体控制动作 + 匹配设备数 + 推荐分数</li>
 *   <li>「创建此场景」按钮：点击后通过 window 自定义事件通知 App 发送“创建{场景名}”，
 *       由 LLM 调用 create_scene_from_template 完成创建（推荐阶段不直接落库）</li>
 * </ul>
 *
 * @author zqs
 * @since 2.0.0
 */

/** 场景推荐创建指令事件名（App.tsx 监听后调用 handleSend） */
export const SCENE_RECOMMEND_CREATE_EVENT = 'hdl-scene-recommend-create';

/** 模板编码 → 展示图标名映射（用于卡片头部图标配色，未命中时使用 Sparkles） */
const TEMPLATE_ICON: Record<string, string> = {
  movie_mode: '🎬',
  sleep_mode: '🌙',
  guest_mode: '👥',
  reading_mode: '📖',
  wake_up_mode: '🌅',
  leave_home: '🚪',
};

/**
 * 从 DynamicPage 传入的 data 中安全提取推荐方案列表。
 *
 * <p>兼容多种数据来源结构：</p>
 * <ul>
 *   <li>{recommends: [...]}（ToolResultEndHandler 直传 ToolResultVO.data）</li>
 *   <li>{data: {recommends: [...]}}（AgentScopeResult 包装）</li>
 *   <li>数组本身（REST 直连 /api/scene/recommend 返回）</li>
 * </ul>
 *
 * @param data DynamicPage 传入的结构化数据
 * @return 推荐方案列表（无法解析时返回空数组）
 */
function extractRecommends(data: Record<string, unknown>): SceneRecommendItem[] {
  const raw = data as Record<string, unknown>;
  if (Array.isArray(raw.recommends)) {
    return raw.recommends as SceneRecommendItem[];
  }
  const nested = raw.data as Record<string, unknown> | undefined;
  if (nested && Array.isArray(nested.recommends)) {
    return nested.recommends as SceneRecommendItem[];
  }
  if (Array.isArray(raw)) {
    return raw as unknown as SceneRecommendItem[];
  }
  return [];
}

export default function SceneRecommendPage({ data }: RoutePageProps) {
  const recommends = extractRecommends(data);
  /** 正在发送创建指令的场景名（用于按钮 loading 态） */
  const [creatingName, setCreatingName] = useState<string | null>(null);

  /** 点击「创建此场景」：通过自定义事件通知 App 发送“创建{场景名}” */
  const handleCreate = (sceneName: string) => {
    if (!sceneName || creatingName) return;
    setCreatingName(sceneName);
    window.dispatchEvent(new CustomEvent<{ sceneName: string }>(SCENE_RECOMMEND_CREATE_EVENT, {
      detail: { sceneName },
    }));
    // 短暂 loading 后复位（创建实际由 LLM 异步完成，这里仅提示已发送）
    setTimeout(() => setCreatingName(null), 1200);
  };

  // 空推荐：无匹配模板
  if (recommends.length === 0) {
    return (
      <div className="animate-fade-in space-y-3">
        <div className="flex items-center gap-2 text-sm">
          <Sparkles size={16} className="neon-text-purple" />
          <span className="gradient-text font-semibold">场景推荐</span>
        </div>
        <div className="glass rounded-xl px-4 py-8 text-center text-sm text-slate-400">
          当前设备组合暂无匹配的场景模板
        </div>
      </div>
    );
  }

  return (
    <div className="animate-fade-in space-y-3">
      {/* 标题栏 */}
      <div className="flex items-center gap-2 text-sm">
        <Sparkles size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">场景推荐</span>
        <span className="text-xs text-slate-400 font-mono">共 {recommends.length} 个方案</span>
      </div>

      {/* 推荐方案卡片列表 */}
      <div className="space-y-2.5">
        {recommends.map((rec, i) => {
          const sceneName = rec.sceneName ?? '未命名场景';
          const desc = rec.description ?? '';
          const deviceActions = Array.isArray(rec.deviceActions) ? rec.deviceActions : [];
          const matchedCount = Array.isArray(rec.matchedDevices) ? rec.matchedDevices.length : deviceActions.length;
          const score = rec.score != null ? Math.round(rec.score) : null;
          const icon = TEMPLATE_ICON[rec.templateCode ?? ''] ?? '✨';
          const isCreating = creatingName === sceneName;

          return (
            <div
              key={rec.templateCode || i}
              className="glass rounded-xl p-3 hover-neon card-shadow border border-neon-purple/15 animate-card-in transition-all"
              style={{ animationDelay: `${i * 70}ms` }}
            >
              {/* 头部：图标 + 场景名 + 分数 */}
              <div className="flex items-center gap-2 mb-1.5">
                <div className="rounded-lg bg-neon-purple/10 p-1.5 text-base flex-shrink-0 border border-neon-purple/20 leading-none">
                  {icon}
                </div>
                <h5 className="text-sm font-semibold text-slate-100 truncate flex-1">
                  {sceneName}
                </h5>
                {score != null && (
                  <span className="flex items-center gap-0.5 rounded-full glass-panel px-2 py-0.5 text-[10px] text-neon-amber border border-neon-amber/30 font-mono flex-shrink-0">
                    <Star size={10} className="fill-neon-amber text-neon-amber" />
                    {score}
                  </span>
                )}
              </div>

              {/* 效果描述 */}
              {desc && (
                <p className="text-xs text-slate-300 leading-relaxed mb-2">{desc}</p>
              )}

              {/* 设备动作列表 */}
              {deviceActions.length > 0 && (
                <div className="mb-2.5 space-y-1">
                  {deviceActions.map((action, idx) => (
                    <div
                      key={`${action.deviceName}-${idx}`}
                      className="flex items-start gap-1.5 rounded-md bg-slate-800/40 px-2 py-1.5 border border-slate-700/30"
                    >
                      <Zap size={11} className="text-neon-cyan mt-0.5 flex-shrink-0" />
                      <div className="min-w-0">
                        <span className="text-[11px] text-slate-200 font-medium">{action.deviceName || '设备'}</span>
                        {action.actionSummary && (
                          <span className="text-[11px] text-slate-400">：{action.actionSummary}</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* 底部：匹配设备数 + 创建按钮 */}
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-slate-500 font-mono flex-shrink-0">
                  匹配 {matchedCount} 个设备
                </span>
                <button
                  onClick={() => handleCreate(sceneName)}
                  disabled={isCreating}
                  className="flex-1 rounded-lg bg-gradient-to-r from-neon-purple to-neon-violet px-2 py-1.5 text-xs font-medium text-white shadow-neon-purple transition-all flex items-center justify-center gap-1 disabled:opacity-50 disabled:cursor-not-allowed hover:shadow-glow-md"
                >
                  {isCreating ? <Loader2 size={12} className="animate-spin" /> : <CheckCircle2 size={12} />}
                  {isCreating ? '已发送创建指令' : '创建此场景'}
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {/* 底部提示 */}
      <p className="text-xs text-slate-500 text-center">
        点击「创建此场景」后，AI 将为你创建对应场景
      </p>
    </div>
  );
}
