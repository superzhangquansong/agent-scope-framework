import { useState, useEffect, useMemo, useCallback } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Home, Film, Star, Play, Info, Trash2, ChevronLeft,
  RefreshCw, CheckCircle2, XCircle, Pencil,
} from 'lucide-react';
import SceneDetailPage from './SceneDetailPage';
import {
  executeScene, getSceneDetail, deleteScene, updateScene, getSceneList,
  type SceneItem, type SceneFunctionDto,
} from '../api/client';
import { Pagination } from '../shared/components';

/** 场景列表每页条数（前端客户端分页） */
const PAGE_SIZE = 6;

/** Toast 提示类型 */
type ToastType = 'success' | 'error' | 'info';
interface ToastState {
  type: ToastType;
  message: string;
}

interface SceneListPageProps {
  /** SSE result 事件携带的结构化数据（DynamicPage 传入） */
  data: Record<string, unknown>;
  /** 可选：外部直接传入场景列表（如 SceneCreateResultPage 创建成功后传入） */
  scenes?: SceneItem[];
}

/**
 * 场景列表页
 *
 * - 每个场景卡片显示：sceneName + sceneType 标签（1=影音，0=普通）+ collect 收藏标记 + roomNames 房间标签
 * - 操作按钮：
 *   - 执行按钮 → 调用 executeScene({sceneId}) REST API
 *   - 详情按钮 → 调用 getSceneDetail({sceneId}) REST API → 内联展示场景详情
 *   - 删除按钮（canDelete="1" 时显示）→ 调用 deleteScene({sceneId}) REST API，成功后从列表移除
 * - 前端客户端分页（6 条/页）
 * - 执行/删除后显示 toast 提示
 */
export default function SceneListPage({ data, scenes: externalScenes }: SceneListPageProps) {
  // 兼容两种数据来源：外部传入 scenes 优先，否则从 data 中读取
  const payload = data as { scenes?: SceneItem[]; total?: number };
  const initialScenes = useMemo(() => externalScenes ?? payload.scenes ?? [], [externalScenes, payload.scenes]);
  const initialTotal = payload.total ?? initialScenes.length;

  // 本地场景列表状态（删除后从列表移除）
  const [scenes, setScenes] = useState<SceneItem[]>(initialScenes);
  const [total, setTotal] = useState<number>(initialTotal);
  // 标记是否正在通过 REST 拉取场景列表
  const [loadingList, setLoadingList] = useState<boolean>(initialScenes.length === 0);

  useEffect(() => {
    setScenes(initialScenes);
  }, [initialScenes]);

  /**
   * 当 SSE 报文未携带 scenes 时，通过 /api/scene/list REST 接口拉取场景列表。
   * 精简报文模式：后端仅返回 routePath=/scene/list，由前端自行查询场景数据。
   */
  useEffect(() => {
    if (initialScenes.length > 0) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await getSceneList();
        if (cancelled) return;
        if (res.success && res.data) {
          setScenes(res.data.scenes ?? []);
          setTotal(res.data.total ?? 0);
        }
      } catch {
        // 静默失败，保留空列表
      } finally {
        if (!cancelled) setLoadingList(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 前端客户端分页
  const totalPages = Math.max(1, Math.ceil(scenes.length / PAGE_SIZE));
  const [page, setPage] = useState(1);

  useEffect(() => {
    setPage(1);
  }, [scenes]);

  const currentPage = Math.min(page, totalPages);
  const start = (currentPage - 1) * PAGE_SIZE;
  const pageItems = scenes.slice(start, start + PAGE_SIZE);

  const goToPage = (p: number) => {
    const target = Math.min(Math.max(1, p), totalPages);
    if (target === currentPage) return;
    setPage(target);
  };

  // Toast 提示
  const [toast, setToast] = useState<ToastState | null>(null);
  const showToast = useCallback((type: ToastType, message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 2500);
  }, []);

  // 内联场景详情
  const [inlineDetail, setInlineDetail] = useState<Record<string, unknown> | null>(null);
  const [loadingDetailId, setLoadingDetailId] = useState<string | undefined>();
  const [actionLoadingId, setActionLoadingId] = useState<string | undefined>();

  /** 获取场景 ID（兼容 sceneId / userSceneId） */
  const getSceneId = (s: SceneItem): string => String(s.sceneId ?? s.userSceneId ?? '');

  /** 获取场景名称（兼容 sceneName / name） */
  const getSceneName = (s: SceneItem): string => s.sceneName ?? s.name ?? '未命名场景';

  /** 执行场景 */
  const handleExecute = async (s: SceneItem) => {
    const sceneId = getSceneId(s);
    const sceneName = getSceneName(s);
    if (!sceneId || actionLoadingId) return;
    setActionLoadingId(sceneId);
    try {
      const res = await executeScene({ sceneId });
      if (res.success) {
        showToast('success', `场景「${sceneName}」已执行`);
      } else {
        showToast('error', res.message || `场景「${sceneName}」执行失败`);
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      showToast('error', `执行失败：${msg}`);
    } finally {
      setActionLoadingId(undefined);
    }
  };

  /** 查看详情：内联展示 */
  const handleDetail = async (s: SceneItem) => {
    const sceneId = getSceneId(s);
    if (!sceneId || loadingDetailId) return;
    setLoadingDetailId(sceneId);
    try {
      const res = await getSceneDetail({ sceneId });
      if (res.success && res.data) {
        // 合并场景名等基础信息（后端详情可能不含 sceneName）
        const detailData = {
          ...(res.data as Record<string, unknown>),
          sceneId,
          sceneName: (res.data as Record<string, unknown>).sceneName ?? getSceneName(s),
          sceneType: (res.data as Record<string, unknown>).sceneType ?? s.sceneType,
        };
        setInlineDetail(detailData);
      } else {
        showToast('error', res.message || '获取场景详情失败');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      showToast('error', `获取详情失败：${msg}`);
    } finally {
      setLoadingDetailId(undefined);
    }
  };

  /** 删除场景：成功后从列表移除 */
  const handleDelete = async (s: SceneItem) => {
    const sceneId = getSceneId(s);
    const sceneName = getSceneName(s);
    if (!sceneId || actionLoadingId) return;
    setActionLoadingId(sceneId);
    try {
      const res = await deleteScene({ sceneId });
      if (res.success) {
        // 从本地列表移除
        setScenes(prev => prev.filter(item => getSceneId(item) !== sceneId));
        showToast('success', `场景「${sceneName}」已删除`);
      } else {
        showToast('error', res.message || `删除场景「${sceneName}」失败`);
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      showToast('error', `删除失败：${msg}`);
    } finally {
      setActionLoadingId(undefined);
    }
  };

  // ===== 编辑场景状态 =====
  /** 当前正在编辑的场景 ID */
  const [editingSceneId, setEditingSceneId] = useState<string | undefined>();
  /** 编辑中的场景名称（受控输入） */
  const [editingName, setEditingName] = useState('');
  /** 编辑中从详情接口获取的原始设备动作列表（用于提交时回传 functions） */
  const [editingFunctions, setEditingFunctions] = useState<SceneFunctionDto[]>([]);
  /** 编辑中从详情接口获取的场景级配置 */
  const [editingCollect, setEditingCollect] = useState(false);
  const [editingDelay, setEditingDelay] = useState(0);
  const [editingExecutePush, setEditingExecutePush] = useState(false);

  /** 点击编辑：获取场景详情，提取 functions 并填充编辑表单 */
  const handleEdit = async (s: SceneItem) => {
    const sceneId = getSceneId(s);
    const sceneName = getSceneName(s);
    if (!sceneId || actionLoadingId) return;
    setActionLoadingId(sceneId);
    try {
      const res = await getSceneDetail({ sceneId });
      if (res.success && res.data) {
        const detail = res.data as Record<string, unknown>;
        // 从详情的 devices 数组重建 functions：devices[].deviceId 即 sid，actions 即 status
        const detailDevices = Array.isArray(detail.devices) ? detail.devices : [];
        const functions: SceneFunctionDto[] = detailDevices.map((d: unknown) => {
          const dev = d as Record<string, unknown>;
          const sid = String(dev.deviceId ?? dev.sid ?? '');
          const actions = Array.isArray(dev.actions) ? dev.actions : [];
          const status = actions.map((a: unknown) => {
            const attr = a as Record<string, unknown>;
            return { key: String(attr.key ?? ''), value: String(attr.value ?? '') };
          });
          return { sid, delaySeconds: 0, status };
        });
        setEditingFunctions(functions);
        setEditingName(sceneName);
        setEditingSceneId(sceneId);
        // 场景级配置（详情可能不返回，使用默认值）
        setEditingCollect(false);
        setEditingDelay(0);
        setEditingExecutePush(false);
      } else {
        showToast('error', res.message || '获取场景详情失败');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      showToast('error', `获取详情失败：${msg}`);
    } finally {
      setActionLoadingId(undefined);
    }
  };

  /** 提交编辑：调用 updateScene API 更新场景 */
  const handleEditSubmit = async () => {
    if (!editingSceneId || !editingName.trim()) return;
    setActionLoadingId(editingSceneId);
    try {
      const res = await updateScene({
        sceneId: editingSceneId,
        sceneName: editingName.trim(),
        functions: editingFunctions,
        collect: editingCollect,
        delaySeconds: editingDelay,
        executePush: editingExecutePush,
      });
      if (res.success) {
        // 更新本地列表中的场景名
        setScenes(prev => prev.map(item =>
          getSceneId(item) === editingSceneId
            ? { ...item, sceneName: editingName.trim() }
            : item
        ));
        showToast('success', `场景「${editingName.trim()}」已更新`);
        setEditingSceneId(undefined);
      } else {
        showToast('error', res.message || '更新场景失败');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      showToast('error', `更新失败：${msg}`);
    } finally {
      setActionLoadingId(undefined);
    }
  };

  /** 取消编辑 */
  const handleEditCancel = () => {
    setEditingSceneId(undefined);
    setEditingName('');
    setEditingFunctions([]);
  };

  // 内联展示场景详情时，直接渲染 SceneDetailPage
  if (inlineDetail) {
    return (
      <div className="animate-fade-in space-y-3">
        <button
          onClick={() => setInlineDetail(null)}
          className="shimmer-btn w-full rounded-lg glass-panel border border-neon-purple/30 px-3 py-2 text-sm font-medium text-neon-cyan hover-neon transition-all flex items-center justify-center gap-1.5"
        >
          <ChevronLeft size={14} />
          返回场景列表
        </button>
        <SceneDetailPage data={inlineDetail} />
      </div>
    );
  }

  return (
    <div className="animate-fade-in space-y-4">
      {/* 标题栏 */}
      <div className="flex items-center gap-2 text-sm">
        <Home size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">场景列表</span>
        <span className="text-xs text-slate-400 font-mono">共 {total} 个</span>
      </div>

      {loadingList ? (
        <div className="glass rounded-xl px-4 py-8 text-center text-sm text-cyan-300 flex items-center justify-center gap-2">
          <RefreshCw size={14} className="animate-spin" />
          正在查询场景列表...
        </div>
      ) : scenes.length === 0 ? (
        <div className="glass rounded-xl px-4 py-8 text-center text-sm text-slate-400">
          暂无场景
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {pageItems.map((scene, i) => {
            const sceneId = getSceneId(scene);
            const sceneName = getSceneName(scene);
            // 场景类型：1=影音（Film 图标），0=普通（Home 图标）
            const isVideoScene = Number(scene.sceneType) === 1;
            const TypeIcon = isVideoScene ? Film : Home;
            const typeLabel = isVideoScene ? '影音' : '普通';
            const isCollected = !!scene.collect;
            const roomNames = Array.isArray(scene.roomNames) ? scene.roomNames : [];
            const isActionLoading = actionLoadingId === sceneId;
            const isDetailLoading = loadingDetailId === sceneId;

            return (
              <div
                key={sceneId || (start + i)}
                className="glass rounded-xl p-3 hover-neon card-shadow border border-neon-purple/15 animate-card-in transition-all"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                {/* 头部：类型图标 + 场景名 + 类型标签 + 收藏标记 */}
                <div className="flex items-center gap-2 mb-2">
                  <div className="rounded-lg bg-neon-purple/10 p-1.5 text-neon-purple flex-shrink-0 border border-neon-purple/20">
                    <TypeIcon size={16} />
                  </div>
                  <h5 className="text-sm font-semibold text-slate-100 truncate flex-1">
                    {sceneName}
                  </h5>
                  <span className="rounded-full glass-panel px-2 py-0.5 text-[10px] text-neon-cyan border border-neon-cyan/30 font-mono flex-shrink-0">
                    {typeLabel}
                  </span>
                  {isCollected && (
                    <span className="flex items-center gap-0.5 text-[10px] text-neon-amber bg-neon-amber/10 border border-neon-amber/30 px-1.5 py-0.5 rounded font-mono flex-shrink-0">
                      <Star size={10} className="fill-neon-amber text-neon-amber" />
                      收藏
                    </span>
                  )}
                </div>

                {/* 房间名称标签 */}
                {roomNames.length > 0 && (
                  <div className="mb-2 flex flex-wrap gap-1.5">
                    {roomNames.map((room, idx) => (
                      <span
                        key={idx}
                        className="rounded bg-slate-700/40 px-1.5 py-0.5 text-[10px] text-slate-300 border border-slate-600/30 font-mono"
                      >
                        {room}
                      </span>
                    ))}
                  </div>
                )}

                {/* 操作按钮 */}
                <div className="flex gap-1.5">
                  <button
                    onClick={() => handleExecute(scene)}
                    disabled={isActionLoading || !sceneId}
                    className="flex-1 rounded-lg bg-gradient-to-r from-neon-purple to-neon-violet px-2 py-1.5 text-xs font-medium text-white shadow-neon-purple transition-all flex items-center justify-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed hover:shadow-glow-md"
                  >
                    {isActionLoading ? <RefreshCw size={12} className="animate-spin" /> : <Play size={12} />}
                    执行
                  </button>
                  <button
                    onClick={() => handleDetail(scene)}
                    disabled={isDetailLoading || !sceneId}
                    className="flex-1 rounded-lg glass-panel border border-neon-cyan/30 px-2 py-1.5 text-xs font-medium text-neon-cyan hover-neon transition-all flex items-center justify-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {isDetailLoading ? <RefreshCw size={12} className="animate-spin" /> : <Info size={12} />}
                    详情
                  </button>
                  <button
                    onClick={() => handleEdit(scene)}
                    disabled={isActionLoading || !sceneId}
                    title="编辑"
                    className="w-8 flex items-center justify-center rounded-lg glass-panel border border-neon-amber/30 text-neon-amber hover:bg-neon-amber/10 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {isActionLoading && actionLoadingId === sceneId ? <RefreshCw size={14} className="animate-spin" /> : <Pencil size={14} />}
                  </button>
                  <button
                    onClick={() => handleDelete(scene)}
                    disabled={isActionLoading || !sceneId}
                    title="删除"
                    className="w-8 flex items-center justify-center rounded-lg glass-panel border border-neon-pink/30 text-neon-pink hover:bg-neon-pink/10 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>

                {/* 内联编辑面板（点击编辑后展开） */}
                {editingSceneId === sceneId && (
                  <div className="mt-2.5 rounded-lg glass-panel border border-neon-amber/30 p-3 space-y-2 animate-fade-in">
                    <div className="text-[11px] text-neon-amber font-mono flex items-center gap-1">
                      <Pencil size={11} />
                      编辑场景
                    </div>
                    {/* 场景名称编辑 */}
                    <div>
                      <label className="text-[10px] text-slate-400 font-mono">场景名称</label>
                      <input
                        type="text"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        className="w-full mt-1 rounded-md glass-panel border border-neon-purple/20 px-2 py-1.5 text-sm text-slate-100 focus:outline-none focus:border-neon-amber/50 font-mono"
                        placeholder="输入场景名称"
                      />
                    </div>
                    {/* 已配置的设备动作数量 */}
                    <div className="text-[11px] text-slate-400 font-mono">
                      已配置 {editingFunctions.length} 个设备动作
                    </div>
                    {/* 保存 / 取消 */}
                    <div className="flex gap-1.5 pt-1">
                      <button
                        onClick={handleEditSubmit}
                        disabled={isActionLoading || !editingName.trim()}
                        className="flex-1 rounded-lg bg-gradient-to-r from-neon-amber to-neon-orange px-2 py-1.5 text-xs font-medium text-white transition-all flex items-center justify-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        {isActionLoading ? <RefreshCw size={12} className="animate-spin" /> : <CheckCircle2 size={12} />}
                        保存
                      </button>
                      <button
                        onClick={handleEditCancel}
                        disabled={isActionLoading}
                        className="flex-1 rounded-lg glass-panel border border-slate-500/30 px-2 py-1.5 text-xs font-medium text-slate-300 hover:bg-slate-700/20 transition-colors disabled:opacity-40"
                      >
                        取消
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 分页控件（仅多于一页时展示） */}
      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        onPageChange={goToPage}
      />

      {/* 底部提示 */}
      <p className="text-xs text-slate-500 text-center">
        点击按钮执行 / 查看场景
      </p>

      {/* Toast 提示（顶部居中，淡入淡出） */}
      <AnimatePresence>
        {toast && (
          <motion.div
            initial={{ opacity: 0, y: -20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.95 }}
            transition={{ duration: 0.25, ease: 'easeOut' }}
            className="fixed top-6 left-1/2 -translate-x-1/2 z-50 glass-strong rounded-lg px-4 py-2.5 card-shadow flex items-center gap-2 border"
            style={{
              borderColor: toast.type === 'success'
                ? 'rgba(57,255,20,0.4)'
                : toast.type === 'error'
                ? 'rgba(236,72,153,0.4)'
                : 'rgba(34,255,247,0.4)',
            }}
          >
            {toast.type === 'success' ? (
              <CheckCircle2 size={16} className="text-neon-green" />
            ) : toast.type === 'error' ? (
              <XCircle size={16} className="text-neon-pink" />
            ) : (
              <Info size={16} className="text-neon-cyan" />
            )}
            <span className="text-sm text-slate-100">{toast.message}</span>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
