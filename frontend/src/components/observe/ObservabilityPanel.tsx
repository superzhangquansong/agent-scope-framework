/**
 * 可观测性浮动面板组件
 *
 * 独立浮动面板，与 StickyNotesContainer 同级，可拖拽缩放。
 * 整合 4 大可视化模块：
 *  1. MetricCard 网格：LLM 调用次数 / Agent 调用次数 / 工具调用次数 / 错误次数
 *  2. ThroughputChart：事件吞吐量折线图（最近 60 秒）
 *  3. AgentTopology：Agent 拓扑图（力导向图）
 *  4. EventTimeline：实时事件流时间线（SSE 推送）
 *
 * 数据来源：
 *  - /api/observe/metrics（定时轮询，5 秒一次）
 *  - /api/observe/agents（定时轮询，10 秒一次）
 *  - /api/observe/events（SSE 长连接，实时推送）
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Activity, Cpu, Wrench, AlertTriangle, RefreshCw, X, Pause, Play,
} from 'lucide-react';
import { useObserveSSE } from '../../hooks/useObserveSSE';
import {
  getMetrics, getAgents, resetMetrics,
  type ObserveMetrics, type AgentNode,
} from '../../api/observe';
import MetricCard from './MetricCard';
import ThroughputChart from './ThroughputChart';
import AgentTopology from './AgentTopology';
import EventTimeline from './EventTimeline';

/** 面板最小宽度 */
const MIN_WIDTH = 720;
/** 面板最小高度 */
const MIN_HEIGHT = 480;
/** 指标轮询间隔（毫秒） */
const METRICS_POLL_INTERVAL = 5000;
/** Agent 列表轮询间隔（毫秒） */
const AGENTS_POLL_INTERVAL = 10000;

/** ObservabilityPanel 属性 */
export interface ObservabilityPanelProps {
  /** 关闭回调 */
  onClose: () => void;
}

/** 可观测性浮动面板 */
export default function ObservabilityPanel({ onClose }: ObservabilityPanelProps) {
  // ===== 状态管理 =====
  const [metrics, setMetrics] = useState<ObserveMetrics | null>(null);
  const [agents, setAgents] = useState<AgentNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [paused, setPaused] = useState(false);

  // ===== SSE 实时事件流订阅 =====
  const { events, connected, clearEvents } = useObserveSSE(!paused);

  // ===== 轮询定时器引用 =====
  const metricsTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const agentsTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ===== 拖拽和缩放状态 =====
  const [position, setPosition] = useState({ x: 60, y: 60 });
  const [size, setSize] = useState({ width: 880, height: 580 });
  const dragRef = useRef<{ startX: number; startY: number; originX: number; originY: number } | null>(null);
  const resizeRef = useRef<{ startX: number; startY: number; originW: number; originH: number } | null>(null);

  // ===== 拉取指标数据 =====
  const fetchMetrics = useCallback(async () => {
    try {
      const data = await getMetrics();
      setMetrics(data);
    } catch (err) {
      console.error('[ObservabilityPanel] 拉取指标失败:', err);
    }
  }, []);

  // ===== 拉取 Agent 列表 =====
  const fetchAgents = useCallback(async () => {
    try {
      const data = await getAgents();
      setAgents(data);
    } catch (err) {
      console.error('[ObservabilityPanel] 拉取 Agent 列表失败:', err);
    }
  }, []);

  // ===== 初始化 + 定时轮询 =====
  useEffect(() => {
    setLoading(true);
    Promise.all([fetchMetrics(), fetchAgents()]).finally(() => setLoading(false));

    // 指标 5 秒轮询
    metricsTimerRef.current = setInterval(() => {
      if (!paused) fetchMetrics();
    }, METRICS_POLL_INTERVAL);
    // Agent 列表 10 秒轮询
    agentsTimerRef.current = setInterval(() => {
      if (!paused) fetchAgents();
    }, AGENTS_POLL_INTERVAL);

    return () => {
      if (metricsTimerRef.current) clearInterval(metricsTimerRef.current);
      if (agentsTimerRef.current) clearInterval(agentsTimerRef.current);
    };
  }, [fetchMetrics, fetchAgents, paused]);

  // ===== 拖拽逻辑 =====
  const handleDragStart = useCallback((e: React.MouseEvent) => {
    dragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      originX: position.x,
      originY: position.y,
    };
    const onMove = (ev: MouseEvent) => {
      if (!dragRef.current) return;
      const dx = ev.clientX - dragRef.current.startX;
      const dy = ev.clientY - dragRef.current.startY;
      setPosition({
        x: Math.max(0, dragRef.current.originX + dx),
        y: Math.max(0, dragRef.current.originY + dy),
      });
    };
    const onUp = () => {
      dragRef.current = null;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [position]);

  // ===== 缩放逻辑 =====
  const handleResizeStart = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    resizeRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      originW: size.width,
      originH: size.height,
    };
    const onMove = (ev: MouseEvent) => {
      if (!resizeRef.current) return;
      const dw = ev.clientX - resizeRef.current.startX;
      const dh = ev.clientY - resizeRef.current.startY;
      setSize({
        width: Math.max(MIN_WIDTH, resizeRef.current.originW + dw),
        height: Math.max(MIN_HEIGHT, resizeRef.current.originH + dh),
      });
    };
    const onUp = () => {
      resizeRef.current = null;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [size]);

  // ===== 手动刷新 =====
  const handleRefresh = useCallback(() => {
    setLoading(true);
    Promise.all([fetchMetrics(), fetchAgents()]).finally(() => setLoading(false));
  }, [fetchMetrics, fetchAgents]);

  // ===== 重置指标 =====
  const handleReset = useCallback(async () => {
    try {
      await resetMetrics();
      await fetchMetrics();
    } catch (err) {
      console.error('[ObservabilityPanel] 重置指标失败:', err);
    }
  }, [fetchMetrics]);

  // ===== 计算指标卡数据 =====
  const agentCallTotal = metrics?.agentMetrics?.reduce((sum, m) => sum + m.count, 0) ?? 0;
  const toolCallTotal = metrics?.toolMetrics?.reduce((sum, m) => sum + m.count, 0) ?? 0;
  const errorTotal = metrics?.errorMetrics?.reduce((sum, m) => sum + m.count, 0) ?? 0;
  const llmAvg = metrics?.llmAvgElapsedMs ?? 0;

  return (
    <div
      className="absolute z-30 glass-panel-strong rounded-xl border border-neon-cyan/40 shadow-[0_8px_40px_-8px_rgba(34,211,238,0.3)] flex flex-col"
      style={{
        left: position.x,
        top: position.y,
        width: size.width,
        height: size.height,
      }}
    >
      {/* ===== 标题栏（可拖拽） ===== */}
      <div
        className="flex items-center justify-between px-4 py-2.5 border-b border-neon-cyan/20 cursor-move select-none"
        onMouseDown={handleDragStart}
      >
        <div className="flex items-center gap-2">
          <Activity size={16} className="text-neon-cyan" />
          <span className="text-sm font-semibold text-neon-cyan tracking-wide">
            可观测性面板
          </span>
          <span className="text-xs text-slate-500 ml-2">
            SSE {connected ? '●' : '○'} {metrics ? `快照 ${new Date(metrics.snapshotTime).toLocaleTimeString('zh-CN', { hour12: false })}` : ''}
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          {/* 暂停/恢复轮询 */}
          <button
            onClick={() => setPaused((p) => !p)}
            title={paused ? '恢复实时刷新' : '暂停实时刷新'}
            className="p-1.5 rounded hover:bg-white/10 transition-colors"
          >
            {paused ? <Play size={14} className="text-neon-green" /> : <Pause size={14} className="text-amber-400" />}
          </button>
          {/* 手动刷新 */}
          <button
            onClick={handleRefresh}
            title="手动刷新"
            className="p-1.5 rounded hover:bg-white/10 transition-colors"
          >
            <RefreshCw size={14} className={`text-slate-300 ${loading ? 'animate-spin' : ''}`} />
          </button>
          {/* 重置指标 */}
          <button
            onClick={handleReset}
            title="重置累计指标"
            className="p-1.5 rounded hover:bg-white/10 transition-colors"
          >
            <AlertTriangle size={14} className="text-amber-400" />
          </button>
          {/* 清空事件 */}
          <button
            onClick={clearEvents}
            title="清空事件流"
            className="p-1.5 rounded hover:bg-white/10 transition-colors"
          >
            <Wrench size={14} className="text-neon-purple" />
          </button>
          {/* 关闭 */}
          <button
            onClick={onClose}
            title="关闭"
            className="p-1.5 rounded hover:bg-red-500/20 transition-colors"
          >
            <X size={14} className="text-red-400" />
          </button>
        </div>
      </div>

      {/* ===== 主内容区 ===== */}
      <div className="flex-1 flex gap-2 p-2 min-h-0">
        {/* 左侧：指标卡 + 吞吐量图 + Agent 拓扑 */}
        <div className="flex-1 flex flex-col gap-2 min-w-0">
          {/* 指标卡片网格 */}
          <div className="grid grid-cols-4 gap-2">
            <MetricCard
              title="LLM 调用"
              value={metrics?.llmCallTotal ?? 0}
              subtitle={`平均 ${llmAvg.toFixed(0)}ms`}
              icon={<Cpu size={14} />}
              color="cyan"
            />
            <MetricCard
              title="Agent 调用"
              value={agentCallTotal}
              subtitle={`${metrics?.agentMetrics?.length ?? 0} 个 Agent`}
              icon={<Activity size={14} />}
              color="purple"
            />
            <MetricCard
              title="工具调用"
              value={toolCallTotal}
              subtitle={`${metrics?.toolMetrics?.length ?? 0} 个工具`}
              icon={<Wrench size={14} />}
              color="green"
            />
            <MetricCard
              title="错误数"
              value={errorTotal}
              subtitle={`SSE 订阅 ${metrics?.subscriberCount ?? 0}`}
              icon={<AlertTriangle size={14} />}
              color={errorTotal > 0 ? 'pink' : 'amber'}
            />
          </div>

          {/* 吞吐量折线图 */}
          <ThroughputChart data={metrics?.throughputSeries ?? []} />

          {/* Agent 拓扑图（填充剩余空间） */}
          <div className="flex-1 min-h-0">
            <AgentTopology nodes={agents} />
          </div>
        </div>

        {/* 右侧：实时事件流时间线 */}
        <div className="w-[340px] shrink-0 flex flex-col">
          <EventTimeline events={events} connected={connected} />
        </div>
      </div>

      {/* ===== 缩放手柄（右下角） ===== */}
      <div
        className="absolute bottom-0 right-0 w-4 h-4 cursor-nwse-resize"
        onMouseDown={handleResizeStart}
      >
        <svg viewBox="0 0 16 16" className="w-full h-full text-neon-cyan/60">
          <path d="M 16 0 L 0 16 M 16 6 L 6 16 M 16 12 L 12 16" stroke="currentColor" strokeWidth="1.5" fill="none" />
        </svg>
      </div>
    </div>
  );
}
