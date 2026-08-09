import { useState, memo } from 'react';
import { X, User, Lock, Loader2 } from 'lucide-react';
import { login, setSessionToken } from '../api/client';

/** 待处理消息截断长度（用于登录弹窗展示） */
const PENDING_MESSAGE_MAX_LENGTH = 30;

interface LoginModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (loginName: string) => void;
  pendingMessage?: string;
  required?: boolean;
}

/**
 * 登录弹窗
 *
 * 玻璃态 + 霓虹紫风格。用户输入 HDL 账号密码后调用 /api/auth/login。
 */
function LoginModal({ open, onClose, onSuccess, pendingMessage, required }: LoginModalProps) {
  const [loginName, setLoginName] = useState('');
  const [loginPwd, setLoginPwd] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginName.trim() || !loginPwd.trim()) {
      setError('用户名和密码不能为空');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await login(loginName.trim(), loginPwd.trim());
      if (res.success && res.data?.sessionToken) {
        setSessionToken(res.data.sessionToken);
        onSuccess(res.data.loginName || loginName.trim());
        setLoginName('');
        setLoginPwd('');
      } else {
        setError(res.message || '登录失败');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(msg || '网络错误');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm animate-fade-in">
      <div className="relative w-[420px] max-w-[92vw] rounded-2xl glass-strong border border-neon-purple/40 shadow-glow-md animate-card-in">
        <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-neon-purple to-transparent shadow-[0_0_8px_#a855f7]" />

        <div className="flex items-center justify-between px-6 pt-5 pb-3">
          <div className="flex-1 min-w-0">
            <h2 className="text-lg font-semibold gradient-text tracking-wide">登录 HDL 账号</h2>
            <p className="mt-1 text-xs text-slate-400 font-mono truncate">
              {pendingMessage
                ? `登录后将继续：${pendingMessage.length > PENDING_MESSAGE_MAX_LENGTH
                    ? pendingMessage.slice(0, PENDING_MESSAGE_MAX_LENGTH) + '...'
                    : pendingMessage}`
                : '登录后即可使用智能家居、产品下单等功能'}
            </p>
          </div>
          {!required && (
            <button
              onClick={onClose}
              disabled={loading}
              className="rounded-full p-1.5 text-slate-400 hover:text-neon-pink hover:bg-neon-purple/10 transition-colors disabled:opacity-40"
              title="关闭"
            >
              <X size={18} />
            </button>
          )}
        </div>

        <form onSubmit={handleSubmit} className="px-6 pb-6 space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5 font-mono uppercase tracking-wider">
              用户名
            </label>
            <div className="relative">
              <User size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-neon-purple/70" />
              <input
                type="text"
                value={loginName}
                onChange={(e) => setLoginName(e.target.value)}
                placeholder="请输入 HDL 用户名"
                autoComplete="username"
                disabled={loading}
                className="input-focus w-full rounded-lg glass-panel border border-neon-purple/20 pl-10 pr-3 py-2.5 text-sm text-slate-100 placeholder-slate-500 transition-all"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5 font-mono uppercase tracking-wider">
              密码
            </label>
            <div className="relative">
              <Lock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-neon-purple/70" />
              <input
                type="password"
                value={loginPwd}
                onChange={(e) => setLoginPwd(e.target.value)}
                placeholder="请输入密码"
                autoComplete="current-password"
                disabled={loading}
                className="input-focus w-full rounded-lg glass-panel border border-neon-purple/20 pl-10 pr-3 py-2.5 text-sm text-slate-100 placeholder-slate-500 transition-all"
              />
            </div>
          </div>

          {error && (
            <div className="rounded-lg bg-red-500/10 border border-red-500/40 px-3 py-2 text-sm text-red-300 shadow-[0_0_10px_rgba(239,68,68,0.25)]">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className={`shimmer-btn w-full rounded-lg bg-gradient-to-r from-neon-purple to-brand-600 px-4 py-2.5 text-sm font-medium text-white shadow-neon-purple transition-all hover:shadow-glow-md disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 ${
              !loading ? 'animate-glow' : ''
            }`}
          >
            {loading && <Loader2 size={16} className="animate-spin" />}
            {loading ? '登录中...' : '登录'}
          </button>

          <p className="text-center text-[11px] text-slate-500 font-mono">
            登录即表示您同意使用 HDL Skill Engine 服务
          </p>
        </form>
      </div>
    </div>
  );
}

export default memo(LoginModal);
