/**
 * 事件时间线组件
 *
 * 实时展示 Agent 执行过程中的事件流（来自 SSE 订阅）。
 * 每条事件显示：时间戳 / 类型标签 / Agent ID / 事件名 / 耗时。
 *
 * 使用纯 CSS + 滚动列表实现（非 ECharts），
 * 因为事件流的展示场景更适合文本列表而非图表。
 */

import { type EventType, type ObserveEvent } from '../../api/observe';

/** 事件类型到颜色和图标的映射（避免动态拼接 class） */
const EVENT_TYPE_STYLE: Record<EventType, { color: string; label: string }> = {
  AGENT_START: { color: 'text-neon-cyan border-neon-cyan/40', label: '启动' },
  AGENT_END: { color: 'text-neon-green border-neon-green/40', label: '结束' },
  LLM_CALL_START: { color: 'text-amber-400 border-amber-400/40', label: 'LLM→' },
  LLM_CALL_END: { color: 'text-amber-400 border-amber-400/40', label: 'LLM✓' },
  TOOL_CALL_START: { color: 'text-neon-purple border-neon-purple/40', label: '工具→' },
  TOOL_CALL_END: { color: 'text-neon-purple border-neon-purple/40', label: '工具✓' },
  MEMORY_LOAD: { color: 'text-sky-400 border-sky-400/40', label: '记忆↓' },
  MEMORY_SAVE: { color: 'text-sky-400 border-sky-400/40', label: '记忆↑' },
  RAG_SEARCH: { color: 'text-pink-400 border-pink-400/40', label: 'RAG' },
  ERROR: { color: 'text-red-400 border-red-400/40', label: '错误' },
  CUSTOM: { color: 'text-slate-400 border-slate-400/40', label: '自定义' },
  UNKNOWN: { color: 'text-slate-500 border-slate-500/40', label: '未知' },
};

/** EventTimeline 属性 */
export interface EventTimelineProps {
  /** 事件列表（最新在前） */
  events: ObserveEvent[];
  /** 是否已连接 SSE */
  connected: boolean;
}

/** 事件时间线组件 */
export default function EventTimeline({ events, connected }: EventTimelineProps) {
  return (
    <div className="glass-panel rounded-lg p-3 border border-neon-purple/20 flex flex-col h-full min-h-0">
      {/* 头部：标题 + 连接状态 */}
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs text-slate-400 uppercase tracking-wider">事件流（实时）</span>
        <div className="flex items-center gap-1.5">
          <span
            className={`w-2 h-2 rounded-full ${connected ? 'bg-neon-green animate-pulse' : 'bg-slate-600'}`}
          />
          <span className="text-xs text-slate-500">{connected ? '已连接' : '断开'}</span>
        </div>
      </div>

      {/* 事件列表（可滚动） */}
      <div className="flex-1 overflow-y-auto custom-scroll pr-1 min-h-0">
        {events.length === 0 ? (
          <div className="text-center text-slate-500 text-sm py-8">
            暂无事件，发起对话后即可看到实时事件流
          </div>
        ) : (
          <div className="space-y-1">
            {events.map((event, idx) => {
              const style = EVENT_TYPE_STYLE[event.type] ?? EVENT_TYPE_STYLE.UNKNOWN;
              const time = event.timestamp
                ? new Date(event.timestamp).toLocaleTimeString('zh-CN', { hour12: false })
                : '--:--:--';
              return (
                <div
                  key={`${event.timestamp ?? idx}-${idx}`}
                  className="flex items-start gap-2 text-xs py-1 px-1.5 rounded hover:bg-white/5 transition-colors"
                >
                  {/* 时间戳 */}
                  <span className="text-slate-500 tabular-nums shrink-0">{time}</span>
                  {/* 事件类型标签 */}
                  <span
                    className={`shrink-0 px-1.5 py-0.5 rounded border ${style.color} text-[10px] font-mono`}
                  >
                    {style.label}
                  </span>
                  {/* Agent ID + 事件名 */}
                  <div className="flex-1 min-w-0">
                    {event.agentId && (
                      <span className="text-neon-cyan font-mono mr-1">
                        [{event.agentId}]
                      </span>
                    )}
                    <span className="text-slate-300">{event.name || event.type}</span>
                    {event.elapsedMs != null && event.elapsedMs > 0 && (
                      <span className="text-amber-400 ml-1.5 tabular-nums">
                        {event.elapsedMs}ms
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
