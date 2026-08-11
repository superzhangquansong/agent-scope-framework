import { useState, useEffect, useRef, useCallback } from 'react';
import {
  Send, RefreshCw,
  Settings,
  Mic, MicOff, VolumeX,
  Paperclip, X,
} from 'lucide-react';
import {
  getSessionToken, setSessionToken, getSessionUuid, refreshSessionUuid,
  getStatus, getChatHistory, logout as apiLogout, type ChatHistoryItem,
  type ResultData,
  type AgentStepData,
  type ImageInfo,
  type PermissionPausedData,
  confirmPermission,
} from './api/client';
import { useSSE } from './hooks/useSSE';
import { useVoiceAssistant } from './hooks/useVoiceAssistant';
import ParticleField from './components/ParticleField';
import LoginModal from './components/LoginModal';
import HomeSelectModal from './components/HomeSelectModal';
import AgentYmlEditor from './components/AgentYmlEditor';
import SettingsModal from './components/SettingsModal';
import AiModel3D from './components/AiModel3D';
import HomePlan3D, { type DeviceControlEvent } from './components/HomePlan3D';
import StickyNotesContainer from './components/StickyNotesContainer';
import ObservabilityPanel from './components/observe/ObservabilityPanel';
import { sanitizeErrorMessage } from './shared/utils';

/** 聊天消息（用户/AI） */
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  /** result 事件携带的路由路径（非 null 表示有结构化结果） */
  routePath?: string | null;
  /** result 事件携带的结构化数据 */
  data?: Record<string, unknown>;
  /** 是否流式输出中 */
  streaming?: boolean;
  /** 错误标记 */
  error?: boolean;
  /**
   * 用户随消息一起发送的图片列表（多模态）。
   *
   * <p>仅用户消息有值：用户在输入框上传图片并填写描述后点击发送，
   * 图片随文本一起通过 /chat/send 接口发送给后端。
   * AI 回复消息此字段为空。</p>
   */
  images?: ImageInfo[];
  /**
   * Agent 执行步骤列表（思考/工具调用过程，仅 assistant 流式响应有值）。
   *
   * <p>由 SSE agent_step 事件实时追加，用于在便利贴面板中展示 ReAct 推理过程，
   * 包括 Agent 启动/结束、LLM 推理调用、工具调用等步骤。</p>
   */
  agentSteps?: AgentStepData[];
}

/** 历史消息最大条数（取最近 N 条作为上下文，降低以节省内存） */
const MAX_HISTORY_MESSAGES = 4;

/** 用户消息 ID 前缀 */
const USER_MSG_ID_PREFIX = 'u-';
/** AI 消息 ID 前缀 */
const AI_MSG_ID_PREFIX = 'a-';

/** 登录后重发消息的延迟（ms），避免与弹窗关闭状态冲突 */
const RESEND_DELAY_MS = 100;

/**
 * v4.4.13 新增：场景创建表单语音提交关键词。
 *
 * <p>当最近一条 AI 便利贴是场景创建表单（routePath=/scene/create）时，
 * 用户说这些关键词会通过事件总线触发表单提交（直接调用 createScene REST API），
 * 而非发送到后端走 scene-create-confirm YML Agent 路径。</p>
 *
 * <p>原因：后端 pendingAction 保存的是 LLM 初始预览数据，用户在前端表单上修改后
 * （如修改场景名、增删设备、调整属性值）后端不知情。直接前端提交能保留表单最新状态。</p>
 */
const SCENE_SAVE_KEYWORDS = new Set([
  '保存', '提交', '保存场景', '提交场景', '确认保存', '确认提交',
  '保存提交', '确认', '确定', '创建', '创建场景',
]);

/** 场景创建表单提交事件名（通过 window.dispatchEvent 派发） */
const SCENE_SUBMIT_EVENT = 'hdl-scene-create-submit';

/**
 * 安全解析 JSON 字符串（v3.4.0 新增）。
 *
 * <p>用于解析后端 hdl_chat_history.result_data 字段（AI 回复的结构化业务数据 JSON），
 * 解析失败时返回 undefined，不抛出异常。</p>
 *
 * @param jsonStr JSON 字符串
 * @returns 解析后的对象，失败时返回 undefined
 */
function safeParseJson(jsonStr: string): Record<string, unknown> | undefined {
  try {
    const parsed = JSON.parse(jsonStr);
    // 仅当解析结果为对象时返回，原始类型（string/number 等）不适用
    return typeof parsed === 'object' && parsed !== null ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/** 会话状态（与后端 /api/auth/status 对齐） */
export interface SessionStatus {
  loggedIn: boolean;
  loginName?: string;
  currentHomeId?: string;
  currentHomeName?: string;
  /** 后端要求选择房屋（无当前房屋时为 true） */
  needSelectHome?: boolean;
  /** 房屋列表查询是否失败（HDL API 不可达或返回空） */
  homeQueryFailed?: boolean;
  /** 可选房屋列表 */
  homes?: Array<{ homeId: string; homeName: string; homeType?: string }>;
}

/**
 * HDL Skill Engine 主界面
 *
 * 三栏布局：
 *  - 左侧 270px：Logo + Agent 列表（从 /api/agents 拉取）+ 用户信息（登录/登出）
 *  - 中间主区：Header + 消息列表 + 输入框（或路由结果页）
 *  - 全局粒子背景（ParticleField，22 个粒子，3 种颜色）
 *
 * SSE 事件：thinking / token / result / error / need_login / done
 *  - result 事件携带固定 JSON {routePath, data, message}
 *  - routePath 非 null 时在对话气泡内联渲染结构化结果（不跳新界面）
 *  - need_login 事件触发 LoginModal，登录成功后自动重发 pendingMessage
 *  - 发送消息前检查登录态，未登录则弹 LoginModal + 缓存 pendingMessage
 *  - /api/auth/status 返回 needSelectHome=true 时弹 HomeSelectModal（登录后不自动选房屋）
 */
export default function App() {
  // ===== 状态 =====
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  /** 登录弹窗是否打开 */
  const [loginOpen, setLoginOpen] = useState(false);
  /** 房屋选择弹窗是否打开 */
  const [homeSelectOpen, setHomeSelectOpen] = useState(false);
  /** Agent YML 编辑器是否打开 */
  const [ymlEditorOpen, setYmlEditorOpen] = useState(false);
  /** 设置弹窗是否打开 */
  const [settingsOpen, setSettingsOpen] = useState(false);
  /** 可观测性浮动面板是否打开 */
  const [observeOpen, setObserveOpen] = useState(false);
  /**
   * 待发送的图片列表（多模态输入预览）。
   *
   * <p>用户点击图片按钮选择图片后，图片被读取为 Base64 加入此列表，
   * 在输入框上方以缩略图形式展示，支持删除（增删查改中的"删"）。
   * 用户填写文字描述后点击发送，图片随文字一起通过 /chat/send 接口发送给后端，
   * 发送后清空此列表。</p>
   *
   * <p>每项含：id（前端生成的唯一标识，用于删除定位）、
   * info（ImageInfo，含 base64/mimeType/fileName，发送给后端）、
   * previewUrl（object URL，用于本地预览展示）。</p>
   */
  const [pendingImages, setPendingImages] = useState<Array<{ id: string; info: ImageInfo; previewUrl: string }>>([]);
  /** 图片上传隐藏 input 的引用（由图片按钮触发） */
  const imageInputRef = useRef<HTMLInputElement>(null);
  /**
   * 是否强制登录（v4.4.3 新增）。
   * 为 true 时 LoginModal 隐藏关闭按钮，用户必须完成登录才能使用系统。
   * 访问页面时若未登录则设为 true，登录成功后设为 false。
   * 发消息时触发的登录（onNeedLogin）不设强制，用户可关闭稍后再登录。
   */
  const [loginRequired, setLoginRequired] = useState(false);
  /** 可选房屋列表 */
  const [homeList, setHomeList] = useState<Array<{ homeId: string; homeName: string; homeType?: string }>>([]);
  /** 触发登录/选房屋的原始消息（操作成功后自动重发，保证任务连续性） */
  const [pendingMessage, setPendingMessage] = useState<string>('');
  /** 会话状态（loggedIn / loginName / currentHomeName 等） */
  const [sessionStatus, setSessionStatus] = useState<SessionStatus | null>(null);
  /** 设备控制事件（触发 3D 户型图设备动效） */
  const [deviceEvent, setDeviceEvent] = useState<DeviceControlEvent | null>(null);
  /** 是否显示 3D 户型图（设备控制时切换为 true，由用户点击 X 按钮关闭） */
  const [showHomePlan, setShowHomePlan] = useState(false);
  /** 历史便利贴消息列表（已归档的对话，不会被清空便利贴操作清除） */
  const [historyMessages, setHistoryMessages] = useState<ChatMessage[]>([]);
  /** 对话轮次计数器（每 3 轮自动清空当前便利贴，归档到历史） */
  const conversationRoundCount = useRef(0);

  // 引用
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  /** 当前流式 AI 消息 ID（供 SSE 回调定位更新） */
  const streamingMsgIdRef = useRef<string | null>(null);
  /** HITL 权限暂停前的消息 ID（handleSend finally 置 null 后保留，供 confirm 恢复使用） */
  const hitlMsgIdRef = useRef<string | null>(null);
  /** result 事件是否已到达（用于提前启用输入框，不等 done） */
  const resultReceivedRef = useRef(false);
  /** 当前发送周期内收到的 result 事件数（v3.6.0 多意图支持，0=首个 result，>0=后续 result 创建新便利贴） */
  const resultCountRef = useRef(0);
  /** 登录后重发消息的定时器（卸载时清理） */
  const resendTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** sessionStatus 的 ref（避免 handleSend 闭包过期，登录后重发能读到最新登录态） */
  const sessionStatusRef = useRef<SessionStatus | null>(null);
  /** 当前发送的文本（避免 onNeedSelectHome 闭包中 messages 过期，导致 pendingMessage 丢失） */
  const pendingInputRef = useRef<string>('');
  /** HITL 权限确认对话框数据 */
  const [permissionDialog, setPermissionDialog] = useState<{
    sessionId: string;
    message: string;
    toolCalls: Array<{ toolCallId: string; toolName: string }>;
  } | null>(null);
  /** 确认请求进行中（防止用户在 confirm SSE 流期间输入） */
  const [confirmPending, setConfirmPending] = useState(false);
  /** 确认弹框动画状态 */
  const [dialogVisible, setDialogVisible] = useState(false);
  useEffect(() => {
    sessionStatusRef.current = sessionStatus;
  }, [sessionStatus]);

  // ===== 更新流式消息 =====
  const updateMessage = useCallback((id: string, updater: (m: ChatMessage) => ChatMessage) => {
    setMessages(prev => prev.map(m => (m.id === id ? updater(m) : m)));
  }, []);

  // ===== SSE 回调（由 useSSE hook 管理取消与 sending 状态） =====
  /** 存储 abort 函数引用，供 onPermissionPaused 回调中断 SSE 流 */
  const abortSseRef = useRef<() => void>(() => {});
  const { send, abort, sending, callbacksRef: sseCallbacksRef } = useSSE({
    onThinking: () => {
      const id = streamingMsgIdRef.current;
      if (id) updateMessage(id, m => ({ ...m, content: '正在思考...' }));
    },
    onAgentStep: (data) => {
      // 接收 AgentScope 执行过程中的步骤事件（Agent 启动/结束、LLM 推理、工具调用）
      // 实时追加到当前 streaming 消息的 agentSteps 数组，供便利贴面板展示 ReAct 推理过程
      const id = streamingMsgIdRef.current;
      if (!id || !data) return;
      const step: AgentStepData = {
        step: data.step ?? 'unknown',
        agentId: data.agentId,
        message: data.message,
        toolName: data.toolName,
        modelName: data.modelName,
        elapsedMs: data.elapsedMs,
        state: data.state,
        tokenCount: data.tokenCount,
        timestamp: data.timestamp ?? Date.now(),
      };
      updateMessage(id, m => ({
        ...m,
        agentSteps: [...(m.agentSteps ?? []), step],
      }));
    },
    onToken: (data) => {
      const id = streamingMsgIdRef.current;
      if (!id) return;
      const token = typeof data === 'string' ? data : (data?.token || data?.text || data?.content || '');
      if (token) {
        updateMessage(id, m => {
          // 首个 token 到达时，清空"正在思考..."占位文本，避免前缀残留
          const base = m.content === '正在思考...' ? '' : m.content;
          return { ...m, content: base + token };
        });
        // 【语音播报修复】onToken 阶段不再逐 token 朗读（避免与 onResult 重复播报）
        // chat agent 的朗读统一由 onResult 中的 broadcastText 处理
      }
    },
    onResult: (data: ResultData) => {
      resultReceivedRef.current = true; // UI 已渲染，允许输入
      const id = streamingMsgIdRef.current;
      const routePath = data?.routePath ?? null;
      const resultData = data?.data ?? {};
      console.log('[onResult] routePath=%s, hasData=%s, msgId=%s, dataKeys=%s',
        routePath, !!data?.data, id,
        data?.data != null ? Object.keys(data.data as Record<string, unknown>).join(',') : 'none');
      // v4.4.13 修复：AgentScope 模式下后端将 message 设为 null（非纯文本路由），
      // 回复文本放在 resultData.reply 中。此处优先取 data.message，
      // 为空时回退到 resultData.reply，避免便利贴内容停留在"正在思考..."
      const replyFromData = (resultData as Record<string, unknown>)?.reply as string | undefined;
      const message = data?.message ?? replyFromData ?? '';
      // broadcastText 是后端统一返回的播报文本：
      // - chat agent 场景：null（token 流式已推送，但前端不再逐 token 朗读，改为朗读完整 message）
      // - 非 chat agent 场景：实际播报文本（如"已为您执行设备控制操作"）
      const broadcastText = (data as unknown as Record<string, unknown>)?.broadcastText as string | undefined;

      // ===== v3.6.0 多意图支持：检查当前 streaming 消息是否已结束 =====
      // 单意图场景：第一个 result 到达时 streaming=true，直接更新现有消息
      // 多意图场景：后续 result 到达时 streaming 已被设为 false，需要创建新便利贴
      if (id) {
        // 通过 resultCountRef 判断是否多意图的首个 result
        // resultCountRef 在 handleSend 开头重置为 0，每次 onResult +1
        const isFirstResult = resultCountRef.current === 0;
        resultCountRef.current += 1;

        if (isFirstResult) {
          // 首个 result：更新现有 streaming 消息
          updateMessage(id, m => ({
            ...m,
            content: message || m.content,
            routePath,
            data: resultData,
            streaming: false,
          }));
        } else {
          // 多意图的后续 result：创建新便利贴消息展示当前 result
          const newId = `msg-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
          setMessages(prev => [...prev, {
            id: newId,
            role: 'assistant',
            content: message || '',
            routePath,
            data: resultData,
            streaming: false,
            timestamp: Date.now(),
          }]);
          // 更新 streamingMsgIdRef 指向新消息（后续 onResult 能继续创建新便利贴）
          streamingMsgIdRef.current = newId;
        }
      }
      // 【语音播报修复】统一播报逻辑：
      // - 非 chat agent（broadcastText 非空）：朗读 broadcastText（如"已为您执行设备控制操作"）
      // - chat agent（broadcastText 为空/null）：朗读完整 message（token 阶段不再朗读，避免重复）
      // - 界面展示的内容也要播报
      if (voice.enabled) {
        const textToSpeak = broadcastText || message;
        if (textToSpeak) {
          voiceSpeakRef.current(textToSpeak);
        }
      }
      // 【关键修复】SSE 流程结束后恢复唤醒词监听，确保持续对话
      // 如果有 TTS 朗读，resumeListening 会等朗读完成后再恢复
      if (voice.enabled) {
        voiceResumeRef.current();
      }

      // ===== 设备控制事件：切换到 3D 户型图并触发动效 =====
      // routePath 为 /device/status 时表示设备控制结果
      if (routePath === '/device/status' || routePath === 'device-status') {
        const devices = (resultData as Record<string, unknown>)?.devices as Array<Record<string, unknown>> | undefined;
        if (devices && devices.length > 0) {
          // 取第一个设备（3D 户型图一次展示一个设备动效）
          const dev = devices[0];
          const deviceName = String(dev?.deviceName || dev?.name || '');
          // spk 物模型编码（如 'light.rgb' / 'light.cct' / 'hvac.fan'），用于精准匹配设备类型
          // spk 来源：设备列表/详情接口返回的设备物模型字段，是 HDL 设备的标准类型标识
          const spk = String(dev?.spk ?? '');
          // HDL /device/info 返回的 attributes 是 schema（支持值列表如 ["on","off"]），不是实际值
          // controlAttributes 是用户实际设置的值（如 rgb=蓝色, brightness=50），优先使用
          const rawAttrs = dev?.controlAttributes || dev?.attributes || dev?.status;
          let attributes: Record<string, unknown> = {};
          if (Array.isArray(rawAttrs)) {
            for (const item of rawAttrs as Array<Record<string, unknown>>) {
              const key = String(item?.key ?? '');
              const rawValue = item?.value;
              if (!key) continue;
              // 数值类属性转为 number（on_off/brightness/cct/speed/swing/position）
              if (['on_off', 'brightness', 'cct', 'speed', 'swing', 'position'].includes(key)) {
                const num = Number(rawValue);
                // 只有有效数字才用 number，否则保留原值
                attributes[key] = Number.isFinite(num) ? num : rawValue;
              } else {
                attributes[key] = rawValue;
              }
            }
          } else if (rawAttrs && typeof rawAttrs === 'object') {
            attributes = rawAttrs as Record<string, unknown>;
          }
          // spk 优先用于 3D 户型图设备类型匹配（避免设备名关键词匹配的歧义）
          setDeviceEvent({ deviceName, spk, attributes });
          // 显示 3D 户型图，由用户点击 X 按钮手动退出（不再自动关闭）
          setShowHomePlan(true);
        }
      }

      // ===== 对话轮次计数（v3.4.0 修复：不在 onResult 中清空，改为发送前清空）=====
      // 每完成一轮对话（onResult 触发），计数器 +1
      // 注意：v3.4.0 移除了 onResult 中的自动清空逻辑，改为在 handleSend 开头清空
      // 原因：在 onResult 中清空会导致第 3 轮的 AI 回复刚生成就被清空，用户看不到回复
      conversationRoundCount.current += 1;
    },
    onError: (data) => {
      const id = streamingMsgIdRef.current;
      // 脱敏：过滤掉程序内部信息（JSON出参、堆栈、异常类名等），避免向用户暴露实现细节
      const raw = data?.message || data?.error || '处理失败';
      const sanitized = sanitizeErrorMessage(raw);
      if (id) {
        updateMessage(id, m => ({
          ...m,
          content: sanitized,
          streaming: false,
          error: true,
        }));
      }
      // 检测残留 ASKING 状态：后端已清除框架状态，刷新 session 后自动重试
       if (raw.includes('权限确认状态已过期') || raw.includes('请在前端刷新会话')) {
         refreshSessionUuid();
         // 自动重发上一条用户消息
         const lastUserMsg = messages[messages.length - 1];
         if (lastUserMsg && lastUserMsg.role === 'user') {
           setTimeout(() => handleSendRef.current(lastUserMsg.content), 500);
         }
       }
      // 出错后也恢复唤醒词监听，确保持续对话
      if (voice.enabled) {
        voiceResumeRef.current();
      }
    },
    onNeedLogin: (data) => {
      // 后端要求登录：缓存当前正在发送的消息，登录成功后自动重发
      const id = streamingMsgIdRef.current;
      if (id) {
        // 定位当前 AI 占位消息对应的用户输入作为待恢复消息
        const idx = messages.findIndex(m => m.id === id);
        if (idx > 0 && messages[idx - 1].role === 'user') {
          setPendingMessage(messages[idx - 1].content);
        }
        updateMessage(id, m => ({
          ...m,
          content: data?.message || '请先登录后继续操作',
          streaming: false,
        }));
      }
      setLoginOpen(true);
    },
    onNeedSelectHome: (data) => {
      // 后端要求选择房屋：缓存当前发送的文本（用 ref 避免 messages 闭包过期），选择房屋后自动重发
      if (pendingInputRef.current) {
        setPendingMessage(pendingInputRef.current);
      }
      const id = streamingMsgIdRef.current;
      if (id) {
        updateMessage(id, m => ({
          ...m,
          content: data?.message || '请先选择房屋',
          streaming: false,
        }));
      }
      // 设置房屋列表并弹出选择框
      const homes = data?.data?.homes || [];
      setHomeList(homes);
      setHomeSelectOpen(true);
    },
    onPermissionPaused: (data: PermissionPausedData) => {
      // HITL 权限确认暂停：显示确认/取消弹框
      const toolCalls = (data?.toolCalls ?? []).map(tc => ({
        toolCallId: tc.toolCallId,
        toolName: tc.toolName,
      }));
      setPermissionDialog({
        sessionId: getSessionUuid(),
        message: data?.message || '操作需要您的确认',
        toolCalls,
      });
      // 延迟触发动画（先设置数据，再让 CSS transition 生效）
      setTimeout(() => setDialogVisible(true), 50);
      // 保存消息 ID 到 hitlMsgIdRef（handleSend finally 会将 streamingMsgIdRef 置 null）
      hitlMsgIdRef.current = streamingMsgIdRef.current;
      // 中断当前 SSE 流，释放 sending 状态（后端将在 confirm 接口恢复执行）
      abortSseRef.current();
      // 更新消息状态，允许用户操作确认弹框
      const id = streamingMsgIdRef.current;
      if (id) {
        updateMessage(id, m => ({
          ...m,
          content: data?.message || '请确认以下操作',
          streaming: false,
        }));
      }
    },
    onDone: () => {
      // SSE 流结束：关闭当前 streaming 消息的流式状态
      const id = streamingMsgIdRef.current;
      if (id) {
        updateMessage(id, m => ({ ...m, streaming: false }));
      }
    },
  });

  // 将 abort 存入 ref（必须在 useSSE 之后执行）
  abortSseRef.current = abort;

  // ===== HITL 权限确认处理 =====
  /** 用户点击"确认"：调用 /api/v1/chat/confirm 恢复 Agent 执行 */
  const handlePermissionConfirm = useCallback(async () => {
    if (!permissionDialog) return;
    const { sessionId, toolCalls } = permissionDialog;
    const userId = sessionStatusRef.current?.loginName || '';
    const houseId = sessionStatusRef.current?.currentHomeId || '';
    const confirms = toolCalls.map(tc => ({ ...tc, allowed: true }));
    // 隐藏弹框动画
    setDialogVisible(false);
    setTimeout(() => {
      setPermissionDialog(null);
    }, 200);
    setConfirmPending(true);
    // 恢复 HITL 暂停前的消息 ID（handleSend finally 已将 streamingMsgIdRef 置 null）
    const existingMsgId = hitlMsgIdRef.current;
    if (existingMsgId) {
      updateMessage(existingMsgId, m => ({ ...m, content: '正在执行操作...', streaming: true }));
      streamingMsgIdRef.current = existingMsgId; // 恢复，供 confirm SSE 流的 onResult 使用
    }
    try {
      await confirmPermission(sessionId, userId, confirms, sseCallbacksRef.current, houseId);
    } catch (e) {
      console.error('[App] 权限确认请求失败:', e);
    } finally {
      setConfirmPending(false);
    }
  }, [permissionDialog]);

  /** 用户点击"取消"：拒绝所有工具调用 */
  const handlePermissionCancel = useCallback(async () => {
    if (!permissionDialog) return;
    const { sessionId, toolCalls } = permissionDialog;
    const userId = sessionStatusRef.current?.loginName || '';
    const houseId = sessionStatusRef.current?.currentHomeId || '';
    const confirms = toolCalls.map(tc => ({ ...tc, allowed: false }));
    // 隐藏弹框动画
    setDialogVisible(false);
    setTimeout(() => {
      setPermissionDialog(null);
    }, 200);
    setConfirmPending(true);
    try {
      await confirmPermission(sessionId, userId, confirms, sseCallbacksRef.current, houseId);
    } catch (e) {
      console.error('[App] 权限确认请求失败:', e);
    } finally {
      setConfirmPending(false);
    }
  }, [permissionDialog]);

  // ===== handleSend 的 ref（供 useVoiceAssistant 间接调用，避免闭包过期） =====
  // useVoiceAssistant 在 handleSend 之前声明，但其 onCommand 通过 ref 调用最新 handleSend
  const handleSendRef = useRef<(text: string) => void>(() => {});

  // ===== 语音助手 Hook（唤醒词"你好小东"实时对话） =====
  // - 持续监听唤醒词，唤醒后进入命令模式
  // - 命令识别完成调用 handleSend 发送文本到后端
  // - onToken 中调用 speak() 流式朗读 AI 回复
  const voice = useVoiceAssistant({
    wakeWord: '你好小东',
    onCommand: (text: string) => {
      // 通过 ref 调用最新的 handleSend，避免闭包过期
      handleSendRef.current(text);
    },
  });

  // 缓存 speak 引用（避免 onToken 闭包过期）
  const voiceSpeakRef = useRef<(text: string) => void>(() => {});
  useEffect(() => {
    voiceSpeakRef.current = voice.speak;
  }, [voice.speak]);

  // 缓存 resumeListening 引用（避免 onResult 闭包过期）
  const voiceResumeRef = useRef<() => void>(() => {});
  useEffect(() => {
    voiceResumeRef.current = voice.resumeListening;
  }, [voice.resumeListening]);

  // ===== 初始化：检查登录状态 =====
  useEffect(() => {
    refreshStatus();
  }, []);

  // ===== 卸载时清理重发定时器 =====
  useEffect(() => {
    return () => {
      if (resendTimerRef.current) clearTimeout(resendTimerRef.current);
    };
  }, []);

  // ===== 自动滚动到底部 =====
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // ===== 刷新会话状态 =====
  // 参照 hdl-agent：调用 /api/auth/status，按登录态/选房状态分发，内置加载历史对话
  const refreshStatus = async () => {
    try {
      const r = await getStatus();
      console.log('[refreshStatus] API 响应:', JSON.stringify(r));
      if (r.success && r.data) {
        const status: SessionStatus = {
          loggedIn: !!r.data.loggedIn,
          loginName: r.data.loginName,
          currentHomeId: r.data.currentHomeId,
          currentHomeName: r.data.currentHomeName,
          needSelectHome: r.data.needSelectHome,
          homeQueryFailed: r.data.homeQueryFailed,
          homes: (r.data.homes ?? []).map((h: Record<string, unknown>) => ({
            homeId: String(h.homeId ?? ''),
            homeName: String(h.homeName ?? ''),
            homeType: h.homeType ? String(h.homeType) : undefined,
          })),
        };
        setSessionStatus(status);
        // 未登录 → 强制登录
        if (!r.data.loggedIn) {
          setLoginRequired(true);
          setLoginOpen(true);
        } else {
          setLoginRequired(false);
          // 已登录：加载历史对话
          try {
            const histResult = await getChatHistory(50);
            if (histResult.success && histResult.data && histResult.data.chatHistory.length > 0) {
              const backendHistory: ChatMessage[] = histResult.data.chatHistory.map((item: ChatHistoryItem) => ({
                id: `history_${item.id ?? Date.now()}_${Math.random()}`,
                role: (item.role === 'user' ? 'user' : 'assistant') as 'user' | 'assistant',
                content: item.content || '',
                routePath: item.routePath || null,
                data: item.resultData ? safeParseJson(item.resultData) : undefined,
              }));
              setHistoryMessages(backendHistory);
            }
          } catch (histErr) {
            console.warn('[refreshStatus] 加载历史对话失败', histErr);
          }
        }
        // 需要选房 → 弹出选房弹窗（含 homes 为空的情况，由 homeQueryFailed 区分提示）
        if (r.data.loggedIn && r.data.needSelectHome) {
          if (status.homes && status.homes.length > 0) {
            setHomeList(status.homes);
          }
          setHomeSelectOpen(true);
        }
      } else {
        // Token 失效，清除本地状态并强制登录
        console.warn('[refreshStatus] 状态 API 返回失败: code=%s, msg=%s', r.code, r.message);
        setSessionToken(null);
        setSessionStatus(null);
        setLoginRequired(true);
        setLoginOpen(true);
      }
    } catch (e) {
      // 状态 API 暂时不可达，保留现有登录态（参照 hdl-agent 静默处理）
      console.warn('[refreshStatus] 状态查询失败，保留现有登录态:', e);
    }
  };

  // ===== 发送消息（核心） =====
  /**
   * 处理图片上传（多模态输入）。
   *
   * <p>统一的多模态图片上传入口，取代旧的户型图独立上传逻辑。
   * 图片不一定是户型图，可能是用户就任意图片提问，由后端视觉 Agent（qwen-vl-max）
   * 自主识别图片类型和用户意图后路由到对应子 Agent（如 floor-plan-agent）。</p>
   *
   * <p>流程：</p>
   * <ol>
   *   <li>用户点击图片按钮触发隐藏的 file input（支持多选）</li>
   *   <li>逐张校验类型（image/*）和大小（5MB 限制）</li>
   *   <li>读取为 Base64，封装为 ImageInfo 加入 pendingImages 列表</li>
   *   <li>在输入框上方以缩略图形式展示，用户可点击 × 删除单张（增删查改中的"删"）</li>
   *   <li>用户填写文字描述后点击发送，图片随文字一起通过 /chat/send 接口发送给后端</li>
   * </ol>
   *
   * <p>注意：上传图片后必须再加文字描述才能发送（发送按钮在无文字时禁用），
   * 后端 /chat/send 接口支持多模态（文字、图片等），由 AgentScope 视觉 Agent 处理。</p>
   */
  const handleImageUpload = useCallback(async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files || files.length === 0) {
      return;
    }
    // 逐张校验并读取为 Base64
    const newItems: Array<{ id: string; info: ImageInfo; previewUrl: string }> = [];
    for (const file of Array.from(files)) {
      // 校验文件类型
      if (!file.type.startsWith('image/')) {
        alert(`文件 ${file.name} 不是图片格式，已跳过（支持 png/jpeg/jpg/webp）`);
        continue;
      }
      // 校验文件大小（限制 5MB，与后端 application.yml multipart 配置一致）
      if (file.size > 5 * 1024 * 1024) {
        alert(`图片 ${file.name} 超过 5MB，已跳过，请压缩后重试`);
        continue;
      }
      try {
        // 读取文件为 Base64 字符串（不含 data URL 前缀，后端 ImageInfo.base64 字段约定）
        const base64 = await readFileAsBase64(file);
        newItems.push({
          id: `img-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          info: {
            base64,
            mimeType: file.type || 'image/png',
            fileName: file.name,
          },
          previewUrl: URL.createObjectURL(file),
        });
      } catch (err) {
        alert(`图片 ${file.name} 读取失败: ${sanitizeErrorMessage((err as Error).message)}`);
      }
    }
    if (newItems.length > 0) {
      setPendingImages(prev => [...prev, ...newItems]);
    }
    // 清空 input 的 value，允许重复选择同一文件
    event.target.value = '';
  }, []);

  /**
   * 删除待发送列表中的指定图片（增删查改中的"删"）。
   *
   * @param id 图片项的唯一标识
   */
  const removePendingImage = useCallback((id: string) => {
    setPendingImages(prev => {
      const target = prev.find(item => item.id === id);
      if (target) {
        // 释放 object URL，避免内存泄漏
        URL.revokeObjectURL(target.previewUrl);
      }
      return prev.filter(item => item.id !== id);
    });
  }, []);

  /**
   * 清空所有待发送图片（发送成功后调用）。
   */
  const clearPendingImages = useCallback(() => {
    setPendingImages(prev => {
      prev.forEach(item => URL.revokeObjectURL(item.previewUrl));
      return [];
    });
  }, []);

  /**
   * 将 File 读取为纯 Base64 字符串（不含 data URL 前缀）。
   *
   * @param file 图片文件
   * @returns Base64 编码字符串
   */
  const readFileAsBase64 = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result;
        if (typeof result !== 'string') {
          reject(new Error('图片读取结果非字符串'));
          return;
        }
        // FileReader.readAsDataURL 返回 "data:image/png;base64,xxxx" 格式，
        // 去掉 data URL 前缀，只保留纯 Base64 编码部分（与后端 ImageInfo.base64 约定一致）
        const commaIdx = result.indexOf(',');
        resolve(commaIdx >= 0 ? result.substring(commaIdx + 1) : result);
      };
      reader.onerror = () => reject(reader.error ?? new Error('图片读取失败'));
      reader.readAsDataURL(file);
    });
  };

  const handleSend = useCallback(async (rawText?: string) => {
    const text = (rawText ?? input).trim();
    if (!text || (sending && !resultReceivedRef.current)) return;

    // v4.4.13 新增：场景创建表单语音提交拦截
    // 当最近一条 AI 便利贴是场景创建表单（routePath=/scene/create）且用户说"保存"/"提交"等关键词时，
    // 通过事件总线触发表单的 handleSubmit，直接调用 createScene REST API 提交表单最新数据，
    // 不发送到后端（避免后端 pendingAction 中的初始预览数据覆盖前端表单修改）
    if (SCENE_SAVE_KEYWORDS.has(text)) {
      const lastAiMsg = messages[messages.length - 1];
      if (lastAiMsg && lastAiMsg.role === 'assistant'
          && lastAiMsg.routePath === '/scene/create') {
        // 派发事件，SceneCreateResultPage 监听后触发表单提交
        window.dispatchEvent(new CustomEvent(SCENE_SUBMIT_EVENT));
        // 仍追加用户消息到便利贴（让用户看到自己的"保存"指令）
        const userMsg: ChatMessage = {
          id: `${USER_MSG_ID_PREFIX}${Date.now()}`,
          role: 'user',
          content: text,
        };
        setMessages(prev => [...prev, userMsg]);
        setInput('');
        return;
      }
    }

    // 登录态检查：未登录则缓存待发消息并弹出登录框
    // 使用 ref 读取最新登录态，避免登录成功后重发时闭包过期
    if (!sessionStatusRef.current?.loggedIn) {
      setPendingMessage(text);
      setLoginOpen(true);
      return;
    }

    // 房屋检查：未选房屋则缓存待发消息并弹出选房框
    if (!sessionStatusRef.current?.currentHomeId) {
      setPendingMessage(text);
      setHomeSelectOpen(true);
      return;
    }

    // ===== v4.4.13：注释掉自动清空便利贴逻辑 =====
    // 原逻辑：发送新消息前，如果已有 >= 3 轮对话，归档旧便利贴再开始新对话
    // 用户要求：不要自动清空屏幕，保留所有便利贴，由用户手动点击清空按钮
    // if (conversationRoundCount.current >= 3) {
    //   conversationRoundCount.current = 0;
    //   setMessages(prev => {
    //     if (prev.length > 0) {
    //       // 将当前便利贴归档到历史（限制历史最多 30 条，防止内存无限增长）
    //       setHistoryMessages(hist => [...hist, ...prev].slice(-30));
    //     }
    //     return [];
    //   });
    // }

    // 缓存当前发送的文本到 ref（onNeedSelectHome 闭包中使用，避免 messages 过期导致 pendingMessage 丢失）
    pendingInputRef.current = text;

    // 快照本次发送携带的图片（多模态）：在清空 pendingImages 之前捕获，
    // 用于追加到用户消息（在便利贴中展示用户上传的图片）和透传给 send()
    const imagesToSend: ImageInfo[] = pendingImages.map(item => item.info);

    // 1. 追加用户消息（携带图片，便于在用户便利贴中回显已上传的图片）
    const userMsg: ChatMessage = {
      id: `${USER_MSG_ID_PREFIX}${Date.now()}`,
      role: 'user',
      content: text,
      images: imagesToSend.length > 0 ? imagesToSend : undefined,
    };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    // 发送后立即清空待发送图片列表（释放 object URL 避免内存泄漏）
    clearPendingImages();

    // 2. 预占位 AI 消息（streaming）
    const aiMsgId = `${AI_MSG_ID_PREFIX}${Date.now()}`;
    // 重置多意图 result 计数器（v3.6.0 多意图支持：本次发送收到的首个 result 更新现有消息，后续 result 创建新便利贴）
    resultCountRef.current = 0;
    resultReceivedRef.current = false;
    streamingMsgIdRef.current = aiMsgId;
    setMessages(prev => [...prev, {
      id: aiMsgId,
      role: 'assistant',
      content: '',
      streaming: true,
    }]);

    // 3. 收集历史（最近 N 条）
    const history = messages
      .slice(-MAX_HISTORY_MESSAGES)
      .filter(m => m.content)
      .map(m => ({ role: m.role, content: m.content }));

    // 4. 发起 SSE（多模态：文字 + 图片 + 房屋 ID 一起发送给后端 /chat/send 接口）
    try {
      await send(text, history, imagesToSend.length > 0 ? imagesToSend : undefined, sessionStatusRef.current?.currentHomeId);
    } catch (e: unknown) {
      const errMsg = e instanceof Error ? e.message : String(e);
      updateMessage(aiMsgId, m => ({
        ...m,
        content: `请求失败：${errMsg}`,
        streaming: false,
        error: true,
      }));
    } finally {
      streamingMsgIdRef.current = null;
    }
  }, [input, sending, messages, send, updateMessage, pendingImages, clearPendingImages]);

  // ===== 同步 handleSend 到 ref（供 useVoiceAssistant 间接调用，避免闭包过期） =====
  useEffect(() => {
    handleSendRef.current = (text: string) => { handleSend(text); };
  }, [handleSend]);

  // ===== 登录成功回调：关闭弹窗 + 刷新状态 + 重发待发消息（参照 hdl-agent） =====
  const handleLoginSuccess = async (_loginName: string) => {
    setLoginOpen(false);
    setLoginRequired(false);
    await refreshStatus();  // 内置：拉取状态 + 选房弹窗 + 加载历史对话
    // 登录成功后有待发消息则自动重发
    if (pendingMessage) {
      const msg = pendingMessage;
      setPendingMessage('');
      resendTimerRef.current = setTimeout(() => handleSend(msg), RESEND_DELAY_MS);
    }
  };

  // ===== 房屋选择成功回调：关闭弹窗 + 刷新状态 + 无感重发待发消息 =====
  const handleHomeSelectSuccess = async (_homeName: string) => {
    setHomeSelectOpen(false);
    await refreshStatus();
    // 无感重发：用户选房屋后自动继续原问题（pendingMessage 由 onNeedSelectHome 通过 ref 缓存）
    if (pendingMessage) {
      const msg = pendingMessage;
      setPendingMessage('');
      pendingInputRef.current = '';  // 清除 ref，避免重复触发
      resendTimerRef.current = setTimeout(() => handleSend(msg), RESEND_DELAY_MS);
    }
  };

  // ===== 登出（参照 hdl-agent：调后端 API + 清除本地状态） =====
  const handleLogout = async () => {
    try {
      await apiLogout();
    } catch (e) {
      console.warn('[logout] 登出失败', e);
    }
    setSessionToken(null);
    setSessionStatus(null);
    setMessages([]);
  };

  // ===== 删除单条消息（便利贴面板右上角删除按钮） =====
  const removeMessage = useCallback((id: string) => {
    setMessages(prev => prev.filter(m => m.id !== id));
  }, []);

  // ===== 清空所有当前便利贴（归档到历史，保留历史便利贴） =====
  const handleClearAllNotes = useCallback(() => {
    setMessages(prev => {
      if (prev.length > 0) {
        // 将当前便利贴归档到历史（限制历史最多 50 条，防止内存无限增长）
        setHistoryMessages(hist => [...hist, ...prev].slice(-30));
      }
      return [];
    });
  }, []);

  // ===== 渲染 =====
  return (
    <div className="relative flex h-screen w-screen overflow-hidden text-slate-100">
      {/* ===== 全局粒子背景 ===== */}
      <ParticleField />

      {/* ===== 主区（全屏布局，无侧边栏） ===== */}
      <main className="relative flex-1 flex flex-col min-w-0">
        {/* 右上角设置入口（齿轮图标） */}
        <div className="absolute top-4 right-4 z-20">
          {/* 系统设置入口（含可观测性面板入口） */}
          <button
            onClick={() => setSettingsOpen(true)}
            title="系统设置"
            className="flex items-center gap-1.5 rounded-full glass-panel px-3 py-2 text-sm text-slate-300 border border-neon-purple/30 hover-neon transition-all"
          >
            <Settings size={14} className="text-neon-purple" />
          </button>
        </div>

        {/* 消息区域：中央 3D 模型 + 周边便利贴 */}
        <div className="flex-1 relative overflow-hidden">
          {/* 中央 3D 模型区：设备控制时显示 3D 户型图，其他时候显示 3D 女孩 */}
          <div className="absolute inset-0 flex items-center justify-center">
            {showHomePlan ? (
              <HomePlan3D deviceEvent={deviceEvent} onClose={() => setShowHomePlan(false)} />
            ) : (
              <AiModel3D
                state={voice.enabled
                  ? (voice.state === 'speaking' ? 'speaking'
                    : voice.state === 'commanding' ? 'thinking'
                    : voice.state === 'listening' ? 'listening'
                    : 'idle')
                  : 'idle'}
              />
            )}
          </div>

          {/* 便利贴消息（环绕 3D 模型，不遮挡中央） */}
          <StickyNotesContainer
            messages={messages}
            historyMessages={historyMessages}
            onRemoveMessage={removeMessage}
            onClearAll={handleClearAllNotes}
          />

          <div ref={messagesEndRef} />
        </div>

        {/* 输入框区域 */}
        <div className="px-4 md:px-8 py-4">
          <div className="max-w-3xl mx-auto">
            {/* HITL 权限确认弹框（玻璃拟态风格，输入框上方） */}
            {permissionDialog && (
              <div
                className="mb-3 rounded-2xl overflow-hidden transition-all duration-300"
                style={{
                  background: 'rgba(15, 23, 42, 0.9)',
                  backdropFilter: 'blur(20px)',
                  border: '1px solid rgba(0, 212, 255, 0.2)',
                  boxShadow: '0 0 40px rgba(0, 212, 255, 0.1), 0 10px 30px rgba(0, 0, 0, 0.3)',
                  opacity: dialogVisible ? 1 : 0,
                  transform: `translateY(${dialogVisible ? 0 : -8}px)`,
                }}
              >
                {/* 霓虹扫描线 */}
                <div className="h-1 bg-gradient-to-r from-cyan-500 via-purple-500 to-cyan-500 opacity-70" />
                <div className="p-3.5">
                  <div className="flex items-center gap-2.5 mb-2.5">
                    <div
                      className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
                      style={{
                        background: 'linear-gradient(135deg, rgba(0, 212, 255, 0.2), rgba(123, 47, 190, 0.2))',
                        border: '1px solid rgba(0, 212, 255, 0.3)',
                      }}
                    >
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#00d4ff" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-semibold text-slate-200">操作确认</p>
                      <p className="text-[11px] text-slate-400 mt-0.5">{permissionDialog.message}</p>
                    </div>
                  </div>
                  {/* 工具列表 */}
                  <div className="flex flex-wrap gap-1.5 mb-3">
                    {permissionDialog.toolCalls.map((tc, i) => (
                      <span
                        key={tc.toolCallId || i}
                        className="text-[10px] px-2 py-0.5 rounded-full font-mono"
                        style={{
                          background: 'rgba(0, 212, 255, 0.1)',
                          border: '1px solid rgba(0, 212, 255, 0.2)',
                          color: '#00d4ff',
                        }}
                      >
                        {tc.toolName === 'batch_control_device' ? '设备控制' :
                         tc.toolName === 'query_device_list' ? '查询设备' :
                         tc.toolName === 'execute_scene' ? '执行场景' :
                         tc.toolName === 'create_scene' ? '创建场景' : tc.toolName}
                      </span>
                    ))}
                  </div>
                  {/* 按钮 */}
                  <div className="flex gap-2">
                    <button onClick={handlePermissionConfirm} disabled={confirmPending}
                      className="flex-1 flex items-center justify-center gap-1 py-2 rounded-xl text-xs font-semibold transition-all disabled:opacity-50"
                      style={{ background: 'linear-gradient(135deg, rgba(0,212,255,0.2), rgba(0,180,216,0.15))', border: '1px solid rgba(0,212,255,0.35)', color: '#00d4ff' }}>
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                      确认
                    </button>
                    <button onClick={handlePermissionCancel} disabled={confirmPending}
                      className="flex-1 flex items-center justify-center gap-1 py-2 rounded-xl text-xs font-semibold transition-all disabled:opacity-50"
                      style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', color: '#f87171' }}>
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                      取消
                    </button>
                  </div>
                </div>
              </div>
            )}
            {/* 麦克风权限错误提示（not-allowed 时显示醒目红色框） */}
            {voice.errorType === 'not-allowed' && (
              <div className="mb-2 rounded-lg bg-red-900/40 border border-red-500/60 px-3 py-2">
                <div className="flex items-start gap-2">
                  <span className="text-red-400 text-sm mt-0.5">⚠</span>
                  <div className="flex-1">
                    <p className="text-red-300 text-xs font-semibold mb-1">麦克风权限被拒绝</p>
                    <p className="text-red-200/80 text-[11px] leading-relaxed whitespace-pre-line">{voice.error}</p>
                    <div className="flex gap-2 mt-2">
                      <button
                        onClick={() => window.location.reload()}
                        className="rounded-md bg-red-600 hover:bg-red-500 text-white text-xs px-3 py-1 transition-colors"
                      >
                        刷新页面重试
                      </button>
                      <button
                        onClick={() => {
                          // 打开 Chrome 麦克风权限设置页
                          window.open('chrome://settings/content/microphone', '_blank');
                        }}
                        className="rounded-md bg-slate-700 hover:bg-slate-600 text-white text-xs px-3 py-1 transition-colors"
                      >
                        打开 Chrome 麦克风设置
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}
            {/* 语音助手状态条（启用时显示） */}
            {voice.enabled && (
              <div className="mb-1.5 flex items-center justify-between gap-2 rounded-lg bg-slate-900/60 border border-neon-cyan/20 px-3 py-1.5">
                <div className="flex items-center gap-2 text-xs">
                  {/* 状态指示点（不同状态不同颜色） */}
                  <span
                    className={`inline-block h-2 w-2 rounded-full ${
                      voice.state === 'listening' ? 'bg-neon-cyan animate-pulse'
                        : voice.state === 'awake' ? 'bg-yellow-400 animate-pulse'
                        : voice.state === 'commanding' ? 'bg-neon-purple animate-pulse'
                        : voice.state === 'speaking' ? 'bg-green-400 animate-pulse'
                        : 'bg-slate-500'
                    }`}
                  />
                  <span className="text-slate-300 font-mono">
                    {voice.state === 'listening' && '监听唤醒词"你好小东"中...'}
                    {voice.state === 'awake' && '已唤醒，请说出指令'}
                    {voice.state === 'commanding' && (voice.transcript || '正在识别指令...')}
                    {voice.state === 'speaking' && 'AI 朗读中...'}
                    {voice.state === 'idle' && '语音助手已暂停'}
                  </span>
                  {voice.error && voice.errorType !== 'not-allowed' && (
                    <span className="text-red-400 ml-2">⚠ {voice.error}</span>
                  )}
                </div>
                <div className="flex items-center gap-1">
                  {/* 手动点击麦克风说话（无需唤醒词） */}
                  <button
                    onClick={() => voice.startCommand()}
                    disabled={voice.state === 'commanding' || sending}
                    className="rounded-md px-2 py-1 text-[11px] text-neon-cyan border border-neon-cyan/30 hover:bg-neon-cyan/10 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                    title="点击说话（手动触发一次命令识别）"
                  >
                    <Mic size={12} className="inline mr-1" />
                    说话
                  </button>
                  {/* 停止朗读 */}
                  <button
                    onClick={() => voice.stopSpeaking()}
                    disabled={voice.state !== 'speaking'}
                    className="rounded-md px-2 py-1 text-[11px] text-slate-300 border border-slate-600/40 hover:bg-slate-700/40 transition-all disabled:opacity-30 disabled:cursor-not-allowed"
                    title="停止朗读"
                  >
                    <VolumeX size={12} className="inline" />
                  </button>
                </div>
              </div>
            )}
            <div className="rounded-2xl glass-strong input-focus px-3 py-2 transition-all focus-within:shadow-neon-purple focus-within:border-neon-purple/60">
              {/* 待发送图片预览条（多模态）：用户上传图片后在输入框上方展示缩略图，
                  支持点击 × 删除单张。图片不一定是户型图，由后端视觉 Agent 识别意图。 */}
              {pendingImages.length > 0 && (
                <div className="flex flex-wrap gap-2 mb-2 px-1 pt-1">
                  {pendingImages.map(item => (
                    <div
                      key={item.id}
                      className="relative group rounded-lg overflow-hidden border border-cyan-500/30 shadow-lg"
                      style={{ width: 64, height: 64 }}
                    >
                      <img
                        src={item.previewUrl}
                        alt={item.info.fileName}
                        className="w-full h-full object-cover"
                      />
                      {/* 删除按钮（悬浮显示） */}
                      <button
                        onClick={() => removePendingImage(item.id)}
                        className="absolute top-0.5 right-0.5 w-5 h-5 rounded-full bg-black/70 hover:bg-red-600/80 text-white flex items-center justify-center transition opacity-0 group-hover:opacity-100"
                        title="移除图片"
                      >
                        <X size={12} />
                      </button>
                      {/* 文件名提示（悬浮显示） */}
                      <div className="absolute bottom-0 left-0 right-0 bg-black/60 text-white text-[9px] px-1 py-0.5 truncate opacity-0 group-hover:opacity-100 transition">
                        {item.info.fileName}
                      </div>
                    </div>
                  ))}
                </div>
              )}
              <div className="flex items-end gap-2">
                {/* 麦克风开关按钮：开启/关闭语音助手（持续监听唤醒词） */}
                <button
                  onClick={() => voice.toggle()}
                  disabled={!voice.supported}
                  className={`shrink-0 rounded-xl p-2 transition-all ${
                    !voice.supported ? 'opacity-30 cursor-not-allowed'
                      : voice.enabled ? 'bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/50 animate-glow'
                      : 'text-slate-400 border border-slate-600/40 hover:text-neon-cyan hover:border-neon-cyan/40'
                  }`}
                  title={
                    !voice.supported ? '当前浏览器不支持语音识别（请使用 Chrome/Edge）'
                      : voice.enabled ? '关闭语音助手'
                      : '开启语音助手（唤醒词：你好小东）'
                  }
                >
                  {voice.enabled ? <Mic size={16} /> : <MicOff size={16} />}
                </button>
                {/* 图片上传按钮（多模态）：点击后选择图片，回显预览，加描述后统一通过 /chat/send 发送。
                    图片不一定是户型图，由后端视觉 Agent（qwen-vl-max）自主识别图片类型和用户意图后路由到对应子 Agent。 */}
                <button
                  onClick={() => imageInputRef.current?.click()}
                  disabled={sending}
                  className={`shrink-0 rounded-xl p-2 transition-all ${
                    sending ? 'opacity-60 cursor-not-allowed'
                      : pendingImages.length > 0
                        ? 'text-neon-cyan border border-neon-cyan/50 bg-neon-cyan/10'
                        : 'text-slate-400 border border-slate-600/40 hover:text-neon-cyan hover:border-neon-cyan/40'
                  }`}
                  title="上传图片（支持多选），输入描述后发送给 AI"
                >
                  <Paperclip size={16} />
                </button>
                {/* 隐藏的文件选择 input（由图片按钮触发，支持多选） */}
                <input
                  ref={imageInputRef}
                  type="file"
                  accept="image/png,image/jpeg,image/jpg,image/webp"
                  multiple
                  onChange={handleImageUpload}
                  className="hidden"
                />
                <textarea
                  ref={inputRef}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      handleSend();
                    }
                  }}
                  placeholder={
                    sending ? 'AI 正在处理...'
                      : pendingImages.length > 0 ? '请输入图片描述/需求，Enter 发送（如：根据这张户型图帮我挑选客厅、厨房、卧室的智能设备，总价不高于10万）'
                      : voice.enabled ? (voice.state === 'listening' ? '说"你好小东"唤醒对话，或直接输入文字'
                        : voice.state === 'awake' ? '请说出指令...'
                        : '输入消息，Enter 发送，Shift+Enter 换行')
                      : '输入消息，Enter 发送，Shift+Enter 换行'
                  }
                  rows={1}
                  disabled={sending && !resultReceivedRef.current || confirmPending || permissionDialog !== null}
                  className="flex-1 resize-none bg-transparent text-sm text-slate-100 placeholder-slate-500 outline-none max-h-32 disabled:opacity-60"
                  style={{ minHeight: '24px' }}
                />
                {/* 中断/发送按钮：sending 时显示为中断按钮，收到 result 后可输入新消息 */}
                {sending && !resultReceivedRef.current ? (
                  <button
                    onClick={() => abortSseRef.current()}
                    className="shrink-0 rounded-xl bg-gradient-to-br from-red-500 to-red-600 p-2 text-white transition-all shadow-lg shadow-red-500/20 hover:shadow-red-500/40"
                    title="中断当前操作"
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>
                  </button>
                ) : (
                  <button
                    onClick={() => handleSend()}
                    disabled={confirmPending || permissionDialog !== null || !input.trim()}
                    className={`shimmer-btn rounded-xl bg-gradient-to-br from-neon-purple to-neon-purple p-2 text-white transition-all disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0 ${
                      input.trim() ? 'shadow-neon-purple animate-glow hover:shadow-glow-md' : ''
                    }`}
                    title="发送"
                  >
                    <Send size={16} />
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* 登录弹窗（v4.4.3：loginRequired=true 时强制登录，隐藏关闭按钮） */}
      <LoginModal
        open={loginOpen}
        onClose={() => setLoginOpen(false)}
        onSuccess={handleLoginSuccess}
        pendingMessage={pendingMessage}
        required={loginRequired}
      />

      {/* 房屋选择弹窗（未选房时不可关闭） */}
      <HomeSelectModal
        open={homeSelectOpen}
        homes={homeList}
        onClose={() => setHomeSelectOpen(false)}
        onSelect={handleHomeSelectSuccess}
        homeQueryFailed={sessionStatus?.homeQueryFailed}
        required={!sessionStatus?.currentHomeId}
      />

      {/* Agent YML 编辑器弹窗（编辑 Nacos YML 配置，保存后热重载 HarnessAgent） */}
      <AgentYmlEditor open={ymlEditorOpen} onClose={() => setYmlEditorOpen(false)} />

      {/* 设置弹窗（账户/房屋/YML/LLM 配置） */}
      <SettingsModal
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        sessionStatus={sessionStatus}
        onLogout={handleLogout}
        onOpenHomeSelect={() => {
          const homes = homeList.length > 0 ? homeList : (sessionStatus?.homes || []);
          setHomeList(homes);
          setHomeSelectOpen(true);
        }}
        onOpenYmlEditor={() => setYmlEditorOpen(true)}
        onOpenLogin={() => setLoginOpen(true)}
        onOpenObserve={() => {
          // 关闭设置弹窗，打开可观测性浮动面板
          setSettingsOpen(false);
          setObserveOpen(true);
        }}
        onOpenRagManager={() => {
          // v4.4.3：RAG 知识库管理改为新开浏览器窗口（独立全屏页面，更大操作空间）
          // 关闭设置弹窗，打开 /rag 路径的新窗口
          setSettingsOpen(false);
          // 构建 RAG 页面 URL（保留当前 base 路径，兼容 K8s 子路径部署）
          const ragUrl = `${window.location.origin}${window.location.pathname.replace(/\/[^/]*$/, '')}/rag`;
          window.open(ragUrl, '_blank', 'noopener,noreferrer');
        }}
      />

      {/* 可观测性浮动面板（拖拽缩放，SSE 实时事件流 + ECharts 指标可视化） */}
      {observeOpen && <ObservabilityPanel onClose={() => setObserveOpen(false)} />}
    </div>
  );
}

