/**
 * Agent YML 编辑器组件（v4.3.3 纯 AgentScope 链路版）
 *
 * <p>通过 Nacos ConfigService 读写 YML 配置，保存后自动触发热重载：</p>
 * <ol>
 *   <li>左侧列表展示可编辑的 YML 文件（subagents.yml / spring-boot-config.yml / model.yml / frontend-routes.yml）</li>
 *   <li>右侧 textarea 编辑选中的 YML 内容</li>
 *   <li>保存按钮 → POST /api/yml/save → Nacos ConfigService.publishConfig</li>
 *   <li>Nacos 推送变更 → Spring Cloud RefreshEvent → @RefreshScope Bean 重建</li>
 *   <li>SubagentDeclarationsConfig 重新加载 → HarnessAgent 马上调度新 Agent</li>
 * </ol>
 *
 * <p>核心场景：编辑 subagents.yml 新增子 Agent，保存后无需重启，HarnessAgent 即可调度新 Agent。</p>
 *
 * @author zqs
 * @since 4.3.3
 */
import { useState, useEffect, useCallback } from 'react';
import { X, Save, RefreshCw, FileText, Check, AlertCircle } from 'lucide-react';
import { listAgentYmlFiles, readAgentYml, saveAgentYml, type YmlFileInfo } from '../api/client';

/** AgentYmlEditor 组件 Props */
interface AgentYmlEditorProps {
  /** 弹窗是否打开 */
  open: boolean;
  /** 关闭弹窗回调 */
  onClose: () => void;
}

/** 保存状态 */
type SaveStatus = 'idle' | 'saving' | 'success' | 'error';

/**
 * Agent YML 编辑器组件。
 */
export default function AgentYmlEditor({ open, onClose }: AgentYmlEditorProps) {
  /** YML 文件列表 */
  const [files, setFiles] = useState<YmlFileInfo[]>([]);
  /** 当前选中的 dataId */
  const [selectedDataId, setSelectedDataId] = useState<string>('');
  /** 当前编辑的 YML 内容 */
  const [content, setContent] = useState<string>('');
  /** 原始内容（用于判断是否有修改） */
  const [originalContent, setOriginalContent] = useState<string>('');
  /** 加载状态 */
  const [loading, setLoading] = useState<boolean>(false);
  /** 保存状态 */
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');
  /** 错误/提示消息 */
  const [message, setMessage] = useState<string>('');

  /** 加载 YML 文件列表 */
  const loadFileList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listAgentYmlFiles();
      if (res.success && res.data) {
        setFiles(res.data);
        // 默认选中第一个文件（subagents.yml）
        if (res.data.length > 0 && !selectedDataId) {
          setSelectedDataId(res.data[0].dataId);
        }
      } else {
        setMessage(res.message || '加载文件列表失败');
      }
    } catch (err) {
      setMessage(`加载文件列表异常: ${err}`);
    } finally {
      setLoading(false);
    }
  }, [selectedDataId]);

  /** 加载指定 YML 文件内容 */
  const loadFileContent = useCallback(async (dataId: string) => {
    setLoading(true);
    setSaveStatus('idle');
    setMessage('');
    try {
      const res = await readAgentYml(dataId);
      if (res.success && res.data) {
        setContent(res.data.content);
        setOriginalContent(res.data.content);
      } else {
        setContent('');
        setOriginalContent('');
        setMessage(res.message || `读取 ${dataId} 失败`);
      }
    } catch (err) {
      setMessage(`读取 ${dataId} 异常: ${err}`);
      setContent('');
      setOriginalContent('');
    } finally {
      setLoading(false);
    }
  }, []);

  /** 弹窗打开时加载文件列表 */
  useEffect(() => {
    if (open) {
      loadFileList();
    }
  }, [open, loadFileList]);

  /** 选中文件变化时加载内容 */
  useEffect(() => {
    if (open && selectedDataId) {
      loadFileContent(selectedDataId);
    }
  }, [open, selectedDataId, loadFileContent]);

  /** 保存 YML 文件 */
  const handleSave = useCallback(async () => {
    if (!selectedDataId || content === originalContent) {
      setMessage('内容未修改，无需保存');
      return;
    }
    setSaveStatus('saving');
    setMessage('正在保存到 Nacos 并触发热重载...');
    try {
      const res = await saveAgentYml(selectedDataId, content);
      if (res.success) {
        setSaveStatus('success');
        setOriginalContent(content);
        setMessage(`保存成功！HarnessAgent 已热加载新的 Agent 配置`);
        // 3 秒后清除成功状态
        setTimeout(() => {
          setSaveStatus('idle');
          setMessage('');
        }, 3000);
      } else {
        setSaveStatus('error');
        setMessage(res.message || '保存失败');
      }
    } catch (err) {
      setSaveStatus('error');
      setMessage(`保存异常: ${err}`);
    }
  }, [selectedDataId, content, originalContent]);

  /** 是否有未保存的修改 */
  const hasChanges = content !== originalContent;

  /** ESC 键关闭 */
  useEffect(() => {
    if (!open) return;
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !hasChanges) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [open, hasChanges, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm">
      <div className="relative w-[95vw] h-[90vh] max-w-7xl rounded-2xl glass-panel border border-neon-cyan/30 shadow-2xl flex flex-col">
        {/* 标题栏 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/10">
          <div className="flex items-center gap-3">
            <FileText size={20} className="text-neon-cyan" />
            <h2 className="text-lg font-semibold text-slate-100">Agent YML 编辑器</h2>
            <span className="text-xs text-slate-400 ml-2">
              编辑 Nacos YML 配置，保存后自动热重载 HarnessAgent
            </span>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-white/10 transition-colors"
            title="关闭"
          >
            <X size={18} className="text-slate-400" />
          </button>
        </div>

        {/* 主体内容：左侧文件列表 + 右侧编辑器 */}
        <div className="flex-1 flex overflow-hidden">
          {/* 左侧文件列表 */}
          <div className="w-64 border-r border-white/10 overflow-y-auto p-3 space-y-1">
            <div className="text-xs text-slate-500 uppercase tracking-wider px-2 py-1 mb-2">
              配置文件
            </div>
            {files.map((file) => (
              <button
                key={file.dataId}
                onClick={() => setSelectedDataId(file.dataId)}
                className={`w-full text-left px-3 py-2.5 rounded-lg transition-all ${
                  selectedDataId === file.dataId
                    ? 'glass-panel border border-neon-cyan/40 text-neon-cyan'
                    : 'hover:bg-white/5 text-slate-300 border border-transparent'
                }`}
              >
                <div className="flex items-center gap-2">
                  <FileText size={14} className="flex-shrink-0" />
                  <span className="text-sm font-medium truncate">{file.dataId}</span>
                </div>
                <div className="text-xs text-slate-500 mt-1 ml-5 truncate">
                  {file.name}
                </div>
              </button>
            ))}
          </div>

          {/* 右侧编辑器 */}
          <div className="flex-1 flex flex-col">
            {/* 工具栏 */}
            <div className="flex items-center justify-between px-4 py-2 border-b border-white/10">
              <div className="flex items-center gap-2">
                <span className="text-sm text-slate-300">
                  {selectedDataId || '未选择文件'}
                </span>
                {hasChanges && (
                  <span className="text-xs text-yellow-400 flex items-center gap-1">
                    <AlertCircle size={12} /> 未保存
                  </span>
                )}
                {saveStatus === 'success' && (
                  <span className="text-xs text-green-400 flex items-center gap-1">
                    <Check size={12} /> 已保存
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => loadFileContent(selectedDataId)}
                  disabled={loading || !selectedDataId}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg glass-panel hover-neon text-xs text-slate-300 transition-all disabled:opacity-50"
                  title="重新加载"
                >
                  <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
                  重新加载
                </button>
                <button
                  onClick={handleSave}
                  disabled={loading || saveStatus === 'saving' || !hasChanges}
                  className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-neon-cyan/20 border border-neon-cyan/40 hover:bg-neon-cyan/30 text-xs text-neon-cyan transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                  title="保存到 Nacos 并触发热重载"
                >
                  {saveStatus === 'saving' ? (
                    <RefreshCw size={12} className="animate-spin" />
                  ) : (
                    <Save size={12} />
                  )}
                  {saveStatus === 'saving' ? '保存中...' : '保存并热重载'}
                </button>
              </div>
            </div>

            {/* 文件描述 */}
            {selectedDataId && files.find(f => f.dataId === selectedDataId)?.description && (
              <div className="px-4 py-1.5 bg-white/5 border-b border-white/10">
                <span className="text-xs text-slate-400">
                  {files.find(f => f.dataId === selectedDataId)?.description}
                </span>
              </div>
            )}

            {/* 消息提示 */}
            {message && (
              <div className={`px-4 py-2 text-xs border-b border-white/10 ${
                saveStatus === 'error' ? 'bg-red-500/10 text-red-400' :
                saveStatus === 'success' ? 'bg-green-500/10 text-green-400' :
                'bg-blue-500/10 text-blue-400'
              }`}>
                {message}
              </div>
            )}

            {/* YML 编辑区 */}
            <textarea
              value={content}
              onChange={(e) => {
                setContent(e.target.value);
                setSaveStatus('idle');
              }}
              disabled={loading || !selectedDataId}
              className="flex-1 w-full bg-transparent text-slate-200 font-mono text-sm p-4 resize-none outline-none disabled:opacity-50"
              placeholder="选择左侧文件以加载 YML 内容..."
              spellCheck={false}
              style={{
                fontFamily: 'Menlo, Monaco, "Courier New", monospace',
                lineHeight: '1.6',
                tabSize: 2,
              }}
            />
          </div>
        </div>

        {/* 底部状态栏 */}
        <div className="flex items-center justify-between px-6 py-2 border-t border-white/10 text-xs text-slate-500">
          <div className="flex items-center gap-4">
            <span>行数: {content.split('\n').length}</span>
            <span>字符: {content.length}</span>
            {hasChanges && <span className="text-yellow-400">有未保存的修改</span>}
          </div>
          <div className="flex items-center gap-2">
            <span className="text-slate-600">Nacos Group: hdl-agent-scope</span>
            <span className="text-slate-600">|</span>
            <span className="text-slate-600">保存后自动热重载</span>
          </div>
        </div>
      </div>
    </div>
  );
}
