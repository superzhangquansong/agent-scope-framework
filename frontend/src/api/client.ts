/**
 * 后端 API 客户端
 *
 * 对接 hdl-agent 后端：
 *  - 登录 / 登出 / 会话状态 / 切换房屋
 *  - Agent 列表 / 路由列表
 *  - 配置热重载
 *  - SSE 流式聊天（核心，6 种事件：thinking / token / result / error / need_login / done）
 *
 * SSE 事件说明：
 *  - thinking:    模型思考中（提示文案）
 *  - token:       流式 token 输出
 *  - result:      最终结构化结果，携带固定 JSON {routePath, data, message}
 *                 routePath 非 null 时前端跳转对应路由组件展示 data
 *  - error:       流程异常
 *  - need_login:  后端要求登录（前端弹 LoginModal，登录后重发原消息）
 *  - done:        流程结束通知（携带 costMs 耗时）
 */

// 当前项目后端直连（AgentScope Framework，端口 8788）
const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8788';

if (!import.meta.env.VITE_API_BASE && import.meta.env.DEV) {
  console.warn('[client] VITE_API_BASE 未配置，使用默认值:', API_BASE);
}

/** SessionToken 在 localStorage 的 key */
const SESSION_TOKEN_KEY = 'hdl_agent_session_token';

/** SessionToken 请求头名 */
const SESSION_TOKEN_HEADER = 'X-Session-Token';

/** API Key 请求头名 */
const API_KEY_HEADER = 'X-API-Key';
/** API Key 值 */
const API_KEY_VALUE = 'scope-framework-secret-key-2026';

/** Session UUID 在 localStorage 的 key */
const SESSION_UUID_KEY = 'scope_framework_session_uuid';

/** 生成 UUID v4 */
function generateUuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

/** 获取/初始化 session UUID */
export function getSessionUuid(): string {
  let id = localStorage.getItem(SESSION_UUID_KEY);
  if (!id) {
    id = generateUuid();
    localStorage.setItem(SESSION_UUID_KEY, id);
  }
  return id;
}

/** 刷新 session UUID */
export function refreshSessionUuid() {
  localStorage.setItem(SESSION_UUID_KEY, generateUuid());
}

/** 获取本地存储的 SessionToken */
export function getSessionToken(): string | null {
  return localStorage.getItem(SESSION_TOKEN_KEY);
}

/** 保存 / 清除 SessionToken */
export function setSessionToken(token: string | null) {
  if (token) {
    localStorage.setItem(SESSION_TOKEN_KEY, token);
  } else {
    localStorage.removeItem(SESSION_TOKEN_KEY);
  }
}

// ===== API 路径常量（消除魔法字符串，便于维护）=====

const API_PATH = {
  // 认证相关
  AUTH_LOGIN: '/api/auth/login',
  AUTH_LOGOUT: '/api/auth/logout',
  AUTH_STATUS: '/api/auth/status',
  AUTH_SWITCH_HOME: '/api/auth/switchHome',
  // Agent 与路由
  AGENTS: '/api/agents',
  ROUTES: '/api/routes',
  // 配置热重载
  CONFIG_RELOAD: '/api/config/reload',
  // Agent YML 编辑器（通过 Nacos ConfigService 读写，保存后自动热重载）
  AGENT_YML_LIST: '/api/yml/list',
  AGENT_YML_READ: '/api/yml/get',
  AGENT_YML_SAVE: '/api/yml/save',
  // 聊天 SSE（AgentScope Framework 接口）
  CHAT_SEND: '/api/v1/chat/stream',
  // HITL 权限确认接口
  CHAT_CONFIRM: '/api/v1/chat/confirm',
  // 设备直连 REST 接口
  DEVICE_LIST: '/api/device/list',
  DEVICE_DETAIL: '/api/device/detail',
  DEVICE_CONTROL: '/api/device/control',
  // 房屋直连 REST 接口
  HOME_LIST: '/api/home/list',
  // 场景直连 REST 接口
  SCENE_CREATE: '/api/scene/create',
  SCENE_UPDATE: '/api/scene/update',
  // 产品与 SKU REST 接口
  PRODUCT_DETAIL: '/api/product/detail',
  PRODUCT_SEARCH: '/api/product/search',
  // 场景列表 / 执行 / 详情 / 删除
  SCENE_LIST: '/api/scene/list',
  SCENE_EXECUTE: '/api/scene/execute',
  SCENE_DETAIL: '/api/scene/detail',
  SCENE_DELETE: '/api/scene/delete',
  // 购物车
  CART_ADD: '/api/cart/add',
  CART_LIST: '/api/cart/list',
  // 户型图方案推荐
  FLOORPLAN_ANALYZE: '/api/floorplan/analyze',
  // 储能电站 REST 接口
  ENERGY_STATION_DETAIL: '/api/energy/station/detail',
  ENERGY_BATTERY_REPORT: '/api/energy/battery/report',
  ENERGY_SAVINGS_REPORT: '/api/energy/savings/report',
  ENERGY_INVERTER_INFO: '/api/energy/inverter/info',
  ENERGY_FAULT_DIAGNOSIS: '/api/energy/fault/diagnosis',
  // 记忆管理（MySQL 持久化：保存/查询/压缩/清空）
  MEMORY_SAVE: '/api/memory/save',
  MEMORY_HISTORY: '/api/memory/history',
  MEMORY_SUMMARY: '/api/memory/summary',
  MEMORY_CHAT_HISTORY: '/api/memory/chat-history',
  MEMORY_COMPRESS: '/api/memory/compress',
  MEMORY_CLEAR: '/api/memory/clear',
  // LLM 模型配置管理
  LLM_CONFIG_GET: '/api/llm/config',
  LLM_CONFIG_UPDATE: '/api/llm/config',
  LLM_SWITCH: '/api/llm/switch',
  // AI 3D 模型管理
  AI_MODEL_LIST: '/api/ai-model/list',
  AI_MODEL_GET: '/api/ai-model/get',
  AI_MODEL_ADD: '/api/ai-model/add',
  AI_MODEL_UPDATE: '/api/ai-model/update',
  AI_MODEL_SET_DEFAULT: '/api/ai-model/set-default',
  AI_MODEL_DELETE: '/api/ai-model/delete',
  // LLM 模型配置管理（数据库驱动）
  LLM_MODEL_LIST: '/api/llm-model/list',
  LLM_MODEL_GET: '/api/llm-model/get',
  LLM_MODEL_ADD: '/api/llm-model/add',
  LLM_MODEL_UPDATE: '/api/llm-model/update',
  LLM_MODEL_SET_DEFAULT: '/api/llm-model/set-default',
  LLM_MODEL_DELETE: '/api/llm-model/delete',
  LLM_MODEL_APPLY: '/api/llm-model/apply',
  // RAG 知识库管理（文档上传/列表/删除/检索/分段/混合检索）
  RAG_LIST: '/api/rag/list',
  RAG_UPLOAD: '/api/rag/upload',
  RAG_SEARCH: '/api/rag/search',
  RAG_DELETE: '/api/rag/delete',
  /** 获取文档分段列表（v4.4.4 新增） */
  RAG_CHUNKS: '/api/rag/chunks',
  /** 混合检索（向量/全文/混合+重排序，v4.4.4 新增） */
  RAG_HYBRID_SEARCH: '/api/rag/hybrid-search',
} as const;

/** SSE 事件类型常量（与后端对齐：thinking / token / result / error / need_login / done / agent_step / permission_paused） */
const SSE_EVENT = {
  THINKING: 'thinking',
  TOKEN: 'token',
  RESULT: 'result',
  ERROR: 'error',
  NEED_LOGIN: 'need_login',
  NEED_SELECT_HOME: 'need_select_home',
  DONE: 'done',
  /** Agent 执行步骤（思考/工具调用过程，实时展示 ReAct 推理过程） */
  AGENT_STEP: 'agent_step',
  /** HITL 权限确认暂停（需要用户在输入框上方点击确认/取消） */
  PERMISSION_PAUSED: 'permission_paused',
} as const;

export type SseEventType = (typeof SSE_EVENT)[keyof typeof SSE_EVENT];

// ===== 通用响应 =====

/** 通用响应 */
export interface ApiResult<T = unknown> {
  code: number;
  message: string;
  data: T;
  success: boolean;
}

/** Agent 定义 */
export interface AgentDefinition {
  agentId: string;
  name: string;
  category?: string;
  description?: string;
  defaultIntent?: string;
  sseEventType?: string;
  requiresConfirmation?: boolean;
  keywords?: string[];
  skillId?: string;
  order?: number;
  enabled?: boolean;
}

/** 可用路由定义（getRoutes 返回） */
export interface RouteDefinition {
  path: string;
  name: string;
  description?: string;
}

// ===== SSE 事件数据类型 =====

interface ThinkingData { content?: string; }
interface TokenData { content?: string; token?: string; text?: string; }

/** result 事件固定数据结构 */
export interface ResultData {
  /** 路由路径，非 null 时前端跳转对应组件；null 表示仅展示 message */
  routePath: string | null;
  /** 传递给路由组件的结构化数据 */
  data: Record<string, unknown>;
  /** 人类可读的结果说明 */
  message: string;
}

interface ErrorData { message?: string; error?: string; }

/** need_login 事件数据：要求前端弹登录框 */
interface NeedLoginData { message?: string; }

/** need_select_home 事件数据：要求前端弹房屋选择框 */
interface NeedSelectHomeData {
  message?: string;
  data?: { homes?: Array<{ homeId: string; homeName: string; homeType?: string }> };
}

/** done 事件数据：流程结束通知（携带耗时） */
interface DoneData { costMs?: number; }

/** permission_paused 事件数据：HITL 权限确认暂停 */
export interface PermissionPausedData {
  /** 提示消息 */
  message: string;
  /** 待确认的工具调用列表 */
  toolCalls?: Array<{
    /** 工具调用 ID（确认时需传回） */
    toolCallId: string;
    /** 工具名称 */
    toolName: string;
    /** 工具入参 */
    input?: Record<string, unknown>;
  }>;
}

/**
 * agent_step 事件数据：Agent 执行步骤（与后端 SSE agent_step 事件对齐）。
 *
 * <p>用于前端实时展示 AgentScope ReAct 推理过程，包括 Agent 启动/结束、
 * LLM 推理调用、工具调用等步骤。</p>
 */
export interface AgentStepData {
  /** 步骤类型：agent_start / agent_end / llm_call / tool_call */
  step: string;
  /** Agent ID（如 hdl-root / device-control-agent） */
  agentId?: string;
  /** 展示消息（如 "Agent 启动: hdl-root" / "LLM 推理完成"） */
  message?: string;
  /** 工具名（tool_call 类型有值，如 query_device_list） */
  toolName?: string;
  /** 模型名（llm_call 类型有值，如 DashScopeChatModel） */
  modelName?: string;
  /** 耗时（毫秒） */
  elapsedMs?: number;
  /** 工具调用状态（SUCCESS / FAILED） */
  state?: string;
  /** LLM 调用 token 数（llm_call 类型有值） */
  tokenCount?: number;
  /** 事件时间戳（毫秒） */
  timestamp: number;
}

/** 多模态图片信息（与后端 ImageInfo 对齐） */
export interface ImageInfo {
  /** Base64 编码的图片数据（不含 data URL 前缀） */
  base64: string;
  /** MIME 类型（如 image/png、image/jpeg） */
  mimeType: string;
  /** 原始文件名 */
  fileName: string;
}

/** SSE 事件回调 */
export interface SseCallbacks {
  onThinking?: (data: ThinkingData) => void;
  onToken?: (data: TokenData | string) => void;
  onResult?: (data: ResultData) => void;
  onError?: (data: ErrorData) => void;
  onNeedLogin?: (data: NeedLoginData) => void;
  onNeedSelectHome?: (data: NeedSelectHomeData) => void;
  onDone?: (data: DoneData) => void;
  /** Agent 执行步骤（思考/工具调用过程，实时展示 ReAct 推理过程） */
  onAgentStep?: (data: AgentStepData) => void;
  /** HITL 权限确认暂停（需要用户确认/取消工具调用） */
  onPermissionPaused?: (data: PermissionPausedData) => void;
}

// ===== 通用请求封装 =====

/**
 * POST JSON 请求
 * @param path API 路径
 * @param body 请求体
 * @param signal 可选的 AbortSignal，用于取消请求
 */
async function postJson<T = unknown>(
  path: string,
  body: Record<string, unknown> = {},
  signal?: AbortSignal,
): Promise<ApiResult<T>> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  try {
    const resp = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal,
    });
    return resp.json();
  } catch (e: unknown) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return { code: 0, message: '请求已取消', data: undefined as unknown as T, success: false };
    }
    throw e;
  }
}

/**
 * GET 请求
 * @param path API 路径
 * @param signal 可选的 AbortSignal，用于取消请求
 */
async function getJson<T = unknown>(
  path: string,
  signal?: AbortSignal,
): Promise<ApiResult<T>> {
  const headers: Record<string, string> = {};
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  try {
    const resp = await fetch(`${API_BASE}${path}`, { headers, signal });
    return resp.json();
  } catch (e: unknown) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return { code: 0, message: '请求已取消', data: undefined as unknown as T, success: false };
    }
    throw e;
  }
}

// ===== 业务 API =====

/** 用户登录 */
export async function login(loginName: string, loginPwd: string) {
  return postJson<{ sessionToken?: string; loginName?: string; expiresIn?: number }>(
    API_PATH.AUTH_LOGIN,
    { loginName, loginPwd },
  );
}

/** 登出 */
export async function logout() {
  return postJson(API_PATH.AUTH_LOGOUT, {});
}

/** 查询会话状态 */
export async function getStatus() {
  return getJson<{
    loggedIn?: boolean;
    loginName?: string;
    currentHomeId?: string;
    currentHomeName?: string;
    needSelectHome?: boolean;
    homeQueryFailed?: boolean;
    homes?: Array<{ homeId: string; homeName: string; homeType?: string }>;
  }>(API_PATH.AUTH_STATUS);
}

/** 切换房屋 */
export async function switchHome(homeName: string) {
  return postJson(API_PATH.AUTH_SWITCH_HOME, { homeName });
}

/** 获取 Agent 列表 */
export async function getAgents() {
  return getJson<AgentDefinition[]>(API_PATH.AGENTS);
}

/** 获取可用路由列表 */
export async function getRoutes() {
  return getJson<RouteDefinition[]>(API_PATH.ROUTES);
}

/** 热重载配置 */
export async function reloadConfig() {
  return postJson(API_PATH.CONFIG_RELOAD);
}

// ===== Agent YML 编辑器 API（通过 Nacos ConfigService 读写） =====

/** YML 文件元信息 */
export interface YmlFileInfo {
  dataId: string;
  name: string;
  description: string;
}

/** YML 文件内容 */
export interface YmlContent {
  dataId: string;
  content: string;
}

/** 获取可编辑的 YML 文件列表 */
export async function listAgentYmlFiles(): Promise<ApiResult<YmlFileInfo[]>> {
  return getJson<YmlFileInfo[]>(API_PATH.AGENT_YML_LIST);
}

/** 读取 YML 文件内容 */
export async function readAgentYml(dataId: string): Promise<ApiResult<YmlContent>> {
  return getJson<YmlContent>(`${API_PATH.AGENT_YML_READ}?dataId=${encodeURIComponent(dataId)}`);
}

/** 保存 YML 文件内容（保存后 Nacos 自动推送变更，触发 @RefreshScope 热重载） */
export async function saveAgentYml(dataId: string, content: string): Promise<ApiResult<void>> {
  return postJson(API_PATH.AGENT_YML_SAVE, { dataId, content });
}

/**
 * 发送聊天消息（SSE 流式接收）
 *
 * 使用 fetch + ReadableStream 解析 SSE，因为 EventSource 只支持 GET。
 *
 * @param message  用户本轮输入
 * @param history  历史消息（保留参数兼容旧调用）
 * @param callbacks SSE 事件回调
 * @param signal    可选的 AbortSignal，用于取消流
 * @param images    多模态图片列表
 * @param houseId   房屋 ID（可选）
 */
export async function sendChat(
  message: string,
  history: Array<{ role: string; content: string }> = [],
  callbacks: SseCallbacks,
  signal?: AbortSignal,
  images?: ImageInfo[],
  houseId?: string,
): Promise<void> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;

  // 请求体使用 ChatStreamDTO 格式
  const body: Record<string, unknown> = {
    sessionId: getSessionUuid(),
    userMessage: message,
  };
  if (houseId) {
    body.houseId = houseId;
  }
  if (images && images.length > 0) {
    body.images = images.map(img => img.base64 || img.fileName);
    body.imageType = 'base64';
  }
  const resp = await fetch(`${API_BASE}${API_PATH.CHAT_SEND}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal,
  });

  if (!resp.ok || !resp.body) {
    throw new Error(`HTTP ${resp.status}`);
  }

  await readSseStream(resp.body.getReader(), signal, callbacks);
}

/**
 * 读取 SSE 流并分发事件（sendChat 与 confirmPermission 共用）。
 */
async function readSseStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  signal: AbortSignal | undefined,
  callbacks: SseCallbacks,
): Promise<void> {
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  /** SSE 流读取超时（毫秒）：120 秒无数据则自动中止（LLM 首 token 可能需等待 compaction + 推理） */
  const SSE_READ_TIMEOUT = 120_000;
  /** 是否已收到 [DONE] 标记 */
  let streamDone = false;

  try {
    while (!streamDone) {
      if (signal?.aborted) {
        await reader.cancel();
        break;
      }
      const readResult = await Promise.race([
        reader.read(),
        new Promise<{ done: boolean; value?: Uint8Array }>((_, reject) =>
          setTimeout(() => reject(new DOMException('SSE 读取超时', 'TimeoutError')), SSE_READ_TIMEOUT),
        ),
      ]);
      const { done, value } = readResult;
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split('\n\n');
      buffer = parts.pop() ?? '';
      for (const part of parts) {
        const evt = parseSseMessage(part);
        if (!evt) continue;
        // 收到 [DONE] 标记后立即退出循环，避免 reader 阻塞导致 loading 状态卡死
        if (evt.event === 'done' && evt.data === '[DONE]') {
          dispatchSseEvent(evt, callbacks);
          streamDone = true;
          break;
        }
        dispatchSseEvent(evt, callbacks);
      }
    }
    if (!streamDone && buffer.trim()) {
      const evt = parseSseMessage(buffer);
      if (evt) dispatchSseEvent(evt, callbacks);
    }
  } catch (e: unknown) {
    if (e instanceof DOMException && (e.name === 'AbortError' || e.name === 'TimeoutError')) {
      return;
    }
    throw e;
  }
}

/**
 * HITL 权限确认：用户确认或拒绝工具调用，恢复 Agent 执行。
 *
 * <p>确认接口返回 SSE 流（与 /api/v1/chat/stream 相同格式），
 * 因此本函数不使用 postJson，而是读取 SSE 流并分发事件。</p>
 *
 * @param sessionId   会话 ID
 * @param userId      用户 ID（登录名）
 * @param confirms    确认项列表（每个工具调用一项，toolCallId + allowed）
 * @param callbacks   SSE 事件回调
 * @param houseId     房屋 ID（可选）
 */
export async function confirmPermission(
  sessionId: string,
  userId: string,
  confirms: Array<{ toolCallId: string; toolName: string; allowed: boolean }>,
  callbacks: SseCallbacks,
  houseId?: string,
): Promise<void> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;

  const body: Record<string, unknown> = {
    sessionId,
    userId,
    confirms,
    houseId: houseId ?? '',
    userMessage: '',
  };
  const resp = await fetch(`${API_BASE}${API_PATH.CHAT_CONFIRM}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });

  if (!resp.ok || !resp.body) {
    throw new Error(`HTTP ${resp.status}`);
  }

  // 读取 SSE 流（复用与 sendChat 相同的解析逻辑）
  await readSseStream(resp.body.getReader(), undefined, callbacks);
}

/** SSE 消息解析 */
function parseSseMessage(raw: string): { event: string; data: unknown } | null {
  const lines = raw.split('\n');
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }
  if (dataLines.length === 0) return null;
  const dataStr = dataLines.join('\n');
  try {
    return { event, data: JSON.parse(dataStr) };
  } catch {
    return { event, data: dataStr };
  }
}

/** 分发 SSE 事件（兼容后端事件类型：text_delta -> token, thinking_* -> thinking, model_call/tool_call -> agent_step） */
function dispatchSseEvent(evt: { event: string; data: unknown }, callbacks: SseCallbacks) {
  // 尝试从 JSON data 中提取 type 字段（后端 BO 格式），用于补充识别
  const dataObj = evt.data && typeof evt.data === 'object' ? evt.data as Record<string, unknown> : null;
  const backendType = (dataObj && typeof dataObj.type === 'string') ? dataObj.type : '';
  const eventName = evt.event !== 'message' ? evt.event : (backendType || 'message');

  switch (eventName) {
    // ===== thinking 事件 =====
    case SSE_EVENT.THINKING:
    case 'thinking_start':
    case 'thinking_delta':
    case 'thinking_end':
      callbacks.onThinking?.(dataObj as ThinkingData);
      break;

    // ===== token / text_delta 事件（流式文本输出） =====
    case SSE_EVENT.TOKEN:
    case 'text_delta':
      callbacks.onToken?.((dataObj && dataObj.delta) ? String(dataObj.delta) : (evt.data as string));
      break;

    // ===== result 事件 =====
    case SSE_EVENT.RESULT:
      callbacks.onResult?.(evt.data as ResultData);
      break;

    // ===== error 事件 =====
    case SSE_EVENT.ERROR:
      callbacks.onError?.(evt.data as ErrorData);
      break;

    // ===== need_login 事件 =====
    case SSE_EVENT.NEED_LOGIN:
      callbacks.onNeedLogin?.(evt.data as NeedLoginData);
      break;

    // ===== need_select_home 事件 =====
    case SSE_EVENT.NEED_SELECT_HOME:
      callbacks.onNeedSelectHome?.(evt.data as NeedSelectHomeData);
      break;

    // ===== done 事件 =====
    case SSE_EVENT.DONE:
      callbacks.onDone?.(evt.data as DoneData);
      break;

    // ===== agent_step 事件（ReAct 推理过程：model_call_*/tool_call_*/agent_* 等） =====
    // 排除 tool_call_delta（参数增量片段，非步骤边界）和 __fragment__（框架内部并行片段）
    case SSE_EVENT.AGENT_STEP:
    case 'agent_start':
    case 'agent_end':
    case 'model_call_start':
    case 'model_call_end':
    case 'tool_call_start':
    case 'tool_call_end':
    case 'tool_call_delta':
      {
        const toolName = dataObj?.toolName as string | undefined;
        // 过滤 framework 内部事件：__fragment__ 是并行工具调用的内部片段，tool_call_delta 是参数增量
        if (eventName === 'tool_call_delta' || (toolName && toolName.startsWith('__fragment__'))) {
          break;
        }
        const stepData: AgentStepData = {
          step: eventName,
          message: dataObj?.message as string,
          toolName,
          modelName: dataObj?.modelName as string,
          elapsedMs: dataObj?.durationMs as number,
          state: dataObj?.state as string,
          tokenCount: dataObj?.totalTokens as number,
          timestamp: (dataObj?.timestamp as number) ?? Date.now(),
        };
        callbacks.onAgentStep?.(stepData);
      }
      break;

    // ===== permission_paused 事件（HITL 权限确认暂停） =====
    case SSE_EVENT.PERMISSION_PAUSED:
      callbacks.onPermissionPaused?.(evt.data as PermissionPausedData);
      break;

    default:
      // 忽略不处理的内部事件（tool_result_*, permission_ask 等）
      if (eventName !== 'message' || !eventName.startsWith('tool_result_')) {
        console.warn('[SSE] 未处理事件:', eventName, evt.data);
      }
  }
}

// ===== 设备直连 REST API =====

/** 设备属性键值对 */
export interface DeviceAttribute {
  key: string;
  value: string;
}

/** 设备详情数据 */
export interface DeviceDetailData {
  deviceId: string;
  /** HDL 原始字段名 deviceName（设备控制接口返回） */
  deviceName?: string;
  /** HDL 原始字段名 name（设备详情/列表接口返回） */
  name?: string;
  spk?: string;
  online?: boolean;
  gatewayId?: string;
  attributes?: Array<DeviceAttribute>;
  status?: Array<DeviceAttribute>;
  [key: string]: unknown;
}

/** 查询设备详情（REST 直调，不走 SSE） */
export async function getDeviceDetail(req: {
  deviceId: string;
  gatewayId?: string;
}, signal?: AbortSignal) {
  return postJson<DeviceDetailData>(API_PATH.DEVICE_DETAIL, req as unknown as Record<string, unknown>, signal);
}

/** 查询当前房屋设备列表（REST 直调，不走 SSE） */
export async function getDeviceList(signal?: AbortSignal): Promise<ApiResult<{ devices: DeviceDetailData[]; total: number }>> {
  return postJson<{ devices: DeviceDetailData[]; total: number }>(API_PATH.DEVICE_LIST, {}, signal);
}

/** 控制单个设备（REST 直调，不走 SSE） */
export async function controlDevice(req: {
  deviceId: string;
  gatewayId: string;
  attributes: Array<DeviceAttribute>;
  spk?: string;
  deviceName?: string;
}, signal?: AbortSignal) {
  return postJson<DeviceDetailData>(API_PATH.DEVICE_CONTROL, req as unknown as Record<string, unknown>, signal);
}

// ===== 房屋直连 REST API =====

/** 房屋信息 */
export interface HomeItem {
  homeId: string;
  homeName: string;
  homeType?: string;
  deviceCount?: number;
  remoteControl?: boolean;
}

/** 查询房屋列表（REST 直调，不走 SSE） */
export async function getHomeList(signal?: AbortSignal): Promise<ApiResult<{ homes: HomeItem[]; total: number }>> {
  return postJson<{ homes: HomeItem[]; total: number }>(API_PATH.HOME_LIST, {}, signal);
}

// ===== 场景直连 REST API =====

/** 场景中单个设备的动作配置 */
export interface SceneFunctionDto {
  sid: string;
  delaySeconds?: number;
  status: Array<DeviceAttribute>;
}

/** 创建场景请求 */
export interface CreateSceneRequest {
  sceneName: string;
  /** 编辑场景时必填（对应 HDL userSceneId），创建场景时为 undefined */
  sceneId?: string;
  functions: SceneFunctionDto[];
  collect?: boolean;
  delaySeconds?: number;
  executePush?: boolean;
  /** 网关 ID（HDL 要求 scenes[].gatewayId 必填，从选中设备获取） */
  gatewayId?: string;
}

/** 创建场景（REST 直调，不走 SSE） */
export async function createScene(req: CreateSceneRequest, signal?: AbortSignal) {
  return postJson<{
    sceneId?: string;
    sceneName?: string;
    sceneType?: string;
    devices?: unknown;
  }>(API_PATH.SCENE_CREATE, req as unknown as Record<string, unknown>, signal);
}

/** 编辑场景（REST 直调，不走 SSE）。请求体与 createScene 相同但需额外传 sceneId */
export async function updateScene(req: CreateSceneRequest, signal?: AbortSignal) {
  return postJson<{
    sceneId?: string;
    sceneName?: string;
    sceneType?: string;
    devices?: unknown;
  }>(API_PATH.SCENE_UPDATE, req as unknown as Record<string, unknown>, signal);
}

// ===== 产品详情 REST API =====

/** 产品 SKU 数据结构 */
export interface ProductSku {
  skuId?: string;
  erpNo?: string;
  specsDesc?: string;
  skuSpecs?: string;
  price?: string | number;
  marketPrice?: string | number;
  unifiedPrice?: string | number;
  channelPrice?: string | number;
  skuImage?: string;
  skuImages?: string[];
  /** HDL 原始字段名 images：SKU 图片 URL 字符串数组 */
  images?: unknown[];
  stock?: number;
  shelvesStatus?: number;
  shelvesStatusText?: string;
  unitName?: string;
  /** HDL 原始字段名 accessoriesProductList：SKU 配件产品列表 */
  accessoriesProductList?: Array<Record<string, unknown>>;
  /** 兼容字段 accessoriesList（旧字段名） */
  accessoriesList?: Array<Record<string, unknown>>;
  [k: string]: unknown;
}

/** 产品详情数据结构 */
export interface ProductDetailData {
  productId?: string;
  /** HDL 原始字段名 productNameCn */
  productNameCn?: string;
  productModel?: string;
  productImage?: string;
  /** HDL 原始字段名 images：List<String> 原始 URL 字符串数组 */
  images?: unknown[];
  /**
   * HDL 原始字段名 imagesList：List<ProductImages> 对象数组
   * （含 productImageUlr/productMiddleImageUlr/productSmallImageUlr）
   * 也可能是字符串数组（兼容 SSE 链路重命名后场景）
   */
  imagesList?: unknown[];
  /** 兼容字段 productImages（SSE 链路重命名后的字符串数组） */
  productImages?: unknown[];
  /** HDL 原始字段名 productSkuList */
  productSkuList?: ProductSku[];
  /** 产品级价格字段（用于底价展示） */
  price?: string | number;
  marketPrice?: string | number;
  unifiedPrice?: string | number;
  channelPrice?: string | number;
  supplierName?: string;
  protocol?: string;
  unitName?: string;
  categoryName?: string;
  [k: string]: unknown;
}

/** 查询产品详情（含 SKU 列表） */
export async function getProductDetail(req: { productId: string }, signal?: AbortSignal) {
  return postJson<ProductDetailData>(API_PATH.PRODUCT_DETAIL, req as unknown as Record<string, unknown>, signal);
}

/** 搜索产品列表（REST 直调，不走 SSE） */
export async function searchProduct(req: { productName: string }, signal?: AbortSignal): Promise<ApiResult<Record<string, unknown>>> {
  return postJson<Record<string, unknown>>(API_PATH.PRODUCT_SEARCH, req as unknown as Record<string, unknown>, signal);
}

// ===== 场景列表 / 执行 / 详情 / 删除 REST API =====

/** 场景列表中的单项数据 */
export interface SceneItem {
  /** 场景 ID（SceneController 返回的 VO 字段名，对应 HDL userSceneId） */
  sceneId?: string;
  /** 兼容字段 userSceneId（HDL 原始字段名） */
  userSceneId?: string;
  /** 场景名称（SceneController 返回的 VO 字段名，对应 HDL name） */
  sceneName?: string;
  /** 兼容字段 name（HDL 原始字段名） */
  name?: string;
  sceneType?: string | number;
  /** 是否收藏 */
  collect?: boolean;
  /** 是否可删除（"1" 或 true 表示可删除） */
  canDelete?: string | boolean;
  /** 关联房间名称列表 */
  roomNames?: string[];
  /** 场景描述 */
  sceneDesc?: string;
  [k: string]: unknown;
}

/** 查询场景列表 */
export async function getSceneList(signal?: AbortSignal): Promise<ApiResult<{ total: number; scenes: SceneItem[] }>> {
  return postJson<{ total: number; scenes: SceneItem[] }>(API_PATH.SCENE_LIST, {}, signal);
}

/** 执行场景 */
export async function executeScene(req: { sceneId: string }, signal?: AbortSignal) {
  return postJson<{ sceneId?: string; sceneName?: string; success?: boolean }>(
    API_PATH.SCENE_EXECUTE,
    req as unknown as Record<string, unknown>,
    signal,
  );
}

/** 查询场景详情 */
export async function getSceneDetail(req: { sceneId: string }, signal?: AbortSignal) {
  return postJson<Record<string, unknown>>(API_PATH.SCENE_DETAIL, req as unknown as Record<string, unknown>, signal);
}

/** 删除场景 */
export async function deleteScene(req: { sceneId: string }, signal?: AbortSignal) {
  return postJson<{ sceneId?: string; success?: boolean }>(
    API_PATH.SCENE_DELETE,
    req as unknown as Record<string, unknown>,
    signal,
  );
}

// ===== 购物车加购 REST API =====

/** 加购请求参数 */
export interface AddToCartRequest {
  skuId: string;
  productId: string;
  quantity?: number;
  erpNo?: string;
}

/** 加购（先调用 postJson 传到 /api/cart/add，后续可扩展） */
export async function addToCart(req: AddToCartRequest, signal?: AbortSignal) {
  return postJson<{ success?: boolean; cartCount?: number }>(
    API_PATH.CART_ADD,
    req as unknown as Record<string, unknown>,
    signal,
  );
}

/** 购物车列表项 */
export interface CartListItem {
  /** HDL 原始字段名 shoppingCartsId */
  shoppingCartsId?: string;
  skuId?: string;
  productId?: string;
  /** HDL 原始字段名 productNameCn */
  productNameCn?: string;
  productModel?: string;
  /** HDL 原始字段名 skuNum */
  skuNum?: number;
  price?: string | number;
  skuImage?: string;
  /** HDL 原始字段名 specsData */
  specsData?: string;
  [k: string]: unknown;
}

/** 购物车列表响应数据 */
export interface CartListData {
  /** 购物车商品列表（CartListVo 外层字段名，内层每个 item 透传 HDL 原始字段名） */
  items?: CartListItem[];
  /** 购物车商品总数（CartListVo 外层字段名，对应 HDL totalCount） */
  total?: number;
  totalAmount?: string | number;
}

/** 查询购物车列表（REST 直调，不走 SSE） */
export async function getCartList(signal?: AbortSignal) {
  return postJson<CartListData>(API_PATH.CART_LIST, {}, signal);
}

// ===== 户型图方案推荐 =====

/** 户型图中的坐标位置（百分比，0-100） */
export interface FloorPlanPosition {
  x: number;
  y: number;
}

/** 户型图识别到的房间信息 */
export interface FloorPlanRoom {
  name: string;
  type: string;
  position: FloorPlanPosition;
  estimatedArea?: string;
}

/** 推荐产品信息（含户型图位置标注） */
export interface RecommendedProduct {
  productId?: string;
  productName?: string;
  productModel?: string;
  productImage?: string;
  skuId?: string;
  price?: string;
  quantity: number;
  roomName?: string;
  position: FloorPlanPosition;
  reason?: string;
  category?: string;
  erpNo?: string;
}

/** 户型图方案推荐结果 */
export interface FloorPlanVO {
  planId: string;
  imageUrl: string;
  analysisSummary?: string;
  rooms?: FloorPlanRoom[];
  recommendedProducts?: RecommendedProduct[];
  totalPrice?: string;
  totalProductCount: number;
  /** 用户原始需求文本（如"客厅、厨房、卧室分别挑选智能设备，总价不高于10万"） */
  userRequirement?: string;
  /** 预算上限（元），从用户需求中解析，null 表示无预算约束 */
  budgetLimit?: number | null;
  /** 预算约束状态：within_budget（预算内）/ over_budget（超预算）/ no_budget（无约束） */
  budgetStatus?: string;
  /** 预算使用率（百分比），无预算时为 null */
  budgetUsageRate?: number | null;
}

/**
 * 上传户型图并生成产品推荐方案（multipart/form-data，支持用户自定义需求）。
 *
 * @param file            户型图图片文件（最大 5MB）
 * @param userRequirement 用户自定义需求文本（可选，如"总价不高于10万"）
 * @param signal          AbortSignal
 * @returns 方案推荐结果（含房间、产品、位置、总价、预算约束）
 */
export async function analyzeFloorPlan(
  file: File,
  userRequirement?: string,
  signal?: AbortSignal,
): Promise<ApiResult<FloorPlanVO>> {
  const formData = new FormData();
  formData.append('file', file);
  // 拼接用户需求文本（非空时传入）
  if (userRequirement && userRequirement.trim()) {
    formData.append('userRequirement', userRequirement.trim());
  }
  const headers: Record<string, string> = {};
  const token = getSessionToken();
  if (token) {
    headers[SESSION_TOKEN_HEADER] = token;
  }
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  try {
    const resp = await fetch(`${API_BASE}${API_PATH.FLOORPLAN_ANALYZE}`, {
      method: 'POST',
      headers,
      body: formData,
      signal,
    });
    // 检查响应体是否为空（避免 "Unexpected end of JSON input" 错误）
    const text = await resp.text();
    if (!text || text.trim().length === 0) {
      return {
        code: -1,
        message: `服务器返回空响应（HTTP ${resp.status}），可能是文件超过 5MB 限制或服务超时`,
        data: null as unknown as FloorPlanVO,
        success: false,
      };
    }
    try {
      const json = JSON.parse(text);
      return json as ApiResult<FloorPlanVO>;
    } catch {
      return {
        code: -1,
        message: `服务器响应非 JSON 格式（HTTP ${resp.status}）: ${text.substring(0, 200)}`,
        data: null as unknown as FloorPlanVO,
        success: false,
      };
    }
  } catch (err) {
    return { code: -1, message: (err as Error).message, data: null as unknown as FloorPlanVO, success: false };
  }
}

// ===== LLM 模型配置 =====

/** Ollama 本地模型配置 */
export interface OllamaConfig {
  host?: string;
  model?: string;
  keepAlive?: number;
  options?: Record<string, unknown>;
}

/** 云端 LLM 模型配置 */
export interface CloudConfig {
  apiBase?: string;
  apiKey?: string;
  model?: string;
}

/** LLM 模型配置（provider 路由：ollama / cloud） */
export interface ModelConfig {
  provider?: string;
  ollama?: OllamaConfig;
  cloud?: CloudConfig;
}

/** 获取当前 LLM 模型配置 */
export async function getLlmConfig() {
  return getJson<ModelConfig>(API_PATH.LLM_CONFIG_GET);
}

/** 更新 LLM 模型配置（完整覆盖） */
export async function updateLlmConfig(config: ModelConfig) {
  return postJson<void>(API_PATH.LLM_CONFIG_UPDATE, config as unknown as Record<string, unknown>);
}

/** 切换 LLM provider（ollama / cloud） */
export async function switchLlmProvider(provider: string) {
  return postJson<void>(API_PATH.LLM_SWITCH, { provider });
}

// ===== 储能电站 REST API =====

/** 储能电站详情数据 */
export interface EnergyStationDetailData {
  homeId?: string | number;
  homeName?: string;
  powerStationImage?: string;
  address?: string;
  powerStationType?: number;
  gridTypeText?: string;
  installedCapacity?: string;
  productionTime?: number;
  electrovalence?: string;
  monetaryUnitText?: string;
  monetarySymbol?: string;
  totalCost?: string;
  createTime?: number;
  [k: string]: unknown;
}

/** 电池报告数据 */
export interface EnergyBatteryReportData {
  score?: number;
  scoreDesc?: string;
  chargeElectricity?: string;
  dischargeElectricity?: string;
  chargeElectricityByPv?: string;
  chargeElectricityByPvRatio?: number;
  rateCapacity?: string;
  useRatio?: number;
  cycle?: number;
  cycleTimes?: number;
  [k: string]: unknown;
}

/** 省钱分析数据 */
export interface EnergySavingsReportData {
  amount?: string;
  monetarySymbol?: string;
  total?: {
    high?: string;
    low?: string;
    pv?: string;
  };
  summary?: string;
  [k: string]: unknown;
}

/** 逆变器实时数据（含充放电功率） */
export interface EnergyInverterInfoData {
  /** 电池 SOC（0-100） */
  batterySoc?: string | number;
  /** 当前电池功率（正值=充电，负值=放电，单位 kW） */
  batteryPowerNow?: string | number;
  /** 当前光伏发电功率 */
  powerPvNow?: string | number;
  /** 当前负载功率 */
  powerLoadNow?: string | number;
  /** 当前电表功率 */
  powerRNow?: string | number;
  /** 今日发电量 */
  totalElectricityPvToday?: string | number;
  /** 今日用电量 */
  totalElectricityToday?: string | number;
  /** 当前电价阶段描述（峰/平/谷） */
  gridPhaseTypeDesc?: string;
  /** 系统状态 */
  systemStatus?: string;
  [k: string]: unknown;
}

/** 查询储能电站详情 */
export async function getEnergyStationDetail(req: { homeId: string }, signal?: AbortSignal) {
  return postJson<EnergyStationDetailData>(API_PATH.ENERGY_STATION_DETAIL, req as unknown as Record<string, unknown>, signal);
}

/** 查询储能电池使用报告 */
export async function getEnergyBatteryReport(req: { homeId: string; startTime?: number; endTime?: number }, signal?: AbortSignal) {
  return postJson<EnergyBatteryReportData>(API_PATH.ENERGY_BATTERY_REPORT, req as unknown as Record<string, unknown>, signal);
}

/** 查询储能省钱分析报告 */
export async function getEnergySavingsReport(req: { homeId: string; startTime?: number; endTime?: number }, signal?: AbortSignal) {
  return postJson<EnergySavingsReportData>(API_PATH.ENERGY_SAVINGS_REPORT, req as unknown as Record<string, unknown>, signal);
}

/** 查询逆变器实时数据（含充放电功率） */
export async function getEnergyInverterInfo(req: { homeId?: string }, signal?: AbortSignal) {
  return postJson<EnergyInverterInfoData>(API_PATH.ENERGY_INVERTER_INFO, req as unknown as Record<string, unknown>, signal);
}

/** 单个电站的故障诊断结果 */
export interface FaultStationItem {
  /** 电站 ID */
  homeId?: string;
  /** 电站名称 */
  homeName?: string;
  /** 是否故障 */
  hasFault?: boolean;
  /** 故障描述 */
  faultDesc?: string;
  /** 预期动作（充电/放电/保持） */
  expectedAction?: string;
  /** 实际状态（充电中/放电中/空闲） */
  actualState?: string;
  /** 电池 SOC（0-100） */
  batterySoc?: number;
  /** 备电 SOC（0-100） */
  backupSoc?: number;
  /** 电池功率（正值=充电，负值=放电，单位 kW） */
  batteryPowerNow?: number;
  /** 逆变器完整实时数据 */
  inverterData?: EnergyInverterInfoData;
  [k: string]: unknown;
}

/** 故障排查结果数据（全量扫描模式） */
export interface EnergyFaultDiagnosisData {
  /** 故障电站列表 */
  faultStations?: FaultStationItem[];
  /** 扫描的电站总数 */
  totalStations?: number;
  /** 故障电站数量 */
  faultCount?: number;
  /** 是否全部正常 */
  allNormal?: boolean;
  [k: string]: unknown;
}

/** 查询故障排查结果（全量扫描所有电站） */
export async function getEnergyFaultDiagnosis(req: { homeId?: string }, signal?: AbortSignal) {
  return postJson<EnergyFaultDiagnosisData>(API_PATH.ENERGY_FAULT_DIAGNOSIS, req as unknown as Record<string, unknown>, signal);
}

// ===== 记忆管理（MySQL 持久化，通过后端 API 操作） =====
//
// 记忆存储从 MySQL 数据库加载：
//   - hdl_memory_history：短期记忆（对话原文，达阈值自动压缩）
//   - hdl_memory_summary：长期记忆（LLM 压缩生成的摘要）
//   - hdl_chat_history：全量历史（供前端历史对话列表查询）
//
// 前端不再负责记忆存储，仅通过后端 API 读写。
// ChatController 在每轮对话后自动保存用户输入 + AI 回复到 MySQL，
// 路由/回复时自动从 MySQL 加载历史和摘要作为 LLM 上下文。
//

/** 记忆条目（与后端 MemoryEntry 对齐） */
export interface MemoryEntry {
  role: 'user' | 'assistant';
  content: string;
  timestamp?: number;
  agentId?: string;
}

/** 历史对话记录（hdl_chat_history 表，前端历史列表展示用） */
export interface ChatHistoryItem {
  id?: number;
  userId?: string;
  sessionToken?: string;
  role: string;
  content: string;
  agentId?: string;
  routePath?: string;
  /** AI 回复的结构化业务数据 JSON 字符串（用于历史回显重建界面） */
  resultData?: string;
  /** 创建时间（毫秒时间戳，BIGINT 类型） */
  createTime?: number;
  /** 修改时间（毫秒时间戳） */
  modifyTime?: number;
  /** 创建人 */
  createPeople?: string;
  /** 租户 ID */
  tenantId?: number;
}

/** GET /api/memory/history 返回数据 */
export interface MemoryHistoryData {
  history: Array<{ role: string; content: string }>;
  count: number;
}

/** GET /api/memory/summary 返回数据 */
export interface MemorySummaryData {
  summary: string;
}

/** GET /api/memory/chat-history 返回数据 */
export interface ChatHistoryData {
  chatHistory: ChatHistoryItem[];
  count: number;
}

/**
 * 查询用户最近的记忆条目（用于 LLM 上下文，前端一般不直接调用）。
 *
 * @param limit 返回条数（可选，默认后端 contextHistoryLimit 配置，最大 50）
 * @returns 历史记录数组 + 条数
 */
export async function getMemoryHistory(limit?: number): Promise<ApiResult<MemoryHistoryData>> {
  const query = limit != null ? `?limit=${limit}` : '';
  return getJson<MemoryHistoryData>(`${API_PATH.MEMORY_HISTORY}${query}`);
}

/**
 * 查询用户长期记忆摘要（Markdown 文本）。
 *
 * @returns 摘要文本，无摘要时为空串
 */
export async function getMemorySummary(): Promise<ApiResult<MemorySummaryData>> {
  return getJson<MemorySummaryData>(API_PATH.MEMORY_SUMMARY);
}

/**
 * 查询用户历史对话列表（前端历史对话展示用）。
 *
 * @param limit 返回条数（可选，默认 100，最大 500）
 * @returns 历史对话数组 + 条数
 */
export async function getChatHistory(limit?: number): Promise<ApiResult<ChatHistoryData>> {
  const query = limit != null ? `?limit=${limit}` : '';
  return getJson<ChatHistoryData>(`${API_PATH.MEMORY_CHAT_HISTORY}${query}`);
}

/**
 * 手动触发记忆压缩（取旧记录调 LLM 生成摘要，删除原记录保留最近 N 条）。
 *
 * @returns 压缩结果（compressed=true 表示已压缩，skipped=true 表示未达阈值）
 */
export async function compressMemory(): Promise<ApiResult<{ compressed?: boolean; skipped?: boolean }>> {
  return postJson<{ compressed?: boolean; skipped?: boolean }>(API_PATH.MEMORY_COMPRESS, {});
}

/**
 * 清空用户所有记忆数据（hdl_memory_history + hdl_memory_summary + hdl_chat_history）。
 *
 * <p>用于登出或用户手动清空场景。此操作不可逆。</p>
 *
 * @returns 清空结果
 */
export async function clearMemory(): Promise<ApiResult<{ cleared: boolean }>> {
  // DELETE 请求自动注入 Bearer Token 和 API Key 头
  const headers: Record<string, string> = {};
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  try {
    const resp = await fetch(`${API_BASE}${API_PATH.MEMORY_CLEAR}`, { method: 'DELETE', headers });
    return resp.json();
  } catch (e: unknown) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return { code: 0, message: '请求已取消', data: { cleared: false }, success: false };
    }
    throw e;
  }
}

// ===== AI 3D 模型管理 =====
//
// 对接后端 /api/ai-model/* 接口，管理前端 3D 场景中的模型配置。
// 数据存储在 hdl_ai_model 表，支持多模型管理与默认模型切换。
//

/** AI 3D 模型数据（与后端 AiModel 实体对齐） */
export interface AiModel {
  id?: number;
  /** 模型编码（唯一，如 ai-girl-default） */
  modelCode?: string;
  /** 模型显示名称 */
  modelName?: string;
  /** 模型文件 URL（GLB/GLTF 格式） */
  modelUrl?: string;
  /** 模型类型：glb / gltf / fbx */
  modelType?: string;
  /** 缩略图 URL */
  thumbnailUrl?: string;
  /** 模型描述 */
  description?: string;
  /** 模型缩放比例 */
  scale?: number;
  /** X 轴位置 */
  positionX?: number;
  /** Y 轴位置 */
  positionY?: number;
  /** Z 轴位置 */
  positionZ?: number;
  /** Y 轴旋转角度 */
  rotationY?: number;
  /** 是否默认模型：0=否，1=是 */
  isDefault?: number;
  /** 排序序号 */
  sortOrder?: number;
  /** 状态：active / inactive */
  status?: string;
  /** 创建时间（毫秒时间戳） */
  createTime?: number;
  /** 修改时间（毫秒时间戳） */
  modifyTime?: number;
}

/**
 * 通用 DELETE 请求封装（自动注入 X-Session-Token 头）。
 *
 * @param path API 路径
 */
async function deleteRequest<T = unknown>(path: string): Promise<ApiResult<T>> {
  const headers: Record<string, string> = {};
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  try {
    const resp = await fetch(`${API_BASE}${path}`, { method: 'DELETE', headers });
    return resp.json();
  } catch (e: unknown) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return { code: 0, message: '请求已取消', data: undefined as unknown as T, success: false };
    }
    throw e;
  }
}

/**
 * 通用 POST 请求（带 query 参数，用于 set-default / apply 等接口）。
 *
 * @param path API 路径
 * @param params query 参数
 */
async function postWithQuery<T = unknown>(path: string, params?: Record<string, string>): Promise<ApiResult<T>> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getSessionToken();
  if (token) headers[SESSION_TOKEN_HEADER] = token;
  headers[API_KEY_HEADER] = API_KEY_VALUE;
  const query = params ? '?' + new URLSearchParams(params).toString() : '';
  try {
    const resp = await fetch(`${API_BASE}${path}${query}`, { method: 'POST', headers });
    return resp.json();
  } catch (e: unknown) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      return { code: 0, message: '请求已取消', data: undefined as unknown as T, success: false };
    }
    throw e;
  }
}

/** 查询所有 3D 模型列表（按 sort_order 排序） */
export async function getAiModelList(): Promise<ApiResult<AiModel[]>> {
  return getJson<AiModel[]>(API_PATH.AI_MODEL_LIST);
}

/** 查询单个 3D 模型详情 */
export async function getAiModel(id: number): Promise<ApiResult<AiModel>> {
  return getJson<AiModel>(`${API_PATH.AI_MODEL_GET}?id=${id}`);
}

/** 添加 3D 模型 */
export async function addAiModel(model: AiModel): Promise<ApiResult<number>> {
  return postJson<number>(API_PATH.AI_MODEL_ADD, model as unknown as Record<string, unknown>);
}

/** 更新 3D 模型 */
export async function updateAiModel(model: AiModel): Promise<ApiResult<void>> {
  return postJson<void>(API_PATH.AI_MODEL_UPDATE, model as unknown as Record<string, unknown>);
}

/** 设置默认 3D 模型 */
export async function setDefaultAiModel(id: number): Promise<ApiResult<void>> {
  return postWithQuery<void>(API_PATH.AI_MODEL_SET_DEFAULT, { id: String(id) });
}

/** 删除 3D 模型（逻辑删除） */
export async function deleteAiModel(id: number): Promise<ApiResult<void>> {
  return deleteRequest<void>(`${API_PATH.AI_MODEL_DELETE}?id=${id}`);
}

// ===== LLM 模型配置管理（数据库驱动）=====
//
// 对接后端 /api/llm-model/* 接口，管理数据库中的 LLM 模型配置。
// 支持多模型管理、动态切换（apply 接口无需重启即可切换运行时 LLM）。
// 与 /api/llm/config（model.yml）的关系：数据库配置优先，yml 作为降级方案。
//

/** LLM 模型配置数据（与后端 LlmModel 实体对齐） */
export interface LlmModel {
  id?: number;
  /** 模型编码（唯一，如 qwen2.5-1.5b） */
  modelCode?: string;
  /** 模型显示名称 */
  modelName?: string;
  /** 模型提供者：ollama / cloud */
  provider?: string;
  /** 模型 ID（如 qwen2.5:1.5b / deepseek-chat） */
  modelId?: string;
  /** API 地址 */
  apiBaseUrl?: string;
  /** API Key（cloud 模型必填，列表查询时已脱敏） */
  apiKey?: string;
  /** 最大 token 数 */
  maxTokens?: number;
  /** 温度参数（0.00-2.00） */
  temperature?: number;
  /** 是否默认模型：0=否，1=是 */
  isDefault?: number;
  /** 是否启用：0=禁用，1=启用 */
  isActive?: number;
  /** 排序序号 */
  sortOrder?: number;
  /** 模型描述 */
  description?: string;
  /** 创建时间（毫秒时间戳） */
  createTime?: number;
  /** 修改时间（毫秒时间戳） */
  modifyTime?: number;
}

/** 查询所有 LLM 模型配置列表 */
export async function getLlmModelList(): Promise<ApiResult<LlmModel[]>> {
  return getJson<LlmModel[]>(API_PATH.LLM_MODEL_LIST);
}

/** 查询单个 LLM 模型配置详情 */
export async function getLlmModel(id: number): Promise<ApiResult<LlmModel>> {
  return getJson<LlmModel>(`${API_PATH.LLM_MODEL_GET}?id=${id}`);
}

/** 添加 LLM 模型配置 */
export async function addLlmModel(model: LlmModel): Promise<ApiResult<number>> {
  return postJson<number>(API_PATH.LLM_MODEL_ADD, model as unknown as Record<string, unknown>);
}

/** 更新 LLM 模型配置 */
export async function updateLlmModel(model: LlmModel): Promise<ApiResult<void>> {
  return postJson<void>(API_PATH.LLM_MODEL_UPDATE, model as unknown as Record<string, unknown>);
}

/** 设置默认 LLM 模型配置 */
export async function setDefaultLlmModel(id: number): Promise<ApiResult<void>> {
  return postWithQuery<void>(API_PATH.LLM_MODEL_SET_DEFAULT, { id: String(id) });
}

/** 删除 LLM 模型配置（逻辑删除） */
export async function deleteLlmModel(id: number): Promise<ApiResult<void>> {
  return deleteRequest<void>(`${API_PATH.LLM_MODEL_DELETE}?id=${id}`);
}

/**
 * 应用模型配置到运行时（动态切换 LLM，无需重启）。
 *
 * <p>调用后端 /api/llm-model/apply 接口，从数据库加载指定模型配置，
 * 动态创建/切换 ModelProvider，立即生效。</p>
 *
 * @param id 模型配置 ID
 */
export async function applyLlmModel(id: number): Promise<ApiResult<void>> {
  return postWithQuery<void>(API_PATH.LLM_MODEL_APPLY, { id: String(id) });
}

// ===== AgentScope 特性相关接口 =====

/** AgentScope 特性 VO */
export interface AgentScopeFeature {
  /** 特性编码（唯一标识） */
  code: string;
  /** 特性名称 */
  name: string;
  /** 特性描述 */
  description: string;
  /** 实现方式（类/方法链路） */
  implementation: string;
  /** 测试命令 */
  testCommand: string;
  /** 测试步骤 */
  testStep: string;
}

/** 协同链路步骤 */
export interface CoordinationStep {
  /** 步骤编码 */
  component: string;
  /** 执行者 */
  actor: string;
  /** 动作描述 */
  action: string;
}

/** 协同链路 VO */
export interface Coordination {
  /** 场景编码 */
  sceneCode: string;
  /** 场景名称 */
  sceneName: string;
  /** 用户输入示例 */
  userInput: string;
  /** 场景描述 */
  description: string;
  /** 协同步骤列表 */
  steps: CoordinationStep[];
}

/** Agent 概要 VO */
export interface AgentBrief {
  /** Agent ID */
  agentId: string;
  /** Agent 名称 */
  name: string;
  /** Agent 描述 */
  description: string;
}

/**
 * 拉取 AgentScope 7 大核心特性清单。
 *
 * <p>对应后端 GET /api/agentscope/features 接口。</p>
 */
export async function getAgentScopeFeatures(): Promise<ApiResult<AgentScopeFeature[]>> {
  return getJson('/api/agentscope/features');
}

/**
 * 拉取多 Agent 协同链路示例。
 *
 * <p>对应后端 GET /api/agentscope/coordinations 接口。</p>
 */
export async function getAgentScopeCoordinations(): Promise<ApiResult<Coordination[]>> {
  return getJson('/api/agentscope/coordinations');
}

/**
 * 拉取按分类分组的 Agent 清单（21 个 Agent）。
 *
 * <p>对应后端 GET /api/agentscope/agents/grouped 接口。</p>
 */
export async function getAgentScopeAgentsGrouped(): Promise<ApiResult<Record<string, AgentBrief[]>>> {
  return getJson('/api/agentscope/agents/grouped');
}

// ===== RAG 知识库管理 API =====
//
// 对接后端 /api/rag/* 接口，管理 RAG 知识库文档。
// 支持文档上传（多格式：txt/md/pdf/doc/docx/xls/xlsx）、列表查看、删除、检索测试。
// 所有请求自动注入 X-Session-Token 头（由下方封装处理）。
//

/** RAG 文档数据（与后端 RagDocument 对齐） */
export interface RagDocument {
  id?: number;
  /** 文档编码（唯一标识） */
  docCode: string;
  /** 文档名称（含扩展名） */
  docName: string;
  /** 内容类型（MIME type） */
  contentType?: string;
  /** 文件大小（字节） */
  fileSize?: number;
  /** 分块数量 */
  chunkCount?: number;
  /** 状态：VECTORIZED=已向量化 / KEYWORD_ONLY=仅关键词 / FAILED=失败 */
  status?: string;
  /** 是否已生成摘要（v4.4.4 新增，用于文档列表展示「已生成摘要 / 无摘要」标签） */
  hasSummary?: boolean;
  /** 创建时间（毫秒时间戳） */
  createTime?: number;
}

/** RAG 检索结果片段 */
export interface RagSearchResult {
  /** 来源文档编码 */
  docCode: string;
  /** 来源文档名称 */
  docName: string;
  /** 分块序号 */
  chunkIndex: number;
  /** 分块内容 */
  content: string;
  /** 相似度分数（0-1，越大越相关） */
  score: number;
  /** 分块摘要（如有，v4.4.4 新增） */
  summary?: string | null;
  /** 分段标签（逗号分隔，v4.4.7 新增） */
  tags?: string | null;
}

/** RAG 文档分段（v4.4.4 新增） */
export interface RagChunk {
  /** 分段 ID */
  id: number;
  /** 所属文档编码 */
  docCode: string;
  /** 分段序号（从 0 开始） */
  chunkIndex: number;
  /** 分段内容 */
  content: string;
  /** 分段摘要（null 表示未生成） */
  summary: string | null;
  /** 来源文档名称 */
  docName: string | null;
  /** 标签（逗号分隔） */
  tags: string | null;
  /** 起始位置（字符偏移） */
  startPos: number | null;
  /** 结束位置（字符偏移） */
  endPos: number | null;
}

/** 列出所有 RAG 文档 */
export async function getRagList(): Promise<ApiResult<RagDocument[]>> {
  return getJson<RagDocument[]>(API_PATH.RAG_LIST);
}

/**
 * 上传 RAG 文档（支持上传进度回调）。
 *
 * <p>使用 FormData 上传文件，<b>不要手动设置 Content-Type</b>，
 * 浏览器会自动加上 multipart/form-data; boundary=... ，
 * 手动设置会导致 boundary 丢失，后端解析失败。</p>
 *
 * <p>使用 XMLHttpRequest 而非 fetch，以支持上传进度回调。</p>
 *
 * @param file 文件对象
 * @param onProgress 上传进度回调（参数为 0-100 的整数百分比）
 */
export async function uploadRagDocument(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<ApiResult<RagDocument>> {
  return new Promise((resolve, reject) => {
    const formData = new FormData();
    formData.append('file', file);

    const xhr = new XMLHttpRequest();

    // 上传进度
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    });

    // 上传完成
    xhr.addEventListener('load', () => {
      try {
        resolve(JSON.parse(xhr.responseText) as ApiResult<RagDocument>);
      } catch (e) {
        reject(e);
      }
    });

    // 上传失败
    xhr.addEventListener('error', () => reject(new Error('上传失败')));

    // 上传被取消
    xhr.addEventListener('abort', () => reject(new DOMException('请求已取消', 'AbortError')));

    // 自动注入 Bearer Token 和 API Key 头
    const token = getSessionToken();
    xhr.open('POST', `${API_BASE}${API_PATH.RAG_UPLOAD}`);
    if (token) xhr.setRequestHeader(SESSION_TOKEN_HEADER, token);
    xhr.setRequestHeader(API_KEY_HEADER, API_KEY_VALUE);

    xhr.send(formData);
  });
}

/**
 * 检索 RAG 知识库片段。
 *
 * @param query 检索问题（自动 URL 编码）
 * @param limit 返回条数（可选，默认由后端决定）
 */
export async function searchRag(query: string, limit?: number): Promise<ApiResult<RagSearchResult[]>> {
  const params = new URLSearchParams();
  params.set('query', query);
  if (limit != null) params.set('limit', String(limit));
  return getJson<RagSearchResult[]>(`${API_PATH.RAG_SEARCH}?${params.toString()}`);
}

/** 删除 RAG 文档（按 docCode 删除） */
export async function deleteRagDocument(docCode: string): Promise<ApiResult<boolean>> {
  return deleteRequest<boolean>(`${API_PATH.RAG_DELETE}?docCode=${encodeURIComponent(docCode)}`);
}

/**
 * 获取文档分段列表（v4.4.4 新增）。
 *
 * <p>对应后端 GET /api/rag/chunks 接口，返回指定文档的全部分段，
 * 包含分段内容、摘要、序号、来源文档名等信息。</p>
 *
 * @param docCode 文档编码
 */
export async function getRagChunks(docCode: string): Promise<RagChunk[]> {
  const r = await fetch(`${API_BASE}${API_PATH.RAG_CHUNKS}?docCode=${encodeURIComponent(docCode)}`, {
    headers: { SESSION_TOKEN_HEADER: getSessionToken() || '', [API_KEY_HEADER]: API_KEY_VALUE },
  });
  return r.json().then((d) => d.data || []);
}

/**
 * 上传文档（带配置参数，v4.4.4 新增）。
 *
 * <p>使用 FormData 上传文件，并将分段大小、重叠、摘要生成等配置作为 form field 一并提交。
 * 用 XHR 实现以支持上传进度回调（与 uploadRagDocument 一致）。</p>
 *
 * @param file 文件对象
 * @param config 上传配置（分段大小/重叠/是否自动生成摘要/摘要模型）
 * @param onProgress 上传进度回调（0-100）
 */
export async function uploadRagDocumentWithConfig(
  file: File,
  config: { chunkSize: number; chunkOverlap: number; autoSummary: boolean; summaryModel: string },
  onProgress?: (percent: number) => void,
): Promise<ApiResult<RagDocument>> {
  return new Promise((resolve, reject) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('chunkSize', String(config.chunkSize));
    formData.append('chunkOverlap', String(config.chunkOverlap));
    formData.append('autoSummary', String(config.autoSummary));
    formData.append('summaryModel', config.summaryModel);

    const xhr = new XMLHttpRequest();

    // 上传进度
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    });

    // 上传完成
    xhr.addEventListener('load', () => {
      try {
        resolve(JSON.parse(xhr.responseText) as ApiResult<RagDocument>);
      } catch (e) {
        reject(e);
      }
    });

    // 上传失败
    xhr.addEventListener('error', () => reject(new Error('上传失败')));

    // 上传被取消
    xhr.addEventListener('abort', () => reject(new DOMException('请求已取消', 'AbortError')));

    // 自动注入 Bearer Token 和 API Key 头
    const token = getSessionToken();
    xhr.open('POST', `${API_BASE}${API_PATH.RAG_UPLOAD}`);
    if (token) xhr.setRequestHeader(SESSION_TOKEN_HEADER, token);
    xhr.setRequestHeader(API_KEY_HEADER, API_KEY_VALUE);

    xhr.send(formData);
  });
}

/**
 * 混合检索（v4.4.4 新增）。
 *
 * <p>对应后端 GET /api/rag/hybrid-search 接口，支持三种检索模式：</p>
 * <ul>
 *   <li>vector：向量检索</li>
 *   <li>fulltext：全文检索</li>
 *   <li>hybrid：混合检索（按 vectorWeight / fulltextWeight 加权融合，可选重排序）</li>
 * </ul>
 *
 * @param query 检索问题（自动 URL 编码）
 * @param config 检索配置（模式/向量权重/全文权重/是否重排序/重排序模型）
 * @param limit 返回条数（可选）
 */
export async function hybridSearchRag(
  query: string,
  config: { mode: string; vectorWeight: number; fulltextWeight: number; rerank: boolean; rerankModel: string },
  limit?: number,
): Promise<RagSearchResult[]> {
  const params = new URLSearchParams();
  params.set('query', query);
  params.set('mode', config.mode);
  params.set('vectorWeight', String(config.vectorWeight));
  params.set('fulltextWeight', String(config.fulltextWeight));
  params.set('rerank', String(config.rerank));
  params.set('rerankModel', config.rerankModel);
  if (limit != null) params.set('limit', String(limit));
  const r = await fetch(`${API_BASE}${API_PATH.RAG_HYBRID_SEARCH}?${params.toString()}`, {
    headers: { SESSION_TOKEN_HEADER: getSessionToken() || '', [API_KEY_HEADER]: API_KEY_VALUE },
  });
  return r.json().then((d) => d.data || []);
}
