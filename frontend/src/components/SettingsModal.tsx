/**
 * 设置弹窗组件
 *
 * <p>集中管理所有系统设置，包含：</p>
 * <ul>
 *   <li>账户信息：登录人、登出</li>
 *   <li>房屋信息：当前房屋、切换房屋</li>
 *   <li>YML 编辑器：打开 Agent YML 编辑器</li>
 *   <li>3D 模型管理：数据库驱动的 3D 模型 CRUD</li>
 *   <li>LLM 模型配置管理：数据库驱动的 LLM 配置 CRUD + 运行时切换</li>
 * </ul>
 *
 * @author zqs
 * @since 3.1.0
 */
import { useState, useEffect, useCallback } from 'react';
import {
  X, User, LogOut, Home, Code2, Save,
  RefreshCw, Check, ChevronRight, Box, Plus, Trash2,
  Edit3, Zap, Network, Cpu, Layers, Activity, Database, Shield,
} from 'lucide-react';
import {
  getAiModelList, addAiModel, updateAiModel, deleteAiModel, setDefaultAiModel,
  getLlmModelList, addLlmModel, updateLlmModel, deleteLlmModel, setDefaultLlmModel, applyLlmModel,
  getAgentScopeFeatures, getAgentScopeCoordinations, getAgentScopeAgentsGrouped,
  type AiModel, type LlmModel,
  type AgentScopeFeature, type Coordination, type AgentBrief,
} from '../api/client';
import type { SessionStatus } from '../App';

/** SettingsModal 组件 Props */
interface SettingsModalProps {
  /** 弹窗是否打开 */
  open: boolean;
  /** 关闭弹窗回调 */
  onClose: () => void;
  /** 当前会话状态 */
  sessionStatus: SessionStatus | null;
  /** 登出回调 */
  onLogout: () => void;
  /** 打开房屋选择弹窗回调 */
  onOpenHomeSelect: () => void;
  /** 打开 YML 编辑器回调 */
  onOpenYmlEditor: () => void;
  /** 打开登录弹窗回调 */
  onOpenLogin: () => void;
  /** v3.7.0 新增：打开可观测性面板回调 */
  onOpenObserve: () => void;
  /** 打开 RAG 知识库管理弹窗回调 */
  onOpenRagManager: () => void;
}

/** 设置面板当前激活的标签页 */
type SettingsTab = 'account' | 'ai-model' | 'llm-model' | 'agentscope';

/**
 * 设置弹窗组件
 *
 * @param props 组件属性
 */
export default function SettingsModal({
  open, onClose, sessionStatus, onLogout, onOpenHomeSelect, onOpenYmlEditor, onOpenLogin, onOpenObserve, onOpenRagManager,
}: SettingsModalProps) {
  /** 当前激活的标签页 */
  const [activeTab, setActiveTab] = useState<SettingsTab>('account');
  /** 提示消息（保存成功/失败） */
  const [toast, setToast] = useState<{ msg: string; type: 'success' | 'error' } | null>(null);

  /** ===== AI 3D 模型管理状态（v3.5.0 新增）===== */
  /** 3D 模型列表 */
  const [aiModelList, setAiModelList] = useState<AiModel[]>([]);
  /** 3D 模型列表加载中 */
  const [aiModelLoading, setAiModelLoading] = useState(false);
  /** 3D 模型编辑中（null=列表模式，非 null=编辑模式） */
  const [editingAiModel, setEditingAiModel] = useState<AiModel | null>(null);

  /** ===== LLM 模型配置管理状态（v3.5.0 新增）===== */
  /** LLM 模型配置列表 */
  const [llmModelList, setLlmModelList] = useState<LlmModel[]>([]);
  /** LLM 模型配置列表加载中 */
  const [llmModelLoading, setLlmModelLoading] = useState(false);
  /** LLM 模型配置编辑中（null=列表模式，非 null=编辑模式） */
  const [editingLlmModel, setEditingLlmModel] = useState<LlmModel | null>(null);

  /** ===== AgentScope 特性状态（v3.6.0 新增）===== */
  /** AgentScope 7 大特性清单 */
  const [scopeFeatures, setScopeFeatures] = useState<AgentScopeFeature[]>([]);
  /** 协同链路示例 */
  const [scopeCoordinations, setScopeCoordinations] = useState<Coordination[]>([]);
  /** 按分类分组的 21 个 Agent */
  const [scopeAgents, setScopeAgents] = useState<Record<string, AgentBrief[]>>({});
  /** AgentScope 数据加载中 */
  const [scopeLoading, setScopeLoading] = useState(false);
  /** 当前展开的协同链路（sceneCode） */
  const [expandedCoordination, setExpandedCoordination] = useState<string | null>(null);

  /** 显示提示消息（3 秒后自动消失） */
  const showToast = (msg: string, type: 'success' | 'error') => {
    setToast({ msg, type });
    setTimeout(() => setToast(null), 3000);
  };

  // ===== AI 3D 模型管理（v3.5.0 新增）=====

  /** 加载 3D 模型列表 */
  const loadAiModelList = useCallback(async () => {
    setAiModelLoading(true);
    try {
      const r = await getAiModelList();
      if (r.success && r.data) {
        setAiModelList(r.data);
      }
    } catch (e) {
      console.warn('[settings] 3D 模型列表加载失败', e);
      showToast('3D 模型列表加载失败', 'error');
    } finally {
      setAiModelLoading(false);
    }
  }, []);

  /** 保存 3D 模型（新增或更新） */
  const handleSaveAiModel = async () => {
    if (!editingAiModel) return;
    if (!editingAiModel.modelCode || !editingAiModel.modelName || !editingAiModel.modelUrl) {
      showToast('模型编码、名称、URL 不能为空', 'error');
      return;
    }
    try {
      const r = editingAiModel.id
        ? await updateAiModel(editingAiModel)
        : await addAiModel(editingAiModel);
      if (r.success) {
        showToast(editingAiModel.id ? '模型更新成功' : '模型添加成功', 'success');
        setEditingAiModel(null);
        loadAiModelList();
      } else {
        showToast(r.message || '保存失败', 'error');
      }
    } catch (e) {
      console.error('[settings] 保存 3D 模型失败', e);
      showToast('保存失败', 'error');
    }
  };

  /** 删除 3D 模型 */
  const handleDeleteAiModel = async (id: number) => {
    try {
      const r = await deleteAiModel(id);
      if (r.success) {
        showToast('模型已删除', 'success');
        loadAiModelList();
      } else {
        showToast(r.message || '删除失败', 'error');
      }
    } catch (e) {
      console.error('[settings] 删除 3D 模型失败', e);
      showToast('删除失败', 'error');
    }
  };

  /** 设置默认 3D 模型 */
  const handleSetDefaultAiModel = async (id: number) => {
    try {
      const r = await setDefaultAiModel(id);
      if (r.success) {
        showToast('已设为默认模型', 'success');
        loadAiModelList();
      } else {
        showToast(r.message || '设置失败', 'error');
      }
    } catch (e) {
      console.error('[settings] 设置默认 3D 模型失败', e);
      showToast('设置失败', 'error');
    }
  };

  // ===== LLM 模型配置管理（v3.5.0 新增）=====

  /** 加载 LLM 模型配置列表 */
  const loadLlmModelList = useCallback(async () => {
    setLlmModelLoading(true);
    try {
      const r = await getLlmModelList();
      if (r.success && r.data) {
        setLlmModelList(r.data);
      }
    } catch (e) {
      console.warn('[settings] LLM 模型配置列表加载失败', e);
      showToast('LLM 模型配置列表加载失败', 'error');
    } finally {
      setLlmModelLoading(false);
    }
  }, []);

  /** 保存 LLM 模型配置（新增或更新） */
  const handleSaveLlmModel = async () => {
    if (!editingLlmModel) return;
    if (!editingLlmModel.modelCode || !editingLlmModel.modelName
        || !editingLlmModel.provider || !editingLlmModel.modelId) {
      showToast('模型编码、名称、provider、modelId 不能为空', 'error');
      return;
    }
    try {
      const r = editingLlmModel.id
        ? await updateLlmModel(editingLlmModel)
        : await addLlmModel(editingLlmModel);
      if (r.success) {
        showToast(editingLlmModel.id ? '配置更新成功' : '配置添加成功', 'success');
        setEditingLlmModel(null);
        loadLlmModelList();
      } else {
        showToast(r.message || '保存失败', 'error');
      }
    } catch (e) {
      console.error('[settings] 保存 LLM 模型配置失败', e);
      showToast('保存失败', 'error');
    }
  };

  /** 删除 LLM 模型配置 */
  const handleDeleteLlmModel = async (id: number) => {
    try {
      const r = await deleteLlmModel(id);
      if (r.success) {
        showToast('配置已删除', 'success');
        loadLlmModelList();
      } else {
        showToast(r.message || '删除失败', 'error');
      }
    } catch (e) {
      console.error('[settings] 删除 LLM 模型配置失败', e);
      showToast('删除失败', 'error');
    }
  };

  /** 设置默认 LLM 模型配置 */
  const handleSetDefaultLlmModel = async (id: number) => {
    try {
      const r = await setDefaultLlmModel(id);
      if (r.success) {
        showToast('已设为默认模型配置', 'success');
        loadLlmModelList();
      } else {
        showToast(r.message || '设置失败', 'error');
      }
    } catch (e) {
      console.error('[settings] 设置默认 LLM 模型配置失败', e);
      showToast('设置失败', 'error');
    }
  };

  /** 应用 LLM 模型配置到运行时（动态切换） */
  const handleApplyLlmModel = async (id: number) => {
    try {
      const r = await applyLlmModel(id);
      if (r.success) {
        showToast('模型配置已应用，运行时已切换', 'success');
        loadLlmModelList();
      } else {
        showToast(r.message || '应用失败', 'error');
      }
    } catch (e) {
      console.error('[settings] 应用 LLM 模型配置失败', e);
      showToast('应用失败', 'error');
    }
  };

  /** v3.5.0 新增：切换到 ai-model 标签页时加载 3D 模型列表 */
  useEffect(() => {
    if (open && activeTab === 'ai-model') {
      loadAiModelList();
    }
  }, [open, activeTab, loadAiModelList]);

  /** v3.5.0 新增：切换到 llm-model 标签页时加载 LLM 模型配置列表 */
  useEffect(() => {
    if (open && activeTab === 'llm-model') {
      loadLlmModelList();
    }
  }, [open, activeTab, loadLlmModelList]);

  /** v3.6.0 新增：切换到 agentscope 标签页时加载特性/协同/Agent 清单 */
  const loadAgentScopeData = useCallback(async () => {
    setScopeLoading(true);
    try {
      const [featRes, coordRes, agentsRes] = await Promise.all([
        getAgentScopeFeatures(),
        getAgentScopeCoordinations(),
        getAgentScopeAgentsGrouped(),
      ]);
      if (featRes.success && featRes.data) setScopeFeatures(featRes.data);
      if (coordRes.success && coordRes.data) setScopeCoordinations(coordRes.data);
      if (agentsRes.success && agentsRes.data) setScopeAgents(agentsRes.data);
    } catch (e) {
      console.warn('[settings] AgentScope 特性加载失败', e);
      showToast('AgentScope 特性加载失败', 'error');
    } finally {
      setScopeLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open && activeTab === 'agentscope') {
      loadAgentScopeData();
    }
  }, [open, activeTab, loadAgentScopeData]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm animate-fade-in"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-2xl max-h-[85vh] overflow-hidden rounded-2xl glass-strong border border-neon-purple/30 shadow-glow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ===== 顶部标题栏 ===== */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-neon-purple/20">
          <h2 className="text-base font-semibold gradient-text">系统设置</h2>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-neon-pink transition-colors"
            title="关闭"
          >
            <X size={18} />
          </button>
        </div>

        {/* ===== 标签页切换 ===== */}
        <div className="flex border-b border-neon-purple/15">
          <button
            onClick={() => setActiveTab('account')}
            className={`flex items-center gap-1.5 px-5 py-2.5 text-sm transition-all border-b-2 ${
              activeTab === 'account'
                ? 'text-neon-cyan border-neon-cyan'
                : 'text-slate-400 border-transparent hover:text-slate-200'
            }`}
          >
            <User size={14} />
            账户与房屋
          </button>
          {/* v3.5.0 新增：AI 3D 模型管理（数据库驱动的 3D 模型 CRUD） */}
          <button
            onClick={() => setActiveTab('ai-model')}
            className={`flex items-center gap-1.5 px-5 py-2.5 text-sm transition-all border-b-2 ${
              activeTab === 'ai-model'
                ? 'text-neon-cyan border-neon-cyan'
                : 'text-slate-400 border-transparent hover:text-slate-200'
            }`}
          >
            <Edit3 size={14} />
            3D 模型管理
          </button>
          {/* v3.5.0 新增：LLM 模型配置管理（数据库驱动的 LLM 配置 CRUD + 运行时切换） */}
          <button
            onClick={() => setActiveTab('llm-model')}
            className={`flex items-center gap-1.5 px-5 py-2.5 text-sm transition-all border-b-2 ${
              activeTab === 'llm-model'
                ? 'text-neon-cyan border-neon-cyan'
                : 'text-slate-400 border-transparent hover:text-slate-200'
            }`}
          >
            <Zap size={14} />
            LLM 模型配置
          </button>
          {/* v3.6.0 新增：AgentScope 特性（7 大特性 + 协同链路 + 21 Agent 清单） */}
          <button
            onClick={() => setActiveTab('agentscope')}
            className={`flex items-center gap-1.5 px-5 py-2.5 text-sm transition-all border-b-2 ${
              activeTab === 'agentscope'
                ? 'text-neon-cyan border-neon-cyan'
                : 'text-slate-400 border-transparent hover:text-slate-200'
            }`}
          >
            <Network size={14} />
            AgentScope 特性
          </button>
        </div>

        {/* ===== 内容区 ===== */}
        <div className="overflow-y-auto max-h-[calc(85vh-110px)] px-5 py-4">
          {/* ===== 账户与房屋标签页 ===== */}
          {activeTab === 'account' && (
            <div className="space-y-4 animate-fade-in">
              {/* 账户信息 */}
              <section className="rounded-xl glass-panel p-4">
                <h3 className="text-sm font-medium text-slate-200 mb-3 flex items-center gap-1.5">
                  <User size={14} className="text-neon-purple" />
                  账户信息
                </h3>
                {sessionStatus?.loggedIn ? (
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <div className="rounded-full bg-gradient-to-br from-neon-purple to-neon-purple p-1.5 text-white shadow-glow-sm">
                        <User size={14} />
                      </div>
                      <span className="text-sm text-slate-200 flex-1">
                        {sessionStatus.loginName || '已登录'}
                      </span>
                      <button
                        onClick={onLogout}
                        className="flex items-center gap-1 rounded-md px-2.5 py-1 text-xs text-red-300 border border-red-500/40 hover:bg-red-900/30 transition-all"
                      >
                        <LogOut size={12} />
                        登出
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    onClick={() => {
                      onOpenLogin();
                      onClose();
                    }}
                    className="shimmer-btn w-full rounded-lg glass-panel hover-neon px-3 py-2.5 transition-all flex items-center justify-center gap-2 text-xs font-medium text-neon-cyan border border-neon-cyan/30"
                  >
                    <User size={12} />
                    登录 HDL 账号
                  </button>
                )}
              </section>

              {/* 房屋信息 */}
              <section className="rounded-xl glass-panel p-4">
                <h3 className="text-sm font-medium text-slate-200 mb-3 flex items-center gap-1.5">
                  <Home size={14} className="text-neon-cyan" />
                  房屋信息
                </h3>
                {sessionStatus?.currentHomeName ? (
                  <div className="flex items-center gap-2">
                    <span className="text-neon-cyan">⌂</span>
                    <span className="text-sm text-slate-200 flex-1 truncate">
                      {sessionStatus.currentHomeName}
                    </span>
                    <button
                      onClick={() => {
                        onOpenHomeSelect();
                        onClose();
                      }}
                      className="flex items-center gap-1 rounded-md px-2.5 py-1 text-xs text-neon-cyan border border-neon-cyan/40 hover:bg-neon-cyan/10 transition-all"
                    >
                      <RefreshCw size={11} />
                      切换
                    </button>
                  </div>
                ) : (
                  <p className="text-xs text-slate-500">未选择房屋</p>
                )}
              </section>

              {/* YML 编辑器入口 */}
              <section className="rounded-xl glass-panel p-4">
                <h3 className="text-sm font-medium text-slate-200 mb-3 flex items-center gap-1.5">
                  <Code2 size={14} className="text-neon-purple" />
                  高级配置
                </h3>
                <div className="space-y-2">
                  {/* v3.7.0 新增：可观测性面板入口 */}
                  <button
                    onClick={onOpenObserve}
                    className="w-full flex items-center gap-2 rounded-lg glass-panel hover-neon px-3 py-2.5 transition-all text-left border border-neon-cyan/20"
                  >
                    <Activity size={14} className="text-neon-cyan" />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm text-slate-200">可观测性面板</div>
                      <div className="text-[10px] text-slate-500 font-mono">实时事件流 / Agent 拓扑 / 指标监控</div>
                    </div>
                    <ChevronRight size={14} className="text-slate-500" />
                  </button>
                  {/* RAG 知识库管理入口（文档上传 / 列表 / 删除 / 检索测试） */}
                  <button
                    onClick={onOpenRagManager}
                    className="w-full flex items-center gap-2 rounded-lg glass-panel hover-neon px-3 py-2.5 transition-all text-left border border-neon-purple/20"
                  >
                    <Database size={14} className="text-neon-purple" />
                    <div className="flex-1 min-w-0">
                      <div className="text-sm text-slate-200">RAG 知识库管理</div>
                      <div className="text-[10px] text-slate-500 font-mono">文档上传 / 向量检索 / 知识库维护</div>
                    </div>
                    <ChevronRight size={14} className="text-slate-500" />
                  </button>
                  {/* Agent YML 编辑器入口（v4.3.3 编辑 Nacos YML 配置，保存后热重载 HarnessAgent） */}
                  <button
                    onClick={() => {
                      onOpenYmlEditor();
                      onClose();
                    }}
                    className="w-full flex items-center gap-2 rounded-lg glass-panel hover-neon px-3 py-2.5 transition-all text-left"
                  >
                    <Code2 size={14} className="text-neon-purple" />
                    <span className="text-sm text-slate-200 flex-1">Agent YML 编辑器</span>
                    <ChevronRight size={14} className="text-slate-500" />
                  </button>
                </div>
              </section>
            </div>
          )}

          {/* ===== AI 3D 模型管理标签页（v3.5.0 新增：数据库驱动的 CRUD）===== */}
          {activeTab === 'ai-model' && (
            <div className="space-y-4 animate-fade-in">
              {/* 顶部操作栏：标题 + 新增按钮 */}
              <section className="rounded-xl glass-panel p-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-medium text-slate-200 flex items-center gap-1.5">
                    <Edit3 size={14} className="text-neon-purple" />
                    3D 模型列表
                  </h3>
                  <button
                    onClick={() => setEditingAiModel({
                      modelCode: '',
                      modelName: '',
                      modelUrl: '',
                      modelType: 'glb',
                      scale: 1,
                      positionX: 0,
                      positionY: 0,
                      positionZ: 0,
                      rotationY: 0,
                      isDefault: 0,
                      sortOrder: 0,
                      status: 'active',
                    })}
                    className="flex items-center gap-1 rounded-md px-2.5 py-1 text-xs text-neon-cyan border border-neon-cyan/40 hover:bg-neon-cyan/10 transition-all"
                  >
                    <Plus size={12} />
                    新增模型
                  </button>
                </div>

                {/* 列表区域 */}
                {aiModelLoading ? (
                  <div className="flex items-center justify-center py-6 text-slate-400">
                    <RefreshCw size={14} className="animate-spin text-neon-purple mr-2" />
                    加载中...
                  </div>
                ) : aiModelList.length > 0 ? (
                  <div className="space-y-2">
                    {aiModelList.map(model => (
                      <div
                        key={model.id}
                        className="flex items-center gap-2 rounded-md bg-slate-900/40 border border-slate-700/40 px-2.5 py-2"
                      >
                        <Box size={12} className="text-neon-purple flex-shrink-0" />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="text-xs text-slate-200 truncate">{model.modelName}</span>
                            {model.isDefault === 1 && (
                              <span className="text-[10px] text-green-400 font-mono">[默认]</span>
                            )}
                          </div>
                          <div className="text-[10px] text-slate-500 font-mono truncate">
                            {model.modelCode} · {model.modelType}
                          </div>
                        </div>
                        {/* 操作按钮组 */}
                        <button
                          onClick={() => setEditingAiModel({ ...model })}
                          className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-neon-cyan border border-neon-cyan/40 hover:bg-neon-cyan/10 transition-all flex-shrink-0"
                          title="编辑"
                        >
                          <Edit3 size={10} />
                          编辑
                        </button>
                        {model.isDefault !== 1 && (
                          <button
                            onClick={() => handleSetDefaultAiModel(model.id!)}
                            className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-green-300 border border-green-500/40 hover:bg-green-900/30 transition-all flex-shrink-0"
                            title="设为默认"
                          >
                            <Check size={10} />
                            默认
                          </button>
                        )}
                        <button
                          onClick={() => handleDeleteAiModel(model.id!)}
                          className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-red-300 border border-red-500/40 hover:bg-red-900/30 transition-all flex-shrink-0"
                          title="删除"
                        >
                          <Trash2 size={10} />
                          删除
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-500 py-4 text-center">暂无 3D 模型，点击右上角"新增模型"添加</p>
                )}
              </section>

              {/* 编辑/新增表单（editingAiModel 非 null 时显示） */}
              {editingAiModel && (
                <section className="rounded-xl glass-panel p-4 border-2 border-neon-cyan/40">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="text-sm font-medium text-slate-200 flex items-center gap-1.5">
                      <Edit3 size={14} className="text-neon-cyan" />
                      {editingAiModel.id ? '编辑 3D 模型' : '新增 3D 模型'}
                    </h3>
                    <button
                      onClick={() => setEditingAiModel(null)}
                      className="text-slate-400 hover:text-slate-200 transition-colors"
                      title="取消"
                    >
                      <X size={14} />
                    </button>
                  </div>
                  <div className="grid grid-cols-2 gap-2.5">
                    {/* 模型编码 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">模型编码 *</span>
                      <input
                        type="text"
                        value={editingAiModel.modelCode || ''}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, modelCode: e.target.value })}
                        placeholder="ai-girl-default"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* 模型名称 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">模型名称 *</span>
                      <input
                        type="text"
                        value={editingAiModel.modelName || ''}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, modelName: e.target.value })}
                        placeholder="默认女孩模型"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50"
                      />
                    </label>
                    {/* 模型 URL */}
                    <label className="block col-span-2">
                      <span className="text-[11px] text-slate-400 font-mono">模型 URL *</span>
                      <input
                        type="text"
                        value={editingAiModel.modelUrl || ''}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, modelUrl: e.target.value })}
                        placeholder="/models/avatar.glb"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* 模型类型 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">模型类型</span>
                      <select
                        value={editingAiModel.modelType || 'glb'}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, modelType: e.target.value })}
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50"
                      >
                        <option value="glb">glb</option>
                        <option value="gltf">gltf</option>
                        <option value="fbx">fbx</option>
                      </select>
                    </label>
                    {/* 缩略图 URL */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">缩略图 URL</span>
                      <input
                        type="text"
                        value={editingAiModel.thumbnailUrl || ''}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, thumbnailUrl: e.target.value })}
                        placeholder="/models/thumb.png"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* 缩放比例 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">缩放比例</span>
                      <input
                        type="number"
                        step="0.1"
                        value={editingAiModel.scale ?? 1}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, scale: parseFloat(e.target.value) || 1 })}
                        placeholder="1"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* 排序序号 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">排序序号</span>
                      <input
                        type="number"
                        value={editingAiModel.sortOrder ?? 0}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, sortOrder: parseInt(e.target.value, 10) || 0 })}
                        placeholder="0"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* X 轴位置 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">X 轴位置</span>
                      <input
                        type="number"
                        step="0.1"
                        value={editingAiModel.positionX ?? 0}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, positionX: parseFloat(e.target.value) || 0 })}
                        placeholder="0"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* Y 轴位置 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">Y 轴位置</span>
                      <input
                        type="number"
                        step="0.1"
                        value={editingAiModel.positionY ?? 0}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, positionY: parseFloat(e.target.value) || 0 })}
                        placeholder="0"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* Z 轴位置 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">Z 轴位置</span>
                      <input
                        type="number"
                        step="0.1"
                        value={editingAiModel.positionZ ?? 0}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, positionZ: parseFloat(e.target.value) || 0 })}
                        placeholder="0"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* Y 轴旋转角度 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">Y 轴旋转角度</span>
                      <input
                        type="number"
                        step="0.1"
                        value={editingAiModel.rotationY ?? 0}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, rotationY: parseFloat(e.target.value) || 0 })}
                        placeholder="0"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50 font-mono"
                      />
                    </label>
                    {/* 状态 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">状态</span>
                      <select
                        value={editingAiModel.status || 'active'}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, status: e.target.value })}
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50"
                      >
                        <option value="active">启用</option>
                        <option value="inactive">禁用</option>
                      </select>
                    </label>
                    {/* 描述 */}
                    <label className="block col-span-2">
                      <span className="text-[11px] text-slate-400 font-mono">描述</span>
                      <input
                        type="text"
                        value={editingAiModel.description || ''}
                        onChange={(e) => setEditingAiModel({ ...editingAiModel, description: e.target.value })}
                        placeholder="模型描述（可选）"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-cyan/50"
                      />
                    </label>
                  </div>
                  {/* 表单操作按钮 */}
                  <div className="flex gap-2 mt-3">
                    <button
                      onClick={handleSaveAiModel}
                      className="flex-1 flex items-center justify-center gap-1.5 rounded-lg bg-gradient-to-br from-neon-cyan/80 to-neon-cyan/60 px-3 py-2 text-xs text-slate-900 font-medium hover:shadow-glow-sm transition-all"
                    >
                      <Save size={12} />
                      保存
                    </button>
                    <button
                      onClick={() => setEditingAiModel(null)}
                      className="flex-1 rounded-lg border border-slate-700/50 px-3 py-2 text-xs text-slate-300 hover:bg-slate-800/40 transition-all"
                    >
                      取消
                    </button>
                  </div>
                </section>
              )}
            </div>
          )}

          {/* ===== LLM 模型配置标签页（v3.5.0 新增：数据库驱动的 CRUD + 运行时切换）===== */}
          {activeTab === 'llm-model' && (
            <div className="space-y-4 animate-fade-in">
              {/* 顶部操作栏：标题 + 新增按钮 */}
              <section className="rounded-xl glass-panel p-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-sm font-medium text-slate-200 flex items-center gap-1.5">
                    <Zap size={14} className="text-neon-purple" />
                    LLM 模型配置列表
                  </h3>
                  <button
                    onClick={() => setEditingLlmModel({
                      modelCode: '',
                      modelName: '',
                      provider: 'ollama',
                      modelId: '',
                      apiBaseUrl: '',
                      apiKey: '',
                      maxTokens: 300,
                      temperature: 0.1,
                      isDefault: 0,
                      isActive: 1,
                      sortOrder: 0,
                    })}
                    className="flex items-center gap-1 rounded-md px-2.5 py-1 text-xs text-neon-cyan border border-neon-cyan/40 hover:bg-neon-cyan/10 transition-all"
                  >
                    <Plus size={12} />
                    新增配置
                  </button>
                </div>

                {/* 列表区域 */}
                {llmModelLoading ? (
                  <div className="flex items-center justify-center py-6 text-slate-400">
                    <RefreshCw size={14} className="animate-spin text-neon-purple mr-2" />
                    加载中...
                  </div>
                ) : llmModelList.length > 0 ? (
                  <div className="space-y-2">
                    {llmModelList.map(model => (
                      <div
                        key={model.id}
                        className="flex items-center gap-2 rounded-md bg-slate-900/40 border border-slate-700/40 px-2.5 py-2"
                      >
                        <Zap size={12} className={`flex-shrink-0 ${model.provider === 'ollama' ? 'text-neon-cyan' : 'text-neon-purple'}`} />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="text-xs text-slate-200 truncate">{model.modelName}</span>
                            {model.isDefault === 1 && (
                              <span className="text-[10px] text-green-400 font-mono">[默认]</span>
                            )}
                            {model.isActive === 1 ? (
                              <span className="text-[10px] text-green-400 font-mono">[启用]</span>
                            ) : (
                              <span className="text-[10px] text-slate-500 font-mono">[禁用]</span>
                            )}
                          </div>
                          <div className="text-[10px] text-slate-500 font-mono truncate">
                            {model.modelCode} · {model.provider} · {model.modelId}
                          </div>
                        </div>
                        {/* 操作按钮组 */}
                        <button
                          onClick={() => setEditingLlmModel({ ...model })}
                          className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-neon-cyan border border-neon-cyan/40 hover:bg-neon-cyan/10 transition-all flex-shrink-0"
                          title="编辑"
                        >
                          <Edit3 size={10} />
                          编辑
                        </button>
                        {/* 应用到运行时按钮：动态切换 LLM 配置 */}
                        <button
                          onClick={() => handleApplyLlmModel(model.id!)}
                          className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-yellow-300 border border-yellow-500/40 hover:bg-yellow-900/30 transition-all flex-shrink-0"
                          title="应用到运行时（立即切换）"
                        >
                          <Zap size={10} />
                          应用
                        </button>
                        {model.isDefault !== 1 && (
                          <button
                            onClick={() => handleSetDefaultLlmModel(model.id!)}
                            className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-green-300 border border-green-500/40 hover:bg-green-900/30 transition-all flex-shrink-0"
                            title="设为默认"
                          >
                            <Check size={10} />
                            默认
                          </button>
                        )}
                        <button
                          onClick={() => handleDeleteLlmModel(model.id!)}
                          className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-red-300 border border-red-500/40 hover:bg-red-900/30 transition-all flex-shrink-0"
                          title="删除"
                        >
                          <Trash2 size={10} />
                          删除
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-500 py-4 text-center">暂无 LLM 模型配置，点击右上角"新增配置"添加</p>
                )}
              </section>

              {/* 编辑/新增表单（editingLlmModel 非 null 时显示） */}
              {editingLlmModel && (
                <section className="rounded-xl glass-panel p-4 border-2 border-neon-purple/40">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="text-sm font-medium text-slate-200 flex items-center gap-1.5">
                      <Zap size={14} className="text-neon-purple" />
                      {editingLlmModel.id ? '编辑 LLM 模型配置' : '新增 LLM 模型配置'}
                    </h3>
                    <button
                      onClick={() => setEditingLlmModel(null)}
                      className="text-slate-400 hover:text-slate-200 transition-colors"
                      title="取消"
                    >
                      <X size={14} />
                    </button>
                  </div>
                  <div className="grid grid-cols-2 gap-2.5">
                    {/* 模型编码 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">模型编码 *</span>
                      <input
                        type="text"
                        value={editingLlmModel.modelCode || ''}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, modelCode: e.target.value })}
                        placeholder="qwen2.5-1.5b"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50 font-mono"
                      />
                    </label>
                    {/* 模型名称 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">模型名称 *</span>
                      <input
                        type="text"
                        value={editingLlmModel.modelName || ''}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, modelName: e.target.value })}
                        placeholder="Qwen2.5 1.5B 本地模型"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50"
                      />
                    </label>
                    {/* provider */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">Provider *</span>
                      <select
                        value={editingLlmModel.provider || 'ollama'}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, provider: e.target.value })}
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50"
                      >
                        <option value="ollama">ollama（本地）</option>
                        <option value="cloud">cloud（云端 OpenAI 兼容）</option>
                      </select>
                    </label>
                    {/* 模型 ID */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">模型 ID *</span>
                      <input
                        type="text"
                        value={editingLlmModel.modelId || ''}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, modelId: e.target.value })}
                        placeholder="qwen2.5:1.5b / deepseek-chat"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50 font-mono"
                      />
                    </label>
                    {/* API 地址 */}
                    <label className="block col-span-2">
                      <span className="text-[11px] text-slate-400 font-mono">API 地址</span>
                      <input
                        type="text"
                        value={editingLlmModel.apiBaseUrl || ''}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, apiBaseUrl: e.target.value })}
                        placeholder="http://localhost:11434（ollama）/ https://api.deepseek.com/v1（cloud）"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50 font-mono"
                      />
                    </label>
                    {/* API Key（仅 cloud 模型需要） */}
                    <label className="block col-span-2">
                      <span className="text-[11px] text-slate-400 font-mono">API Key（cloud 模型必填，编辑时 **** 表示保留原值）</span>
                      <input
                        type="password"
                        value={editingLlmModel.apiKey || ''}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, apiKey: e.target.value })}
                        placeholder="sk-****"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50 font-mono"
                      />
                    </label>
                    {/* 最大 token 数 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">最大 Token 数</span>
                      <input
                        type="number"
                        value={editingLlmModel.maxTokens ?? 300}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, maxTokens: parseInt(e.target.value, 10) || 300 })}
                        placeholder="300"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50 font-mono"
                      />
                    </label>
                    {/* 温度 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">温度 Temperature</span>
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        max="2"
                        value={editingLlmModel.temperature ?? 0.1}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, temperature: parseFloat(e.target.value) || 0.1 })}
                        placeholder="0.1"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50 font-mono"
                      />
                    </label>
                    {/* 排序序号 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">排序序号</span>
                      <input
                        type="number"
                        value={editingLlmModel.sortOrder ?? 0}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, sortOrder: parseInt(e.target.value, 10) || 0 })}
                        placeholder="0"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50 font-mono"
                      />
                    </label>
                    {/* 是否启用 */}
                    <label className="block">
                      <span className="text-[11px] text-slate-400 font-mono">是否启用</span>
                      <select
                        value={editingLlmModel.isActive ?? 1}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, isActive: parseInt(e.target.value, 10) })}
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50"
                      >
                        <option value={1}>启用</option>
                        <option value={0}>禁用</option>
                      </select>
                    </label>
                    {/* 描述 */}
                    <label className="block col-span-2">
                      <span className="text-[11px] text-slate-400 font-mono">描述</span>
                      <input
                        type="text"
                        value={editingLlmModel.description || ''}
                        onChange={(e) => setEditingLlmModel({ ...editingLlmModel, description: e.target.value })}
                        placeholder="模型描述（可选）"
                        className="mt-0.5 w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2.5 py-1.5 text-xs text-slate-200 outline-none focus:border-neon-purple/50"
                      />
                    </label>
                  </div>
                  {/* 表单操作按钮 */}
                  <div className="flex gap-2 mt-3">
                    <button
                      onClick={handleSaveLlmModel}
                      className="flex-1 flex items-center justify-center gap-1.5 rounded-lg bg-gradient-to-br from-neon-purple/80 to-neon-purple/60 px-3 py-2 text-xs text-white font-medium hover:shadow-glow-sm transition-all"
                    >
                      <Save size={12} />
                      保存
                    </button>
                    <button
                      onClick={() => setEditingLlmModel(null)}
                      className="flex-1 rounded-lg border border-slate-700/50 px-3 py-2 text-xs text-slate-300 hover:bg-slate-800/40 transition-all"
                    >
                      取消
                    </button>
                  </div>
                </section>
              )}
            </div>
          )}

          {/* ===== AgentScope 特性标签页（v3.6.0 新增：7 大特性 + 协同链路 + 21 Agent 清单）===== */}
          {activeTab === 'agentscope' && (
            <div className="space-y-4 animate-fade-in">
              {scopeLoading ? (
                <div className="flex items-center justify-center py-8 text-slate-400">
                  <RefreshCw size={16} className="animate-spin text-neon-purple mr-2" />
                  加载 AgentScope 特性数据...
                </div>
              ) : (
                <>
                  {/* ===== 7 大核心特性 ===== */}
                  <section className="rounded-xl glass-panel p-4">
                    <h3 className="text-sm font-medium text-slate-200 mb-3 flex items-center gap-1.5">
                      <Cpu size={14} className="text-neon-purple" />
                      7 大核心特性
                      <span className="text-[10px] text-slate-500 font-mono">（共 {scopeFeatures.length} 项）</span>
                    </h3>
                    <div className="space-y-2.5">
                      {scopeFeatures.map(feat => {
                        // 根据特性 code 选择不同图标
                        const iconMap: Record<string, typeof Cpu> = {
                          'multi-model': Layers,
                          'react-loop': Cpu,
                          'memory': Database,
                          'rag': Database,
                          'observe': Activity,
                          'tenant': Shield,
                          'multi-agent': Network,
                        };
                        const Icon = iconMap[feat.code] || Cpu;
                        return (
                          <div
                            key={feat.code}
                            className="rounded-md bg-slate-900/40 border border-slate-700/40 px-3 py-2.5"
                          >
                            <div className="flex items-center gap-2 mb-1.5">
                              <Icon size={12} className="text-neon-cyan flex-shrink-0" />
                              <span className="text-xs font-medium text-slate-200 flex-1">{feat.name}</span>
                              <span className="text-[10px] text-slate-500 font-mono">{feat.code}</span>
                            </div>
                            <p className="text-[11px] text-slate-400 leading-relaxed mb-1.5">{feat.description}</p>
                            <div className="text-[10px] text-slate-500 font-mono leading-relaxed bg-slate-900/60 rounded px-2 py-1 mb-1.5">
                              <span className="text-neon-purple">实现：</span>{feat.implementation}
                            </div>
                            <div className="grid grid-cols-1 gap-1 mt-2">
                              <div className="text-[11px] text-slate-300 leading-relaxed">
                                <span className="text-neon-cyan">测试指令：</span>
                                <code className="ml-1 px-1.5 py-0.5 rounded bg-neon-cyan/10 text-neon-cyan text-[10px]">
                                  {feat.testCommand}
                                </code>
                              </div>
                              <div className="text-[10px] text-slate-500 leading-relaxed">
                                <span className="text-yellow-400">测试步骤：</span>{feat.testStep}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </section>

                  {/* ===== 多 Agent 协同链路 ===== */}
                  <section className="rounded-xl glass-panel p-4">
                    <h3 className="text-sm font-medium text-slate-200 mb-3 flex items-center gap-1.5">
                      <Network size={14} className="text-neon-cyan" />
                      多 Agent 协同链路
                      <span className="text-[10px] text-slate-500 font-mono">（共 {scopeCoordinations.length} 个协同场景）</span>
                    </h3>
                    <div className="space-y-2">
                      {scopeCoordinations.map(coord => {
                        const expanded = expandedCoordination === coord.sceneCode;
                        return (
                          <div
                            key={coord.sceneCode}
                            className="rounded-md bg-slate-900/40 border border-slate-700/40 overflow-hidden"
                          >
                            {/* 折叠头 */}
                            <button
                              onClick={() => setExpandedCoordination(expanded ? null : coord.sceneCode)}
                              className="w-full flex items-center gap-2 px-3 py-2 hover:bg-slate-800/40 transition-all text-left"
                            >
                              <ChevronRight
                                size={12}
                                className={`text-slate-500 transition-transform ${expanded ? 'rotate-90' : ''}`}
                              />
                              <span className="text-xs font-medium text-slate-200 flex-1">{coord.sceneName}</span>
                              <span className="text-[10px] text-slate-500 font-mono">{coord.steps.length} 步</span>
                            </button>
                            {/* 展开内容 */}
                            {expanded && (
                              <div className="px-3 pb-3 pt-1 space-y-2">
                                <p className="text-[11px] text-slate-400 leading-relaxed">{coord.description}</p>
                                <div className="text-[11px] text-slate-300 leading-relaxed bg-slate-900/60 rounded px-2 py-1.5 mb-2">
                                  <span className="text-neon-cyan">用户输入：</span>
                                  <code className="ml-1 px-1.5 py-0.5 rounded bg-neon-cyan/10 text-neon-cyan text-[10px]">
                                    {coord.userInput}
                                  </code>
                                </div>
                                {/* 调用链路（垂直时间线） */}
                                <div className="relative pl-4 space-y-1.5">
                                  <div className="absolute left-[5px] top-1 bottom-1 w-px bg-neon-purple/30" />
                                  {coord.steps.map((step, idx) => (
                                    <div key={idx} className="relative">
                                      <div className="absolute -left-4 top-1 w-2 h-2 rounded-full bg-neon-cyan shadow-glow-sm" />
                                      <div className="ml-2">
                                        <div className="flex items-center gap-1.5">
                                          <span className="text-[10px] text-slate-500 font-mono">#{idx + 1}</span>
                                          <span className="text-[11px] text-neon-purple font-medium">{step.actor}</span>
                                          <span className="text-[10px] text-slate-500">→</span>
                                          <span className="text-[10px] text-slate-400 font-mono">{step.component}</span>
                                        </div>
                                        <p className="text-[10px] text-slate-400 leading-relaxed mt-0.5">{step.action}</p>
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </section>

                  {/* ===== 21 个 Agent 清单（按分类分组）===== */}
                  <section className="rounded-xl glass-panel p-4">
                    <h3 className="text-sm font-medium text-slate-200 mb-3 flex items-center gap-1.5">
                      <Layers size={14} className="text-neon-purple" />
                      21 个 Agent 清单
                      <span className="text-[10px] text-slate-500 font-mono">（按业务分类）</span>
                    </h3>
                    <div className="grid grid-cols-2 gap-2.5">
                      {Object.entries(scopeAgents).map(([category, agents]) => (
                        <div
                          key={category}
                          className="rounded-md bg-slate-900/40 border border-slate-700/40 p-2.5"
                        >
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-[11px] font-medium text-neon-cyan">{category}</span>
                            <span className="text-[10px] text-slate-500 font-mono">{agents.length}</span>
                          </div>
                          <div className="space-y-1">
                            {agents.map(agent => (
                              <div
                                key={agent.agentId}
                                className="rounded bg-slate-900/60 px-1.5 py-1 hover:bg-slate-800/60 transition-colors"
                                title={agent.description}
                              >
                                <div className="flex items-center gap-1.5">
                                  <span className="text-[10px] text-slate-500 font-mono flex-shrink-0">▸</span>
                                  <span className="text-[11px] text-slate-200 truncate flex-1">{agent.name}</span>
                                </div>
                                <div className="text-[9px] text-slate-600 font-mono truncate ml-2.5">{agent.agentId}</div>
                              </div>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </section>
                </>
              )}
            </div>
          )}
        </div>

        {/* ===== Toast 提示 ===== */}
        {toast && (
          <div className={`absolute bottom-4 left-1/2 -translate-x-1/2 rounded-lg px-4 py-2 text-xs font-medium animate-fade-in ${
            toast.type === 'success'
              ? 'bg-green-900/80 text-green-200 border border-green-500/40'
              : 'bg-red-900/80 text-red-200 border border-red-500/40'
          }`}>
            {toast.msg}
          </div>
        )}
      </div>
    </div>
  );
}
