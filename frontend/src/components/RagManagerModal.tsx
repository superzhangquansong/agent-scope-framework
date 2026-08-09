/**
 * RAG 知识库管理弹窗组件
 *
 * <p>独立的弹窗组件，用于维护 RAG 知识库资料，包含 3 个功能区：</p>
 * <ul>
 *   <li>文档列表区（顶部）：卡片式展示已上传文档，支持删除；点击卡片可展开查看分段列表（含摘要）</li>
 *   <li>上传区（中间）：支持多格式文件上传，带进度条；支持「高级配置」面板配置分段大小/重叠/摘要生成</li>
 *   <li>检索测试区（底部）：输入问题检索知识库，展示相似度片段；支持「检索设置」面板配置检索模式/权重/重排序</li>
 * </ul>
 *
 * <p>配色与 SettingsModal 保持一致（毛玻璃 + 霓虹色），交互反馈包括 loading / toast。</p>
 */
import { useState, useEffect, useCallback, useRef } from 'react';
import {
  X, Upload, Search, Trash2, FileText, FileSpreadsheet, FileCode,
  File, RefreshCw, Database, CheckCircle, AlertCircle, Sparkles,
  ChevronDown, ChevronRight, Sliders, Layers, Tag,
} from 'lucide-react';
import {
  getRagList, deleteRagDocument, getRagChunks,
  uploadRagDocumentWithConfig, hybridSearchRag,
  type RagDocument, type RagSearchResult, type RagChunk,
} from '../api/client';

/** RagManagerModal 组件 Props */
interface RagManagerModalProps {
  /** 弹窗是否打开 */
  open: boolean;
  /** 关闭弹窗回调 */
  onClose: () => void;
}

/** 支持上传的文件格式 */
const ACCEPTED_FORMATS = '.txt,.md,.pdf,.docx,.doc,.xlsx,.xls';

/** 摘要模型可选项 */
const SUMMARY_MODELS = ['qwen-plus', 'qwen-max', 'qwen-turbo'] as const;

/** 重排序模型可选项 */
const RERANK_MODELS = ['qwen-plus', 'qwen-max'] as const;

/** 检索模式类型 */
type SearchMode = 'vector' | 'fulltext' | 'hybrid';

/** 检索模式配置（用于渲染单选按钮组） */
const SEARCH_MODES: { value: SearchMode; label: string; tag: string }[] = [
  { value: 'vector', label: '向量检索', tag: '向量' },
  { value: 'fulltext', label: '全文检索', tag: '全文' },
  { value: 'hybrid', label: '混合检索', tag: '混合+重排序' },
];

/**
 * 根据文件名获取对应的图标和颜色。
 *
 * @param fileName 文件名（含扩展名）
 * @returns 图标组件和 Tailwind 颜色类名
 */
function getFileIcon(fileName: string): { Icon: typeof FileText; color: string } {
  const ext = fileName.split('.').pop()?.toLowerCase() || '';
  if (ext === 'pdf') return { Icon: FileText, color: 'text-red-400' };
  if (ext === 'doc' || ext === 'docx') return { Icon: FileText, color: 'text-blue-400' };
  if (ext === 'xls' || ext === 'xlsx') return { Icon: FileSpreadsheet, color: 'text-green-400' };
  if (ext === 'md') return { Icon: FileCode, color: 'text-purple-400' };
  if (ext === 'txt') return { Icon: File, color: 'text-slate-300' };
  return { Icon: FileText, color: 'text-slate-400' };
}

/**
 * 获取文档状态标签的显示文本和样式。
 *
 * @param status 文档状态（VECTORIZED / KEYWORD_ONLY / FAILED）
 * @returns 标签文本和 Tailwind 样式类名
 */
function getStatusTag(status?: string): { text: string; className: string } {
  switch (status) {
    case 'VECTORIZED':
      return { text: '已向量化', className: 'bg-green-900/40 text-green-300 border-green-500/40' };
    case 'KEYWORD_ONLY':
      return { text: '仅关键词', className: 'bg-orange-900/40 text-orange-300 border-orange-500/40' };
    case 'FAILED':
      return { text: '失败', className: 'bg-red-900/40 text-red-300 border-red-500/40' };
    default:
      return { text: '未知', className: 'bg-slate-800/40 text-slate-400 border-slate-600/40' };
  }
}

/**
 * 获取摘要状态标签的显示文本和样式。
 *
 * @param hasSummary 是否已生成摘要
 * @returns 标签文本和 Tailwind 样式类名
 */
function getSummaryTag(hasSummary?: boolean): { text: string; className: string } {
  return hasSummary
    ? { text: '已生成摘要', className: 'bg-cyan-900/40 text-cyan-300 border-cyan-500/40' }
    : { text: '无摘要', className: 'bg-slate-800/40 text-slate-400 border-slate-600/40' };
}

/**
 * 格式化文件大小（字节 → KB/MB）。
 *
 * @param bytes 文件大小（字节）
 * @returns 格式化后的字符串
 */
function formatFileSize(bytes?: number): string {
  if (!bytes) return '-';
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)}MB`;
}

/**
 * 格式化时间戳（毫秒 → YYYY-MM-DD HH:mm）。
 *
 * @param ts 毫秒时间戳
 * @returns 格式化后的时间字符串
 */
function formatTime(ts?: number): string {
  if (!ts) return '-';
  const d = new Date(ts);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * 根据相似度分数获取进度条颜色。
 *
 * @param score 相似度分数（0-1）
 * @returns Tailwind 背景色类名
 */
function getScoreBarColor(score: number): string {
  if (score >= 0.7) return 'bg-green-400';
  if (score >= 0.4) return 'bg-orange-400';
  return 'bg-slate-500';
}

/**
 * 截取内容前 N 个字符作为预览。
 *
 * @param text 原始文本
 * @param maxLen 最大长度（默认 100）
 * @returns 截取后的预览文本（超出时追加省略号）
 */
function truncatePreview(text: string, maxLen = 100): string {
  if (!text) return '';
  return text.length > maxLen ? `${text.slice(0, maxLen)}...` : text;
}

/**
 * RAG 知识库管理弹窗组件
 *
 * @param props 组件属性
 */
export default function RagManagerModal({ open, onClose }: RagManagerModalProps) {
  /** ===== 文档列表状态 ===== */
  /** 已上传文档列表 */
  const [docList, setDocList] = useState<RagDocument[]>([]);
  /** 列表加载中 */
  const [listLoading, setListLoading] = useState(false);
  /** 当前确认删除的文档 docCode（null=未在确认状态） */
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  /** 正在删除中的文档 docCode */
  const [deleting, setDeleting] = useState<string | null>(null);
  /** 当前展开分段列表的文档 docCode（null=全部收起） */
  const [expandedDoc, setExpandedDoc] = useState<string | null>(null);
  /** 分段缓存（docCode -> chunks），避免重复请求 */
  const [chunksCache, setChunksCache] = useState<Record<string, RagChunk[]>>({});
  /** 正在加载分段的文档 docCode */
  const [chunksLoading, setChunksLoading] = useState<string | null>(null);

  /** ===== 上传状态 ===== */
  /** 是否正在上传 */
  const [uploading, setUploading] = useState(false);
  /** 上传进度（0-100） */
  const [uploadProgress, setUploadProgress] = useState(0);
  /** 隐藏的 file input 引用 */
  const fileInputRef = useRef<HTMLInputElement>(null);

  /** ===== 上传高级配置 ===== */
  /** 是否展开「高级配置」面板 */
  const [showAdvancedUpload, setShowAdvancedUpload] = useState(false);
  /** 分段大小（字符，100-2000） */
  const [chunkSize, setChunkSize] = useState(500);
  /** 分段重叠（字符，0-200） */
  const [chunkOverlap, setChunkOverlap] = useState(50);
  /** 是否自动生成摘要 */
  const [autoSummary, setAutoSummary] = useState(true);
  /** 摘要模型 */
  const [summaryModel, setSummaryModel] = useState<string>('qwen-plus');

  /** ===== 检索测试状态 ===== */
  /** 检索输入框文本 */
  const [searchQuery, setSearchQuery] = useState('');
  /** 是否正在检索 */
  const [searching, setSearching] = useState(false);
  /** 检索结果列表 */
  const [searchResults, setSearchResults] = useState<RagSearchResult[]>([]);
  /** 是否已执行过检索（用于区分"未搜索"和"无结果"） */
  const [hasSearched, setHasSearched] = useState(false);
  /** 当前检索模式（用于结果标签展示） */
  const [searchMode, setSearchMode] = useState<SearchMode>('hybrid');

  /** ===== 检索配置 ===== */
  /** 是否展开「检索设置」面板 */
  const [showRetrievalConfig, setShowRetrievalConfig] = useState(false);
  /** 向量权重（0-1，仅混合检索模式显示） */
  const [vectorWeight, setVectorWeight] = useState(0.7);
  /** 全文权重（0-1，仅混合检索模式显示） */
  const [fulltextWeight, setFulltextWeight] = useState(0.3);
  /** 是否启用重排序 */
  const [rerank, setRerank] = useState(true);
  /** 重排序模型 */
  const [rerankModel, setRerankModel] = useState<string>('qwen-plus');

  /** ===== Toast 提示 ===== */
  const [toast, setToast] = useState<{ msg: string; type: 'success' | 'error' } | null>(null);

  /** 显示提示消息（3 秒后自动消失） */
  const showToast = (msg: string, type: 'success' | 'error') => {
    setToast({ msg, type });
    setTimeout(() => setToast(null), 3000);
  };

  /** 加载文档列表 */
  const loadDocList = useCallback(async () => {
    setListLoading(true);
    try {
      const r = await getRagList();
      if (r.success && r.data) {
        setDocList(r.data);
      } else {
        showToast(r.message || '文档列表加载失败', 'error');
      }
    } catch (e) {
      console.warn('[rag] 文档列表加载失败', e);
      showToast('文档列表加载失败', 'error');
    } finally {
      setListLoading(false);
    }
  }, []);

  /** 弹窗打开时加载文档列表 */
  useEffect(() => {
    if (open) {
      loadDocList();
    }
  }, [open, loadDocList]);

  /**
   * 切换文档卡片展开/收起状态，展开时按需加载分段列表。
   *
   * @param docCode 文档编码
   */
  const handleToggleChunks = async (docCode: string) => {
    if (expandedDoc === docCode) {
      setExpandedDoc(null);
      return;
    }
    setExpandedDoc(docCode);
    // 已有缓存，直接返回
    if (chunksCache[docCode]) return;
    setChunksLoading(docCode);
    try {
      const chunks = await getRagChunks(docCode);
      setChunksCache((prev) => ({ ...prev, [docCode]: chunks }));
    } catch (e) {
      console.warn('[rag] 分段列表加载失败', e);
      showToast('分段列表加载失败', 'error');
    } finally {
      setChunksLoading(null);
    }
  };

  /** 处理文件上传（携带高级配置参数） */
  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // 校验分段配置范围
    const safeChunkSize = Math.min(2000, Math.max(100, chunkSize));
    const safeChunkOverlap = Math.min(200, Math.max(0, chunkOverlap));

    setUploading(true);
    setUploadProgress(0);
    try {
      const r = await uploadRagDocumentWithConfig(
        file,
        {
          chunkSize: safeChunkSize,
          chunkOverlap: safeChunkOverlap,
          autoSummary,
          summaryModel,
        },
        (percent) => setUploadProgress(percent),
      );
      if (r.success) {
        showToast(`文档「${file.name}」上传成功`, 'success');
        // 清空分段缓存（可能有过期数据）
        setChunksCache({});
        loadDocList();
      } else {
        showToast(r.message || '上传失败', 'error');
      }
    } catch (e) {
      console.error('[rag] 上传失败', e);
      showToast(e instanceof Error ? e.message : '上传失败', 'error');
    } finally {
      setUploading(false);
      setUploadProgress(0);
      // 重置 file input，允许重复选择同一文件
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  /** 处理文档删除 */
  const handleDelete = async (docCode: string) => {
    setDeleting(docCode);
    try {
      const r = await deleteRagDocument(docCode);
      if (r.success) {
        showToast('文档已删除', 'success');
        // 清理分段缓存中对应文档的数据
        setChunksCache((prev) => {
          const next = { ...prev };
          delete next[docCode];
          return next;
        });
        // 如果删除的是当前展开的文档，收起展开状态
        if (expandedDoc === docCode) setExpandedDoc(null);
        loadDocList();
      } else {
        showToast(r.message || '删除失败', 'error');
      }
    } catch (e) {
      console.error('[rag] 删除失败', e);
      showToast('删除失败', 'error');
    } finally {
      setDeleting(null);
      setConfirmDelete(null);
    }
  };

  /** 处理检索（使用混合检索 API，根据 mode 切换模式） */
  const handleSearch = async () => {
    const q = searchQuery.trim();
    if (!q || searching) return;

    setSearching(true);
    setHasSearched(true);
    setSearchMode(searchMode);
    try {
      const results = await hybridSearchRag(
        q,
        {
          mode: searchMode,
          vectorWeight,
          fulltextWeight,
          rerank,
          rerankModel,
        },
        5,
      );
      setSearchResults(results);
    } catch (e) {
      console.error('[rag] 检索失败', e);
      showToast('检索失败', 'error');
    } finally {
      setSearching(false);
    }
  };

  /** 检索输入框 Enter 键触发搜索 */
  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSearch();
    }
  };

  if (!open) return null;

  /** 当前检索模式对应的标签文本 */
  const currentModeTag = SEARCH_MODES.find((m) => m.value === searchMode)?.tag || '混合+重排序';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm animate-fade-in"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-3xl max-h-[90vh] overflow-hidden rounded-2xl glass-strong border border-neon-purple/30 shadow-glow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ===== 顶部标题栏 ===== */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-neon-purple/20">
          <h2 className="text-base font-semibold gradient-text flex items-center gap-2">
            <Database size={16} className="text-neon-purple" />
            RAG 知识库管理
          </h2>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-neon-pink transition-colors"
            title="关闭"
          >
            <X size={18} />
          </button>
        </div>

        {/* ===== 内容区（3 个功能区）===== */}
        <div className="overflow-y-auto max-h-[calc(90vh-110px)] px-5 py-4 space-y-4">

          {/* ===== 1. 文档列表区（顶部）===== */}
          <section className="rounded-xl glass-panel p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-medium text-slate-200 flex items-center gap-1.5">
                <FileText size={14} className="text-neon-cyan" />
                文档列表
                {docList.length > 0 && (
                  <span className="text-[10px] text-slate-500 font-mono">（共 {docList.length} 个）</span>
                )}
              </h3>
              <button
                onClick={loadDocList}
                disabled={listLoading}
                className="flex items-center gap-1 rounded-md px-2 py-1 text-[11px] text-slate-400 border border-slate-600/40 hover:bg-slate-800/40 transition-all disabled:opacity-40"
                title="刷新列表"
              >
                <RefreshCw size={10} className={listLoading ? 'animate-spin' : ''} />
                刷新
              </button>
            </div>

            {/* 列表内容 */}
            {listLoading ? (
              <div className="flex items-center justify-center py-6 text-slate-400">
                <RefreshCw size={14} className="animate-spin text-neon-purple mr-2" />
                加载中...
              </div>
            ) : docList.length > 0 ? (
              <div className="space-y-2">
                {docList.map((doc) => {
                  const { Icon, color } = getFileIcon(doc.docName);
                  const statusTag = getStatusTag(doc.status);
                  const summaryTag = getSummaryTag(doc.hasSummary);
                  const isConfirming = confirmDelete === doc.docCode;
                  const isDeleting = deleting === doc.docCode;
                  const isExpanded = expandedDoc === doc.docCode;
                  const chunks = chunksCache[doc.docCode];
                  const isLoadingChunks = chunksLoading === doc.docCode;
                  return (
                    <div
                      key={doc.docCode}
                      className="rounded-md bg-slate-900/40 border border-slate-700/40 hover:border-slate-600/60 transition-all overflow-hidden"
                    >
                      {/* 文档卡片主体（可点击展开分段） */}
                      <div
                        className="flex items-center gap-2.5 px-3 py-2.5 cursor-pointer"
                        onClick={() => handleToggleChunks(doc.docCode)}
                      >
                        {/* 展开/收起箭头 */}
                        <div className="flex-shrink-0 text-slate-500">
                          {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                        </div>
                        {/* 文件类型图标 */}
                        <div className="flex-shrink-0">
                          <Icon size={16} className={color} />
                        </div>
                        {/* 文档信息 */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span className="text-xs text-slate-200 truncate font-medium">{doc.docName}</span>
                            {/* 状态标签 */}
                            <span className={`text-[10px] px-1.5 py-0.5 rounded border font-mono flex-shrink-0 ${statusTag.className}`}>
                              {statusTag.text}
                            </span>
                            {/* 摘要状态标签 */}
                            <span className={`text-[10px] px-1.5 py-0.5 rounded border flex items-center gap-0.5 flex-shrink-0 ${summaryTag.className}`}>
                              <Sparkles size={9} />
                              {summaryTag.text}
                            </span>
                          </div>
                          <div className="flex items-center gap-2 mt-0.5 text-[10px] text-slate-500 font-mono">
                            <span>{formatFileSize(doc.fileSize)}</span>
                            {doc.chunkCount != null && (
                              <span className="flex items-center gap-0.5">
                                · <Layers size={9} /> {doc.chunkCount} 块
                              </span>
                            )}
                            <span>· {formatTime(doc.createTime)}</span>
                          </div>
                        </div>
                        {/* 删除按钮 / 确认操作 */}
                        {isConfirming ? (
                          <div
                            className="flex items-center gap-1 flex-shrink-0"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <button
                              onClick={() => handleDelete(doc.docCode)}
                              disabled={isDeleting}
                              className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-red-300 bg-red-900/30 border border-red-500/50 hover:bg-red-900/50 transition-all disabled:opacity-40"
                              title="确认删除"
                            >
                              {isDeleting ? <RefreshCw size={10} className="animate-spin" /> : <CheckCircle size={10} />}
                              确认
                            </button>
                            <button
                              onClick={() => setConfirmDelete(null)}
                              disabled={isDeleting}
                              className="rounded-md px-2 py-1 text-[10px] text-slate-400 border border-slate-600/40 hover:bg-slate-800/40 transition-all disabled:opacity-40"
                              title="取消"
                            >
                              取消
                            </button>
                          </div>
                        ) : (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setConfirmDelete(doc.docCode);
                            }}
                            className="flex items-center gap-0.5 rounded-md px-2 py-1 text-[10px] text-red-300 border border-red-500/40 hover:bg-red-900/30 transition-all flex-shrink-0"
                            title="删除"
                          >
                            <Trash2 size={10} />
                            删除
                          </button>
                        )}
                      </div>

                      {/* 分段列表（展开时显示） */}
                      {isExpanded && (
                        <div className="border-t border-slate-700/40 bg-black/20 px-3 py-2.5">
                          {isLoadingChunks ? (
                            <div className="flex items-center justify-center py-4 text-slate-400">
                              <RefreshCw size={11} className="animate-spin text-neon-purple mr-2" />
                              加载分段中...
                            </div>
                          ) : chunks && chunks.length > 0 ? (
                            <div className="space-y-1.5">
                              <div className="flex items-center gap-1.5 text-[10px] text-slate-500 mb-1.5">
                                <Layers size={10} className="text-neon-cyan" />
                                <span className="font-mono">{chunks.length} 个分段</span>
                              </div>
                              {chunks.map((chunk) => (
                                <div
                                  key={chunk.id}
                                  className="rounded bg-slate-900/60 border border-slate-700/50 px-2.5 py-2"
                                >
                                  {/* 分段头部：序号 + 文件名标签 */}
                                  <div className="flex items-center gap-1.5 mb-1 flex-wrap">
                                    <span className="text-[10px] font-mono text-neon-cyan flex-shrink-0">
                                      #{chunk.chunkIndex}
                                    </span>
                                    {chunk.docName && (
                                      <span className="flex items-center gap-0.5 text-[9px] px-1.5 py-0.5 rounded border border-slate-600/50 bg-slate-800/60 text-slate-400 font-mono flex-shrink-0">
                                        <Tag size={8} />
                                        {chunk.docName}
                                      </span>
                                    )}
                                    {chunk.summary && (
                                      <span className="flex items-center gap-0.5 text-[9px] px-1.5 py-0.5 rounded border border-cyan-500/40 bg-cyan-900/30 text-cyan-300 flex-shrink-0">
                                        <Sparkles size={8} />
                                        有摘要
                                      </span>
                                    )}
                                  </div>
                                  {/* 内容预览 */}
                                  <p className="text-[11px] text-slate-400 leading-relaxed">
                                    {truncatePreview(chunk.content, 100)}
                                  </p>
                                  {/* 摘要（如有） */}
                                  {chunk.summary && (
                                    <div className="mt-1.5 pt-1.5 border-t border-slate-700/40">
                                      <div className="flex items-center gap-0.5 text-[9px] text-cyan-400 mb-0.5">
                                        <Sparkles size={9} />
                                        摘要
                                      </div>
                                      <p className="text-[10px] text-slate-500 leading-relaxed">
                                        {chunk.summary}
                                      </p>
                                    </div>
                                  )}
                                  {/* 标签（逗号分隔渲染为芯片） */}
                                  {chunk.tags && (
                                    <div className="mt-1.5 flex flex-wrap gap-1">
                                      {chunk.tags.split(',').filter(t => t.trim() && !t.startsWith('chunk_')).map((tag, idx) => (
                                        <span key={idx} className="text-[9px] px-1.5 py-0.5 rounded bg-purple-900/30 border border-purple-500/30 text-purple-300 flex items-center gap-0.5">
                                          <Tag size={8} />
                                          {tag.trim()}
                                        </span>
                                      ))}
                                    </div>
                                  )}
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div className="flex items-center justify-center py-4 text-slate-500 text-[11px]">
                              <Layers size={14} className="text-slate-600 mr-1.5" />
                              暂无分段数据
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-6 text-slate-500">
                <File size={24} className="text-slate-600 mb-2" />
                <p className="text-xs">暂无文档，请上传资料到知识库</p>
              </div>
            )}
          </section>

          {/* ===== 2. 上传区（中间）===== */}
          <section className="rounded-xl glass-panel p-4">
            <h3 className="text-sm font-medium text-slate-200 mb-3 flex items-center gap-1.5">
              <Upload size={14} className="text-neon-purple" />
              上传文档
            </h3>

            {/* 隐藏的 file input */}
            <input
              ref={fileInputRef}
              type="file"
              accept={ACCEPTED_FORMATS}
              onChange={handleUpload}
              className="hidden"
            />

            {/* 高级配置折叠面板 */}
            <div className="mb-3 rounded-lg border border-slate-700/40 overflow-hidden">
              <button
                type="button"
                onClick={() => setShowAdvancedUpload((v) => !v)}
                className="w-full flex items-center justify-between px-3 py-2 text-[11px] text-slate-300 bg-slate-900/40 hover:bg-slate-800/40 transition-all"
              >
                <span className="flex items-center gap-1.5">
                  <Sliders size={12} className="text-neon-purple" />
                  高级配置
                  {(chunkSize !== 500 || chunkOverlap !== 50 || !autoSummary || summaryModel !== 'qwen-plus') && (
                    <span className="text-[9px] px-1 py-0.5 rounded bg-neon-purple/20 text-neon-purple border border-neon-purple/40 font-mono">
                      已自定义
                    </span>
                  )}
                </span>
                <ChevronDown
                  size={12}
                  className={`text-slate-500 transition-transform ${showAdvancedUpload ? 'rotate-180' : ''}`}
                />
              </button>
              {showAdvancedUpload && (
                <div className="px-3 py-3 space-y-3 bg-black/20">
                  {/* 分段大小 + 分段重叠（并排） */}
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="flex items-center gap-1 text-[10px] text-slate-400 mb-1">
                        <Layers size={10} className="text-neon-cyan" />
                        分段大小
                        <span className="text-slate-600">（字符）</span>
                      </label>
                      <input
                        type="number"
                        min={100}
                        max={2000}
                        value={chunkSize}
                        onChange={(e) => setChunkSize(Number(e.target.value) || 500)}
                        className="w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2 py-1.5 text-[11px] text-slate-200 font-mono outline-none focus:border-neon-cyan/50"
                      />
                      <p className="text-[9px] text-slate-600 mt-0.5">范围 100-2000</p>
                    </div>
                    <div>
                      <label className="flex items-center gap-1 text-[10px] text-slate-400 mb-1">
                        <RefreshCw size={10} className="text-neon-cyan" />
                        分段重叠
                        <span className="text-slate-600">（字符）</span>
                      </label>
                      <input
                        type="number"
                        min={0}
                        max={200}
                        value={chunkOverlap}
                        onChange={(e) => setChunkOverlap(Number(e.target.value) || 0)}
                        className="w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2 py-1.5 text-[11px] text-slate-200 font-mono outline-none focus:border-neon-cyan/50"
                      />
                      <p className="text-[9px] text-slate-600 mt-0.5">范围 0-200</p>
                    </div>
                  </div>

                  {/* 摘要生成开关 */}
                  <div className="flex items-center justify-between">
                    <label className="flex items-center gap-1.5 text-[11px] text-slate-200">
                      <Sparkles size={11} className="text-neon-purple" />
                      摘要生成
                      <span className="text-[10px] text-slate-500">（上传时自动为每个分段生成摘要）</span>
                    </label>
                    <button
                      type="button"
                      onClick={() => setAutoSummary((v) => !v)}
                      className={`relative inline-flex h-4 w-7 items-center rounded-full transition-colors ${
                        autoSummary ? 'bg-neon-purple/70' : 'bg-slate-700'
                      }`}
                    >
                      <span
                        className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${
                          autoSummary ? 'translate-x-3.5' : 'translate-x-0.5'
                        }`}
                      />
                    </button>
                  </div>

                  {/* 摘要模型（仅启用摘要生成时显示） */}
                  {autoSummary && (
                    <div>
                      <label className="flex items-center gap-1 text-[10px] text-slate-400 mb-1">
                        <FileCode size={10} className="text-neon-cyan" />
                        摘要模型
                      </label>
                      <select
                        value={summaryModel}
                        onChange={(e) => setSummaryModel(e.target.value)}
                        className="w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2 py-1.5 text-[11px] text-slate-200 font-mono outline-none focus:border-neon-cyan/50"
                      >
                        {SUMMARY_MODELS.map((m) => (
                          <option key={m} value={m} className="bg-slate-900">
                            {m}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* 上传按钮 */}
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="w-full flex items-center justify-center gap-2 rounded-lg bg-gradient-to-br from-neon-purple/80 to-neon-cyan/60 px-4 py-3 text-sm text-white font-medium hover:shadow-glow-md transition-all disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {uploading ? (
                <>
                  <RefreshCw size={14} className="animate-spin" />
                  上传中... {uploadProgress}%
                </>
              ) : (
                <>
                  <Upload size={14} />
                  选择文件上传
                </>
              )}
            </button>

            {/* 上传进度条 */}
            {uploading && (
              <div className="mt-2.5">
                <div className="h-1.5 rounded-full bg-slate-800/60 overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-neon-purple to-neon-cyan rounded-full transition-all duration-300"
                    style={{ width: `${uploadProgress}%` }}
                  />
                </div>
              </div>
            )}

            {/* 支持格式提示 */}
            <p className="mt-2 text-[10px] text-slate-500 font-mono text-center">
              支持 .txt .md .pdf .docx .doc .xlsx .xls 格式
            </p>
          </section>

          {/* ===== 3. 检索测试区（底部）===== */}
          <section className="rounded-xl glass-panel p-4">
            <h3 className="text-sm font-medium text-slate-200 mb-3 flex items-center gap-1.5">
              <Search size={14} className="text-neon-cyan" />
              检索测试
            </h3>

            {/* 检索设置折叠面板 */}
            <div className="mb-3 rounded-lg border border-slate-700/40 overflow-hidden">
              <button
                type="button"
                onClick={() => setShowRetrievalConfig((v) => !v)}
                className="w-full flex items-center justify-between px-3 py-2 text-[11px] text-slate-300 bg-slate-900/40 hover:bg-slate-800/40 transition-all"
              >
                <span className="flex items-center gap-1.5">
                  <Sliders size={12} className="text-neon-cyan" />
                  检索设置
                  <span className="text-[9px] px-1 py-0.5 rounded bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/40 font-mono">
                    {SEARCH_MODES.find((m) => m.value === searchMode)?.label}
                  </span>
                </span>
                <ChevronDown
                  size={12}
                  className={`text-slate-500 transition-transform ${showRetrievalConfig ? 'rotate-180' : ''}`}
                />
              </button>
              {showRetrievalConfig && (
                <div className="px-3 py-3 space-y-3 bg-black/20">
                  {/* 检索模式单选按钮组 */}
                  <div>
                    <label className="flex items-center gap-1 text-[10px] text-slate-400 mb-1.5">
                      <Search size={10} className="text-neon-cyan" />
                      检索模式
                    </label>
                    <div className="grid grid-cols-3 gap-1.5">
                      {SEARCH_MODES.map((m) => (
                        <button
                          key={m.value}
                          type="button"
                          onClick={() => setSearchMode(m.value)}
                          className={`rounded-md px-2 py-1.5 text-[10px] font-medium transition-all border ${
                            searchMode === m.value
                              ? 'bg-neon-cyan/20 text-neon-cyan border-neon-cyan/50'
                              : 'bg-slate-900/40 text-slate-400 border-slate-700/50 hover:bg-slate-800/40'
                          }`}
                        >
                          {m.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 向量权重 + 全文权重滑块（仅混合检索模式显示） */}
                  {searchMode === 'hybrid' && (
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="flex items-center justify-between text-[10px] text-slate-400 mb-1">
                          <span className="flex items-center gap-1">
                            <Sliders size={10} className="text-neon-purple" />
                            向量权重
                          </span>
                          <span className="font-mono text-neon-purple">{vectorWeight.toFixed(2)}</span>
                        </label>
                        <input
                          type="range"
                          min={0}
                          max={1}
                          step={0.05}
                          value={vectorWeight}
                          onChange={(e) => setVectorWeight(Number(e.target.value))}
                          className="w-full h-1 accent-neon-purple cursor-pointer"
                        />
                      </div>
                      <div>
                        <label className="flex items-center justify-between text-[10px] text-slate-400 mb-1">
                          <span className="flex items-center gap-1">
                            <Sliders size={10} className="text-neon-cyan" />
                            全文权重
                          </span>
                          <span className="font-mono text-neon-cyan">{fulltextWeight.toFixed(2)}</span>
                        </label>
                        <input
                          type="range"
                          min={0}
                          max={1}
                          step={0.05}
                          value={fulltextWeight}
                          onChange={(e) => setFulltextWeight(Number(e.target.value))}
                          className="w-full h-1 accent-neon-cyan cursor-pointer"
                        />
                      </div>
                    </div>
                  )}

                  {/* 重排序开关 */}
                  <div className="flex items-center justify-between">
                    <label className="flex items-center gap-1.5 text-[11px] text-slate-200">
                      <Sparkles size={11} className="text-neon-cyan" />
                      重排序
                      <span className="text-[10px] text-slate-500">（对检索结果二次精排）</span>
                    </label>
                    <button
                      type="button"
                      onClick={() => setRerank((v) => !v)}
                      className={`relative inline-flex h-4 w-7 items-center rounded-full transition-colors ${
                        rerank ? 'bg-neon-cyan/70' : 'bg-slate-700'
                      }`}
                    >
                      <span
                        className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${
                          rerank ? 'translate-x-3.5' : 'translate-x-0.5'
                        }`}
                      />
                    </button>
                  </div>

                  {/* 重排序模型（仅启用重排序时显示） */}
                  {rerank && (
                    <div>
                      <label className="flex items-center gap-1 text-[10px] text-slate-400 mb-1">
                        <FileCode size={10} className="text-neon-cyan" />
                        重排序模型
                      </label>
                      <select
                        value={rerankModel}
                        onChange={(e) => setRerankModel(e.target.value)}
                        className="w-full rounded-md bg-slate-900/60 border border-slate-700/50 px-2 py-1.5 text-[11px] text-slate-200 font-mono outline-none focus:border-neon-cyan/50"
                      >
                        {RERANK_MODELS.map((m) => (
                          <option key={m} value={m} className="bg-slate-900">
                            {m}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* 搜索输入框 + 按钮 */}
            <div className="flex items-center gap-2">
              <div className="flex-1 flex items-center gap-1.5 rounded-lg bg-slate-900/60 border border-slate-700/50 px-3 py-2 focus-within:border-neon-cyan/50 transition-all">
                <Search size={13} className="text-slate-500 flex-shrink-0" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onKeyDown={handleSearchKeyDown}
                  placeholder="输入问题测试检索效果..."
                  disabled={searching}
                  className="flex-1 bg-transparent text-xs text-slate-200 placeholder-slate-500 outline-none disabled:opacity-60"
                />
              </div>
              <button
                onClick={handleSearch}
                disabled={searching || !searchQuery.trim()}
                className="flex items-center gap-1 rounded-lg bg-gradient-to-br from-neon-cyan/80 to-neon-cyan/60 px-4 py-2 text-xs text-slate-900 font-medium hover:shadow-glow-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
              >
                {searching ? <RefreshCw size={12} className="animate-spin" /> : <Sparkles size={12} />}
                搜索
              </button>
            </div>

            {/* 检索结果区 */}
            <div className="mt-3">
              {searching ? (
                <div className="flex items-center justify-center py-6 text-slate-400">
                  <RefreshCw size={14} className="animate-spin text-neon-cyan mr-2" />
                  检索中...
                </div>
              ) : hasSearched && searchResults.length > 0 ? (
                <div className="space-y-2">
                  {searchResults.map((result, idx) => {
                    const percent = Math.round(result.score * 100);
                    const barColor = getScoreBarColor(result.score);
                    const { Icon } = getFileIcon(result.docName);
                    return (
                      <div
                        key={`${result.docCode}-${result.chunkIndex}-${idx}`}
                        className="rounded-md bg-slate-900/40 border border-slate-700/40 px-3 py-2.5"
                      >
                        {/* 来源信息 + 检索模式标签 */}
                        <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                          <Icon size={11} className="text-slate-400 flex-shrink-0" />
                          <span className="text-[11px] text-slate-300 truncate flex-1 min-w-0">
                            {result.docName}
                          </span>
                          {/* 来源标签 */}
                          <span className="flex items-center gap-0.5 text-[9px] px-1.5 py-0.5 rounded border border-slate-600/50 bg-slate-800/60 text-slate-400 font-mono flex-shrink-0">
                            <Tag size={8} />
                            来源
                          </span>
                          {/* 检索模式标签 */}
                          <span className="text-[9px] px-1.5 py-0.5 rounded border bg-purple-900/40 text-purple-300 border-purple-500/40 font-mono flex-shrink-0">
                            {currentModeTag}
                          </span>
                          <span className="text-[10px] text-slate-500 font-mono flex-shrink-0">
                            #{result.chunkIndex}
                          </span>
                        </div>
                        {/* 相似度进度条 */}
                        <div className="flex items-center gap-2 mb-1.5">
                          <span className="text-[10px] text-slate-500 font-mono flex-shrink-0">相似度</span>
                          <div className="flex-1 h-1 rounded-full bg-slate-800/60 overflow-hidden">
                            <div
                              className={`h-full rounded-full transition-all duration-500 ${barColor}`}
                              style={{ width: `${percent}%` }}
                            />
                          </div>
                          <span className={`text-[10px] font-mono font-medium flex-shrink-0 ${
                            result.score >= 0.7 ? 'text-green-400'
                              : result.score >= 0.4 ? 'text-orange-400'
                              : 'text-slate-500'
                          }`}>
                            {percent}%
                          </span>
                        </div>
                        {/* 内容片段 */}
                        <p className="text-[11px] text-slate-400 leading-relaxed line-clamp-4">
                          {result.content}
                        </p>
                        {/* 摘要（如有） */}
                        {result.summary && (
                          <div className="mt-1.5 pt-1.5 border-t border-slate-700/40">
                            <div className="flex items-center gap-0.5 text-[9px] text-cyan-400 mb-0.5">
                              <Sparkles size={9} />
                              摘要
                            </div>
                            <p className="text-[10px] text-slate-500 leading-relaxed">
                              {result.summary}
                            </p>
                          </div>
                        )}
                        {/* 标签（逗号分隔渲染为芯片） */}
                        {result.tags && (
                          <div className="mt-1.5 flex flex-wrap gap-1">
                            {result.tags.split(',').filter(t => t.trim() && !t.startsWith('chunk_')).map((tag, idx) => (
                              <span key={idx} className="text-[9px] px-1.5 py-0.5 rounded bg-purple-900/30 border border-purple-500/30 text-purple-300 flex items-center gap-0.5">
                                <Tag size={8} />
                                {tag.trim()}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : hasSearched ? (
                <div className="flex flex-col items-center justify-center py-6 text-slate-500">
                  <AlertCircle size={20} className="text-slate-600 mb-2" />
                  <p className="text-xs">未找到相关文档片段</p>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center py-6 text-slate-500">
                  <Search size={20} className="text-slate-600 mb-2" />
                  <p className="text-xs">输入问题后点击搜索，测试知识库检索效果</p>
                </div>
              )}
            </div>
          </section>
        </div>

        {/* ===== Toast 提示 ===== */}
        {toast && (
          <div className={`absolute bottom-4 left-1/2 -translate-x-1/2 rounded-lg px-4 py-2 text-xs font-medium animate-fade-in flex items-center gap-1.5 ${
            toast.type === 'success'
              ? 'bg-green-900/80 text-green-200 border border-green-500/40'
              : 'bg-red-900/80 text-red-200 border border-red-500/40'
          }`}>
            {toast.type === 'success' ? <CheckCircle size={12} /> : <AlertCircle size={12} />}
            {toast.msg}
          </div>
        )}
      </div>
    </div>
  );
}
