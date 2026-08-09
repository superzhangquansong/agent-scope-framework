/**
 * RAG 知识库管理独立页面（v4.4.3 新增）。
 *
 * <p>从主应用的设置弹窗中通过 {@code window.open('/rag', '_blank')} 打开，
 * 在全新的浏览器窗口/标签页中展示 RAG 知识库管理界面，提供更大的操作空间。</p>
 *
 * <p>本页面是 RagManagerModal 的全屏包装器：</p>
 * <ul>
 *   <li>复用 RagManagerModal 组件的全部功能（文档列表/上传/检索测试）</li>
 *   <li>open 永远为 true（页面存活期间始终展示）</li>
 *   <li>onClose 调用 window.close() 关闭当前浏览器窗口</li>
 *   <li>顶部增加标题栏，显示"RAG 知识库管理"和关闭按钮</li>
 * </ul>
 *
 * <p>路由方式：main.tsx 根据 window.location.pathname 判断，
 * 路径为 /rag 时渲染本页面，其他路径渲染主 App。</p>
 */
import RagManagerModal from './components/RagManagerModal';

/**
 * RAG 知识库管理独立页面组件。
 */
export default function RagPage() {
  return (
    <RagManagerModal
      open={true}
      onClose={() => window.close()}
    />
  );
}
