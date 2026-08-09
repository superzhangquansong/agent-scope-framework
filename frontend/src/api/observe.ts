/**
 * 可观测性 API 客户端
 *
 * 对接 hdl-agent 后端 /api/observe/* 接口：
 *  - GET  /api/observe/events   —— SSE 实时事件流订阅（长连接）
 *  - GET  /api/observe/metrics  —— 查询累计指标快照
 *  - GET  /api/observe/agents   —— 查询 Agent 节点列表（含拓扑关系）
 *  - GET  /api/observe/traces   —— 查询最近历史事件（调用链 trace）
 *  - POST /api/observe/reset    —— 重置所有累计指标
 *
 * 与 client.ts 共享同一 API_BASE 和 SessionToken 注入逻辑，
 * 但独立成文件便于维护，避免 client.ts 过度膨胀。
 */

// 复用 client.ts 中的 API_BASE 和 auth 工具
import { getSessionToken } from './client';

/** 后端 API 基础地址（与 client.ts 保持一致） */
const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8788';

/** API Key 请求头名 */
const API_KEY_HEADER = 'X-API-Key';
/** API Key 值 */
const API_KEY_VALUE = 'scope-framework-secret-key-2026';

// ===== 类型定义（与后端 VO 对齐）=====

/** 事件类型枚举（与 AgentEvent.EventType 对齐） */
export type EventType =
  | 'AGENT_START'
  | 'AGENT_END'
  | 'LLM_CALL_START'
  | 'LLM_CALL_END'
  | 'TOOL_CALL_START'
  | 'TOOL_CALL_END'
  | 'MEMORY_LOAD'
  | 'MEMORY_SAVE'
  | 'RAG_SEARCH'
  | 'ERROR'
  | 'CUSTOM'
  | 'UNKNOWN';

/** Agent 事件 VO（与 ObserveEventVO 对齐） */
export interface ObserveEvent {
  type: EventType;
  name?: string;
  agentId?: string;
  userId?: string;
  tenantId?: number;
  traceId?: string;
  elapsedMs?: number;
  timestamp?: number; // 后端 Date 序列化为 long（毫秒时间戳）
  data?: Record<string, unknown>;
}

/** Agent 调用指标 */
export interface AgentMetric {
  agentId: string;
  count: number;
  elapsedTotalMs?: number;
  avgElapsedMs?: number;
  errorCount?: number;
}

/** 工具调用指标 */
export interface ToolMetric {
  toolName: string;
  count: number;
}

/** 错误指标 */
export interface ErrorMetric {
  agentId: string;
  count: number;
}

/** 吞吐量数据点 */
export interface ThroughputPoint {
  timestamp: number; // 秒级时间戳
  count: number;
}

/** 指标快照 VO（与 ObserveMetricsVO 对齐） */
export interface ObserveMetrics {
  agentMetrics: AgentMetric[];
  toolMetrics: ToolMetric[];
  errorMetrics: ErrorMetric[];
  llmCallTotal: number;
  llmElapsedTotalMs: number;
  llmAvgElapsedMs: number;
  subscriberCount: number;
  throughputSeries: ThroughputPoint[];
  snapshotTime: number;
}

/** Agent 节点 VO（与 AgentNodeVO 对齐） */
export interface AgentNode {
  agentId: string;
  displayName: string;
  category: string;
  callCount: number;
  elapsedTotalMs?: number;
  avgElapsedMs?: number;
  errorCount: number;
  healthStatus: 'healthy' | 'warning' | 'error';
  subAgents: string[];
}

/** 统一 API 响应（与 client.ts 的 ApiResult 对齐） */
interface ApiResult<T> {
  success: boolean;
  code: number;
  message: string;
  data: T;
  total?: number;
}

// ===== API 调用函数 =====

/**
 * 查询累计指标快照。
 * GET /api/observe/metrics
 */
export async function getMetrics(): Promise<ObserveMetrics> {
  const headers: Record<string, string> = {};
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  const resp = await fetch(`${API_BASE}/api/observe/metrics`, { headers });
  const result: ApiResult<ObserveMetrics> = await resp.json();
  if (!result.success) {
    throw new Error(result.message || '查询指标失败');
  }
  return result.data;
}

/**
 * 查询 Agent 节点列表（含拓扑关系）。
 * GET /api/observe/agents
 */
export async function getAgents(): Promise<AgentNode[]> {
  const headers: Record<string, string> = {};
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  const resp = await fetch(`${API_BASE}/api/observe/agents`, { headers });
  const result: ApiResult<AgentNode[]> = await resp.json();
  if (!result.success) {
    throw new Error(result.message || '查询 Agent 列表失败');
  }
  return result.data ?? [];
}

/**
 * 查询最近历史事件（调用链 trace）。
 * GET /api/observe/traces?limit=50
 */
export async function getTraces(limit: number = 50): Promise<ObserveEvent[]> {
  const headers: Record<string, string> = {};
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  const resp = await fetch(`${API_BASE}/api/observe/traces?limit=${limit}`, { headers });
  const result: ApiResult<ObserveEvent[]> = await resp.json();
  if (!result.success) {
    throw new Error(result.message || '查询调用链失败');
  }
  return result.data ?? [];
}

/**
 * 重置所有累计指标。
 * POST /api/observe/reset
 */
export async function resetMetrics(): Promise<void> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  const resp = await fetch(`${API_BASE}/api/observe/reset`, {
    method: 'POST',
    headers,
    body: '{}',
  });
  const result: ApiResult<void> = await resp.json();
  if (!result.success) {
    throw new Error(result.message || '重置指标失败');
  }
}

// ===== SSE 事件流订阅 =====

/** SSE 事件回调 */
export interface ObserveSseCallbacks {
  /** 收到初始快照（连接建立时推送的最近 10 条历史事件） */
  onSnapshot?: (events: ObserveEvent[]) => void;
  /** 收到实时事件 */
  onEvent?: (event: ObserveEvent) => void;
  /** 连接错误 */
  onError?: (error: Event) => void;
  /** 连接打开 */
  onOpen?: () => void;
}

/**
 * 订阅可观测性 SSE 事件流。
 *
 * 使用浏览器原生 EventSource API（GET 请求，符合 SSE 规范）。
 * 后端 /api/observe/events 接口产生两种事件：
 *  - snapshot：连接建立时推送的最近 10 条历史事件
 *  - event：实时事件推送
 *
 * @param callbacks SSE 回调
 * @returns EventSource 实例（调用 .close() 主动断开）
 */
export function subscribeObserveEvents(callbacks: ObserveSseCallbacks): EventSource {
  // 构造 URL（EventSource 不支持自定义请求头，SessionToken 通过 URL 参数传递）
  const url = new URL(`${API_BASE}/api/observe/events`);
  const token = getSessionToken();
  if (token) {
    url.searchParams.set('token', token);
  }

  // 创建 EventSource 连接
  const es = new EventSource(url.toString());

  es.onopen = () => {
    callbacks.onOpen?.();
  };

  // 监听 snapshot 事件（初始历史快照）
  es.addEventListener('snapshot', (e: MessageEvent) => {
    try {
      const payload = JSON.parse(e.data);
      const events: ObserveEvent[] = payload.events ?? [];
      callbacks.onSnapshot?.(events);
    } catch (err) {
      console.error('[observe SSE] 解析 snapshot 失败:', err);
    }
  });

  // 监听 event 事件（实时事件）
  es.addEventListener('event', (e: MessageEvent) => {
    try {
      const event: ObserveEvent = JSON.parse(e.data);
      callbacks.onEvent?.(event);
    } catch (err) {
      console.error('[observe SSE] 解析 event 失败:', err);
    }
  });

  es.onerror = (e) => {
    callbacks.onError?.(e);
  };

  return es;
}
