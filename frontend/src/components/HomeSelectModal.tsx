import { useState } from 'react';
import { Home, X, ChevronRight } from 'lucide-react';
import { switchHome as apiSwitchHome } from '../api/client';

/** 房屋信息 */
interface HomeInfo {
  homeId: string;
  homeName: string;
  homeType?: string;
}

/** 房屋选择弹窗属性 */
interface HomeSelectModalProps {
  open: boolean;
  homes: HomeInfo[];
  onClose: () => void;
  onSelect: (homeName: string) => void;
  /** 房屋列表查询是否失败（HDL API 不可达等） */
  homeQueryFailed?: boolean;
  /** 是否必须选择房屋（true 时隐藏关闭按钮、禁用背景点击关闭） */
  required?: boolean;
}

/**
 * 房屋选择弹窗。
 *
 * <p>当用户有多个房屋且未选择时，由 need_select_home SSE 事件触发弹出。
 * 用户选择房屋后调用 /api/auth/switchHome 切换，然后重发原消息。</p>
 */
export default function HomeSelectModal({ open, homes, onClose, onSelect, homeQueryFailed, required }: HomeSelectModalProps) {
  const [selecting, setSelecting] = useState(false);
  const [error, setError] = useState('');

  if (!open) return null;

  const handleSelect = async (homeName: string) => {
    setSelecting(true);
    setError('');
    try {
      const r = await apiSwitchHome(homeName);
      if (r.success) {
        onSelect(homeName);
      } else {
        setError(r.message || '切换房屋失败');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '切换房屋失败');
    } finally {
      setSelecting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm animate-fade-in"
      onClick={required ? undefined : onClose}
    >
      <div
        className="relative w-full max-w-md rounded-2xl glass-strong border border-neon-purple/40 shadow-glow-lg p-6"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 关闭按钮（required 模式下隐藏） */}
        {!required && (
          <button
            onClick={onClose}
            className="absolute top-3 right-3 text-slate-400 hover:text-neon-pink transition-colors"
          >
            <X size={18} />
          </button>
        )}

        {/* 标题 */}
        <div className="flex items-center gap-3 mb-4">
          <div className="rounded-xl bg-gradient-to-br from-neon-purple to-brand-600 p-2.5 text-white shadow-neon-purple">
            <Home size={20} />
          </div>
          <div>
            <h3 className="text-base font-semibold gradient-text">选择房屋</h3>
            <p className="text-[11px] text-slate-400 font-mono">请选择要操作的房屋</p>
          </div>
        </div>

        {/* 错误提示 */}
        {error && (
          <div className="mb-3 rounded-lg glass-panel border border-red-500/40 px-3 py-2 text-xs text-red-300">
            {error}
          </div>
        )}

        {/* 房屋列表 */}
        <div className="space-y-2 max-h-64 overflow-y-auto">
          {homes.length === 0 ? (
            <div className="text-center py-6 text-sm text-slate-500">
              {homeQueryFailed ? (
                <>
                  <div className="text-amber-400 mb-1">房屋列表加载失败</div>
                  <div className="text-xs text-slate-400">请检查 HDL 网关连接和 appKey/appSecret 配置</div>
                </>
              ) : (
                '未找到房屋信息'
              )}
            </div>
          ) : (
            homes.map((home, i) => (
              <button
                key={home.homeId || i}
                onClick={() => handleSelect(home.homeName)}
                disabled={selecting}
                className="group w-full flex items-center gap-3 rounded-xl glass-panel hover-neon px-4 py-3 text-left transition-all disabled:opacity-50"
              >
                <div className="rounded-lg bg-neon-purple/10 p-2 text-neon-purple group-hover:bg-neon-purple/20 group-hover:shadow-glow-sm transition-all">
                  <Home size={16} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-slate-200 group-hover:text-neon-cyan transition-colors truncate">
                    {home.homeName}
                  </div>
                  {home.homeType && (
                    <div className="text-[11px] text-slate-400 font-mono">{home.homeType}</div>
                  )}
                </div>
                <ChevronRight size={14} className="text-slate-500 group-hover:text-neon-purple group-hover:translate-x-0.5 transition-all" />
              </button>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
