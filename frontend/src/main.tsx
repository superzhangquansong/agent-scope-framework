import { Component, StrictMode, type ReactNode, type ErrorInfo } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import RagPage from './RagPage.tsx'

interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
}

/**
 * 全局错误边界组件
 * 捕获子组件渲染异常，避免整个应用白屏
 */
class ErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('[ErrorBoundary] 捕获异常:', error, errorInfo);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 24, textAlign: 'center' }}>
          <h2 style={{ color: '#a855f7', marginBottom: 12 }}>应用出现异常</h2>
          <p style={{ color: '#64748b', marginBottom: 16 }}>{this.state.error?.message || '未知错误'}</p>
          <button
            onClick={() => window.location.reload()}
            style={{
              padding: '8px 20px',
              background: '#a855f7',
              color: 'white',
              border: 'none',
              borderRadius: 8,
              cursor: 'pointer',
            }}
          >
            刷新页面
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('Root element #root not found');
}

// v4.4.3：根据 URL 路径决定渲染哪个页面（无 react-router 的轻量路由）
// /rag → RAG 知识库管理独立页面（从设置弹窗 window.open 打开的新浏览器窗口）
// 其他路径 → 主应用 App
const currentPath = window.location.pathname.replace(/\/+$/, ''); // 去除尾部斜杠
const isRagPage = currentPath === '/rag' || currentPath.endsWith('/rag');

createRoot(rootEl).render(
  <StrictMode>
    <ErrorBoundary>
      {isRagPage ? <RagPage /> : <App />}
    </ErrorBoundary>
  </StrictMode>,
)
