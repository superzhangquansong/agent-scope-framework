/**
 * 可拖拽 + 可缩放浮动面板容器组件（含历史便利贴）
 *
 * <p>将 AI 回复以浮动面板形式展示在 3D 女孩模型周边，不遮挡中央模型。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>每个面板展示完整的消息内容 + 结构化结果界面（DynamicPage）</li>
 *   <li>面板可手动拖拽，自由布局</li>
 *   <li>面板可拉大缩小（右下角缩放手柄）</li>
 *   <li>面板可收起/展开（收起时仅显示标题栏）</li>
 *   <li>右上角删除按钮：真正删除该面板（从消息列表移除）</li>
 *   <li>标题栏显示中文标题（路由路径 → 中文映射）</li>
 *   <li>最多显示 6 个面板，超出时自动收起最老的</li>
 *   <li>右上角固定收起的历史便利贴入口（可展开查看所有历史对话）</li>
 *   <li>全屏清除入口：一键清空当前便利贴（保留历史便利贴）</li>
 * </ul>
 *
 * @author zqs
 * @since 3.2.0
 */
import { useState, useRef, useCallback, useEffect, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  ChevronDown, ChevronUp, ChevronRight, User, Bot, X, GripHorizontal,
  History, Trash2,
  Loader2, Brain, Wrench, Cpu, CheckCircle2, Play,
} from 'lucide-react';
import DynamicPage, { ROUTE_PATH } from './DynamicPage';
import type { ChatMessage } from '../App';
import type { AgentStepData } from '../api/client';

/** 当前显示的最大面板数量（超出时最老的自动归入历史，降低以节省内存） */
const MAX_PANELS = 4;

/** 用户消息面板默认宽度（px，小尺寸） */
const USER_PANEL_WIDTH = 300;
/** AI 回复面板默认宽度（px，大尺寸） */
const AI_PANEL_WIDTH = 540;
/** AI 回复面板默认高度（px，大尺寸） */
const AI_PANEL_HEIGHT = 480;
/** AI 回复面板最小宽度（px，缩放下限） */
const AI_MIN_PANEL_WIDTH = 380;
/** AI 回复面板最小高度（px，缩放下限） */
const AI_MIN_PANEL_HEIGHT = 280;

/**
 * Markdown 渲染组件样式映射（科幻深色主题）。
 *
 * <p>适配深色背景：文字浅色、表格边框霓虹紫、代码块深色背景、标题霓虹青。</p>
 * <p>字号与 panel-text（13px）保持一致。</p>
 */
const MARKDOWN_COMPONENTS = {
  h1: ({ children }: { children?: React.ReactNode }) => (
    <h1 className="text-base font-bold text-neon-cyan mt-3 mb-2 leading-snug">{children}</h1>
  ),
  h2: ({ children }: { children?: React.ReactNode }) => (
    <h2 className="text-sm font-bold text-neon-cyan mt-3 mb-2 leading-snug">{children}</h2>
  ),
  h3: ({ children }: { children?: React.ReactNode }) => (
    <h3 className="text-[13px] font-semibold text-neon-purple mt-2 mb-1.5 leading-snug">{children}</h3>
  ),
  h4: ({ children }: { children?: React.ReactNode }) => (
    <h4 className="text-[13px] font-semibold text-neon-purple mt-2 mb-1 leading-snug">{children}</h4>
  ),
  h5: ({ children }: { children?: React.ReactNode }) => (
    <h5 className="text-[13px] font-medium text-slate-200 mt-2 mb-1 leading-snug">{children}</h5>
  ),
  h6: ({ children }: { children?: React.ReactNode }) => (
    <h6 className="text-[13px] font-medium text-slate-300 mt-2 mb-1 leading-snug">{children}</h6>
  ),
  p: ({ children }: { children?: React.ReactNode }) => (
    <p className="text-[13px] text-slate-200 leading-relaxed my-1.5">{children}</p>
  ),
  ul: ({ children }: { children?: React.ReactNode }) => (
    <ul className="text-[13px] text-slate-200 leading-relaxed my-1.5 pl-5 list-disc space-y-0.5">{children}</ul>
  ),
  ol: ({ children }: { children?: React.ReactNode }) => (
    <ol className="text-[13px] text-slate-200 leading-relaxed my-1.5 pl-5 list-decimal space-y-0.5">{children}</ol>
  ),
  li: ({ children }: { children?: React.ReactNode }) => (
    <li className="text-slate-200">{children}</li>
  ),
  table: ({ children }: { children?: React.ReactNode }) => (
    <div className="overflow-x-auto my-2">
      <table className="w-full text-[13px] border-collapse border border-neon-purple/40">{children}</table>
    </div>
  ),
  thead: ({ children }: { children?: React.ReactNode }) => (
    <thead className="bg-neon-purple/15">{children}</thead>
  ),
  th: ({ children }: { children?: React.ReactNode }) => (
    <th className="border border-neon-purple/40 px-2 py-1 text-left text-neon-cyan font-semibold">{children}</th>
  ),
  td: ({ children }: { children?: React.ReactNode }) => (
    <td className="border border-neon-purple/30 px-2 py-1 text-slate-200">{children}</td>
  ),
  code: ({ className, children }: { className?: string; children?: React.ReactNode }) => (
    <code className={`${className ?? ''} px-1 py-0.5 rounded bg-space-900/80 text-neon-green font-mono text-[12px]`}>{children}</code>
  ),
  pre: ({ children }: { children?: React.ReactNode }) => (
    <pre className="my-2 p-3 rounded-md bg-space-900/90 border border-neon-purple/20 overflow-x-auto [&_code]:bg-transparent [&_code]:p-0 [&_code]:border-0 [&_code]:text-neon-green">{children}</pre>
  ),
  blockquote: ({ children }: { children?: React.ReactNode }) => (
    <blockquote className="my-2 pl-3 border-l-2 border-neon-purple/50 bg-neon-purple/5 py-1 text-slate-300 text-[13px]">{children}</blockquote>
  ),
  a: ({ children, href }: { children?: React.ReactNode; href?: string }) => (
    <a href={href} target="_blank" rel="noopener noreferrer" className="text-neon-cyan underline hover:text-neon-blue transition-colors">{children}</a>
  ),
  strong: ({ children }: { children?: React.ReactNode }) => (
    <strong className="font-bold text-white">{children}</strong>
  ),
  em: ({ children }: { children?: React.ReactNode }) => (
    <em className="italic text-slate-100">{children}</em>
  ),
  hr: () => <hr className="my-3 border-neon-purple/30" />,
};

/** 面板位置 */
interface PanelPosition {
  x: number;
  y: number;
}

/** 面板尺寸 */
interface PanelSize {
  width: number;
  height: number;
}

/** 面板状态 */
interface PanelState {
  /** 是否已收起 */
  collapsed: boolean;
  /** 面板位置 */
  position: PanelPosition;
  /** 面板尺寸（支持拉大缩小） */
  size: PanelSize;
  /** z-index（点击时置顶） */
  zIndex: number;
}

/** StickyNotesContainer Props */
interface StickyNotesContainerProps {
  /** 当前消息列表（活跃便利贴） */
  messages: ChatMessage[];
  /** 历史消息列表（已归档的便利贴，不会被清空） */
  historyMessages?: ChatMessage[];
  /** 删除单条消息回调 */
  onRemoveMessage?: (id: string) => void;
  /** 清空所有当前便利贴回调（将当前消息归档到历史） */
  onClearAll?: () => void;
}

/**
 * 路由路径 → 中文标题映射表。
 *
 * <p>用于面板标题栏显示，避免出现 product-list 这样的英文路由。</p>
 */
const ROUTE_TITLE_MAP: Record<string, string> = {
  [ROUTE_PATH.PRODUCT_LIST]: '产品列表',
  [ROUTE_PATH.PRODUCT_DETAIL]: '产品详情',
  [ROUTE_PATH.DEVICE_DETAIL]: '设备详情',
  [ROUTE_PATH.DEVICE_STATUS]: '设备状态',
  [ROUTE_PATH.DEVICE_LIST]: '设备列表',
  [ROUTE_PATH.HOME_LIST]: '房屋列表',
  [ROUTE_PATH.SCENE_LIST]: '场景列表',
  [ROUTE_PATH.SCENE_DETAIL]: '场景详情',
  [ROUTE_PATH.SCENE_CREATE]: '场景创建',
  [ROUTE_PATH.SCENE_EXECUTE]: '场景执行',
  [ROUTE_PATH.SCENE_DELETE]: '场景删除',
  [ROUTE_PATH.SCENE_RECOMMEND]: '场景推荐',
  [ROUTE_PATH.CART_LIST]: '购物车',
  [ROUTE_PATH.CART_ADD]: '加入购物车',
  [ROUTE_PATH.CART_CHECKOUT]: '结算',
  [ROUTE_PATH.QA_LIST]: '百问百答',
  [ROUTE_PATH.ENERGY_STATION_LIST]: '储能电站',
  [ROUTE_PATH.ENERGY_STATION_DETAIL]: '电站详情',
  [ROUTE_PATH.ENERGY_BATTERY_REPORT]: '电池报告',
  [ROUTE_PATH.ENERGY_SAVINGS_REPORT]: '省钱分析',
  [ROUTE_PATH.ENERGY_INVERTER_INFO]: '逆变器实时数据',
  [ROUTE_PATH.ENERGY_FAULT_DIAGNOSIS]: '电站故障排查',
  [ROUTE_PATH.FLOOR_PLAN_RESULT]: '户型图方案',
  [ROUTE_PATH.GENERIC]: '查询结果',
  [ROUTE_PATH.EMPTY]: '结果',
  [ROUTE_PATH.ERROR]: '错误',
};

/**
 * 根据路由路径获取中文标题。
 *
 * @param routePath 路由路径（如 product-list）
 * @return 中文标题（如 "产品列表"），未匹配时返回 "AI 回复"
 */
function getPanelTitle(routePath?: string | null): string {
  if (!routePath) return 'AI 回复';
  // 归一化：去掉开头的 /，将 / 替换为 -
  const normalized = routePath.replace(/^\//, '').replace(/\//g, '-');
  return ROUTE_TITLE_MAP[normalized] || 'AI 回复';
}

/**
 * 计算面板的初始随机位置（避开屏幕中央 3D 模型区域）。
 *
 * <p>布局策略：面板分布在屏幕四个角区域，避开中央 50%×60% 的区域。</p>
 *
 * @param index 面板索引
 * @param screenWidth 屏幕宽度
 * @param screenHeight 屏幕高度
 * @param isUser 是否用户消息
 * @return 初始位置坐标
 */
function getRandomPosition(index: number, screenWidth: number, screenHeight: number, isUser: boolean): PanelPosition {
  const panelWidth = isUser ? USER_PANEL_WIDTH : AI_PANEL_WIDTH;
  const panelHeight = isUser ? 180 : AI_PANEL_HEIGHT;

  // 用户消息贴在左下角附近，AI 回复分布四周
  if (isUser) {
    return {
      x: 20,
      y: 80 + index * 30,
    };
  }

  // AI 回复分三区分布
  const zone = index % 3;
  const offset = Math.floor(index / 3);

  switch (zone) {
    case 0: // 左侧区域
      return {
        x: 20 + offset * 20,
        y: 120 + offset * (panelHeight + 20),
      };
    case 1: // 右侧区域
      return {
        x: screenWidth - panelWidth - 20 - offset * 20,
        y: 120 + offset * (panelHeight + 20),
      };
    case 2: // 底部区域
    default:
      return {
        x: (screenWidth - panelWidth) / 2 + (offset - 1) * (panelWidth + 20),
        y: screenHeight - panelHeight - 120 - offset * 10,
      };
  }
}

/**
 * Agent 思考过程面板（ReAct 推理步骤实时展示）。
 *
 * <p>在便利贴面板顶部展示 Agent 的思考和工具调用过程，
 * 让用户看到 AI 的推理路径，提升透明度和信任度。</p>
 *
 * <p>步骤类型：</p>
 * <ul>
 *   <li>agent_start：Agent 启动（Play 图标）</li>
 *   <li>agent_end：Agent 完成（CheckCircle2 图标）</li>
 *   <li>llm_call：LLM 推理调用（Brain 图标）</li>
 *   <li>tool_call：工具调用（Wrench 图标）</li>
 * </ul>
 *
 * @param steps 步骤列表
 * @param streaming 是否流式输出中（true 时显示加载动画）
 */
function AgentStepsPanel({ steps, streaming, isThinkingPlaceholder }: { steps: AgentStepData[]; streaming?: boolean; isThinkingPlaceholder?: boolean }) {
  const [expanded, setExpanded] = useState(streaming || isThinkingPlaceholder);

  /** 工具名 → 中文名映射 */
  const TOOL_NAME_CN: Record<string, string> = {
    query_device_list: '查询设备列表',
    query_device_detail: '查询设备详情',
    batch_control_device: '批量控制设备',
    search_product: '搜索产品',
    query_home_list: '查询房屋列表',
    create_scene: '创建场景',
    execute_scene: '执行场景',
    load_skill_through_path: '加载技能文档',
    http_request: 'HTTP 请求',
  };

  /** 获取工具中文名（无映射时返回原名） */
  const getToolCnName = (toolName?: string) => {
    if (!toolName) return 'unknown';
    return TOOL_NAME_CN[toolName] ?? toolName;
  };

  /** 根据步骤类型返回对应图标 */
  const renderStepIcon = (step: string, state?: string) => {
    const iconColor = state === 'FAILED' ? '#ff4d6d' : '#00ffff';
    // agent_start / agent_end
    if (step === 'agent_start') return <Play size={11} color={iconColor} />;
    if (step === 'agent_end') return <CheckCircle2 size={11} color={iconColor} />;
    // model_call_* → LLM 推理
    if (step.startsWith('model_call')) return <Brain size={11} color={iconColor} />;
    // tool_call_* → 工具调用
    if (step.startsWith('tool_call')) return <Wrench size={11} color={iconColor} />;
    return <Cpu size={11} color={iconColor} />;
  };

  /** 生成步骤展示文本 */
  const renderStepLabel = (step: AgentStepData): string => {
    const elapsed = step.elapsedMs != null ? ` · ${(step.elapsedMs / 1000).toFixed(1)}s` : '';
    const agent = step.agentId ? `[${step.agentId}] ` : '';
    // LLM 推理（model_call_start / model_call_end）
    if (step.step.startsWith('model_call')) {
      const tokens = step.tokenCount ? ` · ${step.tokenCount} tokens` : '';
      const phase = step.step === 'model_call_end' ? 'LLM 推理完成' : 'LLM 推理中';
      return `${agent}${phase}${step.modelName ? ` · ${step.modelName}` : ''}${tokens}${elapsed}`;
    }
    // 工具调用（tool_call_start / tool_call_end）
    if (step.step.startsWith('tool_call')) {
      const phase = step.step === 'tool_call_end' ? '调用完成' : '调用中';
      return `${agent}${getToolCnName(step.toolName)} · ${phase}${step.state ? ` · ${step.state}` : ''}${elapsed}`;
    }
    // Agent 生命周期
    if (step.step === 'agent_start') return `${agent}Agent 启动${elapsed}`;
    if (step.step === 'agent_end') return `${agent}Agent 完成${elapsed}`;
    return `${agent}${step.message ?? step.step}${elapsed}`;
  };

  return (
    <div className="agent-steps-panel">
      <div 
        className="agent-steps-header" 
        onClick={() => setExpanded(!expanded)}
        style={{ cursor: 'pointer', userSelect: 'none' }}
      >
        {expanded ? <ChevronDown size={14} color="#00ffff" /> : <ChevronRight size={14} color="#00ffff" />}
        {streaming && isThinkingPlaceholder ? (
          <Loader2 size={11} color="#00ffff" className="agent-steps-spinner" />
        ) : (
          <Brain size={11} color="#00ffff" />
        )}
        <span>{streaming && isThinkingPlaceholder ? '正在思考过程...' : '思考过程'}</span>
        <span className="agent-steps-count">{steps.length} 步</span>
      </div>
      {expanded && (
        <div className="agent-steps-list">
          {steps.map((s, idx) => (
            <div className="agent-step-item" key={idx}>
              {renderStepIcon(s.step, s.state)}
              <span className="agent-step-label">{renderStepLabel(s)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * 单个可拖拽 + 可缩放浮动面板
 *
 * @param message 消息内容
 * @param panelState 面板状态（位置/尺寸/收起/zIndex）
 * @param onChangeState 面板状态变更回调
 * @param onClose 关闭（删除）面板回调
 */
function DraggablePanel({
  message,
  panelState,
  onChangeState,
  onClose,
}: {
  message: ChatMessage;
  panelState: PanelState;
  onChangeState: (state: Partial<PanelState>) => void;
  onClose: () => void;
}) {
  /** 拖拽中标记 */
  const [dragging, setDragging] = useState(false);
  /** 缩放中标记 */
  const [resizing, setResizing] = useState(false);
  /** 拖拽起始位置（鼠标相对面板左上角偏移） */
  const dragOffset = useRef<PanelPosition>({ x: 0, y: 0 });
  /** 缩放起始尺寸 + 起始鼠标位置 */
  const resizeStart = useRef<{ width: number; height: number; mouseX: number; mouseY: number }>({
    width: 0, height: 0, mouseX: 0, mouseY: 0,
  });
  /** 面板 DOM 引用 */
  const panelRef = useRef<HTMLDivElement>(null);
  /** 全局 z-index 计数器 */
  const zIndexCounter = useRef(1);

  const isUser = message.role === 'user';
  // 中文标题（路由路径 → 中文映射）
  const panelTitle = isUser ? '我的消息' : getPanelTitle(message.routePath);
  // 是否有结构化结果（需要渲染 DynamicPage）
  // 知识库路由特殊处理：同时展示 LLM 回复（主答案）和结构化数据（下载链接）
  const isKnowledgeRoute = !isUser && message.routePath === '/qa/list';
  const hasStructuredResult = !isUser && message.routePath && message.data && Object.keys(message.data).length > 0 && !isKnowledgeRoute;
  // 归一化路由路径（用于 DynamicPage 组件分发）
  const normalizedPath = message.routePath ? message.routePath.replace(/^\//, '').replace(/\//g, '-') : '';

  /** 鼠标按下标题栏：开始拖拽 */
  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    // 点击关闭按钮或展开按钮时不触发拖拽
    const target = e.target as HTMLElement;
    if (target.closest('button')) return;
    // 记录鼠标相对面板的偏移
    const rect = panelRef.current?.getBoundingClientRect();
    if (rect) {
      dragOffset.current = {
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      };
    }
    setDragging(true);
    // 提升 z-index
    zIndexCounter.current += 1;
    onChangeState({ zIndex: 100 + zIndexCounter.current });
    e.preventDefault();
  }, [onChangeState]);

  /** 鼠标移动：拖拽中 */
  const handleMouseMove = useCallback((e: MouseEvent) => {
    if (!dragging) return;
    const newX = e.clientX - dragOffset.current.x;
    const newY = e.clientY - dragOffset.current.y;
    // 限制在视口范围内（留 10px 边距）
    const maxX = window.innerWidth - 100;
    const maxY = window.innerHeight - 60;
    onChangeState({
      position: {
        x: Math.max(0, Math.min(newX, maxX)),
        y: Math.max(0, Math.min(newY, maxY)),
      },
    });
  }, [dragging, onChangeState]);

  /** 鼠标释放：结束拖拽 */
  const handleMouseUp = useCallback(() => {
    setDragging(false);
    setResizing(false);
  }, []);

  /** 缩放手柄鼠标按下：开始缩放（仅 AI 回复面板支持） */
  const handleResizeMouseDown = useCallback((e: React.MouseEvent) => {
    if (isUser) return; // 用户消息面板不支持缩放
    e.preventDefault();
    e.stopPropagation();
    resizeStart.current = {
      width: panelState.size.width,
      height: panelState.size.height,
      mouseX: e.clientX,
      mouseY: e.clientY,
    };
    setResizing(true);
    // 提升 z-index
    zIndexCounter.current += 1;
    onChangeState({ zIndex: 100 + zIndexCounter.current });
  }, [isUser, panelState.size, onChangeState]);

  /** 缩放中鼠标移动 */
  const handleResizeMouseMove = useCallback((e: MouseEvent) => {
    if (!resizing) return;
    const dx = e.clientX - resizeStart.current.mouseX;
    const dy = e.clientY - resizeStart.current.mouseY;
    const newWidth = Math.max(AI_MIN_PANEL_WIDTH, resizeStart.current.width + dx);
    const newHeight = Math.max(AI_MIN_PANEL_HEIGHT, resizeStart.current.height + dy);
    // 限制最大尺寸不超过视口
    const maxW = window.innerWidth - panelState.position.x - 10;
    const maxH = window.innerHeight - panelState.position.y - 10;
    onChangeState({
      size: {
        width: Math.min(newWidth, maxW),
        height: Math.min(newHeight, maxH),
      },
    });
  }, [resizing, panelState.position, onChangeState]);

  // 拖拽 + 缩放事件绑定到 document（鼠标移出面板也能继续操作）
  useEffect(() => {
    if (dragging || resizing) {
      const moveHandler = dragging ? handleMouseMove : handleResizeMouseMove;
      document.addEventListener('mousemove', moveHandler);
      document.addEventListener('mouseup', handleMouseUp);
      return () => {
        document.removeEventListener('mousemove', moveHandler);
        document.removeEventListener('mouseup', handleMouseUp);
      };
    }
  }, [dragging, resizing, handleMouseMove, handleResizeMouseMove, handleMouseUp]);

  /** 切换收起/展开 */
  const toggleCollapse = useCallback(() => {
    onChangeState({ collapsed: !panelState.collapsed });
  }, [panelState.collapsed, onChangeState]);

  // 动效类型：用户消息从左滑入，AI 回复从右滑入，错误从上弹下
  const animClass = message.error
    ? 'panel-anim-drop'
    : isUser
      ? 'panel-anim-left'
      : 'panel-anim-right';

  return (
    <div
      ref={panelRef}
      className={`floating-panel ${animClass} ${dragging ? 'dragging' : ''} ${resizing ? 'resizing' : ''} ${panelState.collapsed ? 'collapsed' : 'expanded'} ${
        isUser ? 'panel-user' : 'panel-assistant'
      } ${message.error ? 'panel-error' : ''}`}
      style={{
        left: `${panelState.position.x}px`,
        top: `${panelState.position.y}px`,
        width: isUser ? `${USER_PANEL_WIDTH}px` : `${panelState.size.width}px`,
        height: isUser
          ? (panelState.collapsed ? 'auto' : 'auto')
          : (panelState.collapsed ? 'auto' : `${panelState.size.height}px`),
        zIndex: panelState.zIndex,
      }}
    >
      {/* 标题栏（拖拽手柄） */}
      <div
        className="panel-header"
        onMouseDown={handleMouseDown}
        style={{ cursor: dragging ? 'grabbing' : 'grab' }}
      >
        {/* 角色图标 */}
        <div className="flex items-center gap-1.5 flex-1 min-w-0">
          {isUser ? (
            <User size={12} className="text-blue-400 flex-shrink-0" />
          ) : (
            <Bot size={12} className="text-neon-purple flex-shrink-0" />
          )}
          <span className="text-xs font-medium text-slate-200 truncate">
            {panelTitle}
          </span>
        </div>
        {/* 拖拽手柄图标 */}
        <GripHorizontal size={13} className="text-slate-500 flex-shrink-0" />
        {/* 收起/展开按钮 */}
        <button
          onClick={toggleCollapse}
          className="text-slate-400 hover:text-neon-cyan transition-colors flex-shrink-0"
          title={panelState.collapsed ? '展开' : '收起'}
        >
          {panelState.collapsed ? <ChevronDown size={14} /> : <ChevronUp size={14} />}
        </button>
        {/* 删除按钮：真正删除该面板 */}
        <button
          onClick={onClose}
          className="text-slate-400 hover:text-red-400 transition-colors flex-shrink-0"
          title="删除"
        >
          <X size={14} />
        </button>
      </div>

      {/* 内容区（收起时隐藏） */}
      {!panelState.collapsed && (
        <div className="panel-body">
          {/* ===== Agent 思考过程（ReAct 推理步骤实时展示）===== */}
          {!isUser && message.agentSteps && message.agentSteps.length > 0 && (
            <AgentStepsPanel 
              steps={message.agentSteps} 
              streaming={message.streaming} 
              isThinkingPlaceholder={message.content === '正在思考...'} 
            />
          )}
          {/* 文字内容（有结构化界面时不展示 LLM 回复文字，避免与 DynamicPage 内容重复；知识库路由除外，需同时展示 LLM 回复和下载链接） */}
          {/* 知识库路由：LLM 回复用 Markdown 渲染（支持 GFM 表格/列表/代码块等） */}
          {isKnowledgeRoute && message.content && message.content !== '正在思考...' && (
            <div className={`panel-text markdown-body ${message.error ? 'text-red-300' : 'text-slate-100'}`}>
              <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS}>
                {message.content}
              </ReactMarkdown>
            </div>
          )}
          {/* 其他路由：无结构化界面时展示 LLM 回复文字（流式 Markdown 渲染） */}
          {!hasStructuredResult && !isKnowledgeRoute && message.content && message.content !== '正在思考...' && (
            <div className={`panel-text markdown-body ${message.error ? 'text-red-300' : 'text-slate-100'}`}>
              <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS}>
                {message.content}
              </ReactMarkdown>
            </div>
          )}
          {/* 用户消息携带的图片（多模态）：在文字下方以缩略图网格展示用户上传的图片 */}
          {isUser && message.images && message.images.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-2">
              {message.images.map((img, idx) => (
                <img
                  key={`user-img-${idx}`}
                  src={`data:${img.mimeType || 'image/png'};base64,${img.base64}`}
                  alt={img.fileName || `图片${idx + 1}`}
                  className="rounded-md border border-slate-600/50 object-cover"
                  style={{ width: 72, height: 72 }}
                />
              ))}
            </div>
          )}
          {/* 结构化结果界面（完整 DynamicPage） */}
          {(hasStructuredResult || isKnowledgeRoute) && message.data && (
            <div className="panel-dynamic-page">
              <DynamicPage path={normalizedPath} data={message.data!} />
            </div>
          )}
          {/* 流式输出指示器 */}
          {message.streaming && (
            <div className="panel-streaming">
              <span className="typing-cursor">▊</span>
            </div>
          )}
        </div>
      )}

      {/* 右下角缩放手柄（仅 AI 回复面板，收起时隐藏） */}
      {!isUser && !panelState.collapsed && (
        <div
          className="panel-resize-handle"
          onMouseDown={handleResizeMouseDown}
          title="拖拽调整大小"
        />
      )}
    </div>
  );
}

/**
 * 历史便利贴面板（右上角固定收起，点击展开查看所有历史对话）。
 *
 * <p>特点：</p>
 * <ul>
 *   <li>默认收起为一个小图标按钮（右上角固定位置）</li>
 *   <li>点击展开为可滚动的列表面板，显示所有历史消息</li>
 *   <li>历史消息不会被"清空对话"操作清除</li>
 * </ul>
 *
 * <p>简化版：移除拖拽功能，确保点击 100% 可靠。</p>
 */
function HistoryPanel({ historyMessages }: { historyMessages: ChatMessage[] }) {
  /** 历史面板是否展开 */
  const [expanded, setExpanded] = useState(false);

  /** 简单的点击切换展开/收起 */
  const handleToggle = useCallback(() => {
    setExpanded(prev => !prev);
  }, []);

  // 收起状态：右上角固定小按钮（z-index 极高，确保可点击）
  if (!expanded) {
    return (
      <button
        onClick={handleToggle}
        title="历史对话"
        className="fixed top-3 right-14 z-[9999] flex items-center gap-1.5 rounded-full glass-panel px-3 py-2 text-xs text-slate-300 border border-neon-cyan/30 hover:border-neon-cyan/60 transition-all cursor-pointer"
        style={{ pointerEvents: 'auto' }}
      >
        <History size={14} className="text-neon-cyan" />
        {historyMessages.length > 0 && (
          <span className="bg-neon-cyan/20 text-neon-cyan text-[10px] font-mono px-1.5 py-0.5 rounded-full">
            {historyMessages.length}
          </span>
        )}
      </button>
    );
  }

  // 展开状态：可滚动的历史列表面板
  return (
    <div
      className="fixed top-3 right-14 z-[9999] glass-strong rounded-xl border border-neon-cyan/30 shadow-glow-lg overflow-hidden flex flex-col"
      style={{
        width: '420px',
        maxHeight: '70vh',
        pointerEvents: 'auto',
      }}
    >
      {/* 标题栏 */}
      <div className="flex items-center gap-2 px-3 py-2 border-b border-white/10">
        <History size={14} className="text-neon-cyan flex-shrink-0" />
        <span className="text-xs font-medium text-slate-200 flex-1">历史对话</span>
        <span className="text-[10px] text-slate-500 font-mono">{historyMessages.length} 条</span>
        <button
          onClick={handleToggle}
          className="text-slate-400 hover:text-neon-cyan transition-colors cursor-pointer"
          title="收起"
        >
          <ChevronUp size={14} />
        </button>
      </div>

      {/* 历史消息列表（可滚动，支持 DynamicPage 结构化界面渲染） */}
      <div className="overflow-y-auto flex-1 p-2 space-y-2">
        {historyMessages.length === 0 ? (
          <div className="text-center text-xs text-slate-500 py-8">暂无历史对话</div>
        ) : (
          historyMessages.map(msg => {
            const isUser = msg.role === 'user';
            const title = isUser ? '我的消息' : getPanelTitle(msg.routePath);
            // 判断是否有结构化结果数据（用于渲染 DynamicPage 界面）
            const hasStructuredResult = !isUser && msg.routePath && msg.data && Object.keys(msg.data).length > 0;
            // 规范化路由路径：去掉 /hdl-agent 前缀，并将 / 替换为 - 与当前便利贴面板逻辑一致
            // 例如 /product/list → product-list，/scene/create → scene-create
            const normalizedPath = msg.routePath
              ? (msg.routePath.startsWith('/hdl-agent') ? msg.routePath.substring('/hdl-agent'.length) : msg.routePath)
                  .replace(/^\//, '').replace(/\//g, '-')
              : undefined;
            return (
              <div
                key={msg.id}
                className={`rounded-lg p-2 text-xs ${
                  isUser
                    ? 'bg-blue-900/20 border border-blue-500/20'
                    : 'bg-neon-purple/10 border border-neon-purple/20'
                }`}
              >
                <div className="flex items-center gap-1.5 mb-1">
                  {isUser ? (
                    <User size={10} className="text-blue-400" />
                  ) : (
                    <Bot size={10} className="text-neon-purple" />
                  )}
                  <span className="text-[10px] text-slate-400 font-medium">{title}</span>
                </div>
                {/* 文字内容（有结构化界面时不展示 LLM 回复文字，避免与 DynamicPage 内容重复） */}
                {(!hasStructuredResult || !normalizedPath) && (
                  <div className={`text-slate-300 leading-relaxed ${msg.error ? 'text-red-300' : ''}`}>
                    {msg.content || '(无文字内容)'}
                  </div>
                )}
                {/* v3.4.0 新增：结构化结果界面（DynamicPage），与当前便利贴面板展示一致 */}
                {hasStructuredResult && normalizedPath && (
                  <div className="mt-2 rounded-md overflow-hidden border border-white/10">
                    <DynamicPage path={normalizedPath} data={msg.data!} />
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

/**
 * 浮动面板容器主组件
 *
 * @param messages 当前消息列表（活跃便利贴）
 * @param historyMessages 历史消息列表（已归档的便利贴）
 * @param onRemoveMessage 删除消息回调（由父组件从 messages 中移除）
 * @param onClearAll 清空所有当前便利贴回调
 */
export default function StickyNotesContainer({
  messages,
  historyMessages = [],
  onRemoveMessage,
  onClearAll,
}: StickyNotesContainerProps) {
  /** 面板状态 Map（key = message.id） */
  const [panelStates, setPanelStates] = useState<Map<string, PanelState>>(new Map());

  // 只展示最近 MAX_PANELS 条消息（限制数量避免内存和性能问题）
  const visibleMessages = useMemo(() => {
    return messages.slice(-MAX_PANELS);
  }, [messages]);

  // 为新消息初始化面板状态
  useEffect(() => {
    const newStates = new Map(panelStates);
    let changed = false;
    visibleMessages.forEach((msg, index) => {
      if (!newStates.has(msg.id)) {
        const isUserMsg = msg.role === 'user';
        // 初始化随机位置（避开中央 3D 模型）+ 默认尺寸
        const pos = getRandomPosition(index, window.innerWidth, window.innerHeight, isUserMsg);
        newStates.set(msg.id, {
          collapsed: false,
          position: pos,
          size: {
            width: isUserMsg ? USER_PANEL_WIDTH : AI_PANEL_WIDTH,
            height: AI_PANEL_HEIGHT,
          },
          zIndex: 10 + index,
        });
        changed = true;
      }
    });
    // 移除已不在 visibleMessages 中的面板状态
    const visibleIds = new Set(visibleMessages.map(m => m.id));
    for (const key of newStates.keys()) {
      if (!visibleIds.has(key)) {
        newStates.delete(key);
        changed = true;
      }
    }
    // 超过 MAX_PANELS 时收起最老的（前 N-MAX 个）
    if (visibleMessages.length >= MAX_PANELS) {
      const oldest = visibleMessages[0];
      if (oldest && newStates.has(oldest.id)) {
        const state = newStates.get(oldest.id)!;
        if (!state.collapsed) {
          newStates.set(oldest.id, { ...state, collapsed: true });
          changed = true;
        }
      }
    }
    if (changed) setPanelStates(newStates);
  }, [visibleMessages]);

  /** 更新单个面板状态 */
  const handleChangeState = useCallback((id: string) => (partial: Partial<PanelState>) => {
    setPanelStates(prev => {
      const next = new Map(prev);
      const current = next.get(id);
      if (current) {
        next.set(id, { ...current, ...partial });
      }
      return next;
    });
  }, []);

  /** 删除面板：调用父组件移除消息 */
  const handleClose = useCallback((id: string) => {
    if (onRemoveMessage) {
      onRemoveMessage(id);
    } else {
      // 兜底：仅收起面板
      setPanelStates(prev => {
        const next = new Map(prev);
        const state = next.get(id);
        if (state) {
          next.set(id, { ...state, collapsed: true });
        }
        return next;
      });
    }
  }, [onRemoveMessage]);

  return (
    <div className="floating-panels-container">
      {/* ===== 当前活跃便利贴 ===== */}
      {visibleMessages.map(msg => {
        const state = panelStates.get(msg.id);
        if (!state) return null;
        return (
          <DraggablePanel
            key={msg.id}
            message={msg}
            panelState={state}
            onChangeState={handleChangeState(msg.id)}
            onClose={() => handleClose(msg.id)}
          />
        );
      })}

      {/* ===== 全屏清空入口（顶部居中醒目按钮，仅当有活跃便利贴时显示） ===== */}
      {visibleMessages.length > 0 && onClearAll && (
        <div className="fixed top-3 left-1/2 -translate-x-1/2 z-[9999] flex items-center gap-2" style={{ pointerEvents: 'auto' }}>
          <button
            onClick={onClearAll}
            title="清空所有对话（历史对话不受影响）"
            className="flex items-center gap-1.5 rounded-full glass-strong px-4 py-2 text-xs text-red-300 border-2 border-red-400/50 hover:border-red-400 hover:bg-red-500/20 hover:text-red-200 transition-all shadow-glow-md cursor-pointer"
          >
            <Trash2 size={14} />
            <span className="font-medium">清空对话</span>
            <span className="ml-1 bg-red-500/30 text-red-200 text-[10px] font-mono px-1.5 py-0.5 rounded-full">
              {visibleMessages.length}
            </span>
          </button>
          {/* 面板计数指示器 */}
          <span className="text-[10px] text-slate-500 font-mono pointer-events-none">
            {visibleMessages.length} / {MAX_PANELS}
          </span>
        </div>
      )}

      {/* ===== 历史便利贴面板（右上角固定收起） ===== */}
      <HistoryPanel historyMessages={historyMessages} />
    </div>
  );
}
