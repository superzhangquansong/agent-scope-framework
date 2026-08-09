/**
 * 百问百答列表页组件
 *
 * <p>展示 CRM 百问百答查询结果，以问答卡片流形式呈现。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>每条 QA 显示为可展开卡片：问题作为标题，点击展开显示答案+图片</li>
 *   <li>科幻毛玻璃风格，霓虹紫青配色</li>
 *   <li>客户端分页（每页 5 条）</li>
 *   <li>空结果时显示友好提示</li>
 * </ul>
 *
 * @author zqs
 * @since 3.3.0
 */
import { useState, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { HelpCircle, ChevronDown, ChevronUp, Tag, RefreshCw, Download } from 'lucide-react';
import { Pagination } from '../shared/components';
import type { RoutePageProps } from './DynamicPage';

/** 每页条数（前端客户端分页） */
const PAGE_SIZE = 5;

/**
 * Markdown 渲染组件样式映射（科幻深色主题）。
 *
 * <p>适配深色背景：文字浅色、表格边框霓虹紫、代码块深色背景、标题霓虹青。</p>
 */
const MARKDOWN_COMPONENTS = {
  h1: ({ children }: { children?: React.ReactNode }) => (
    <h1 className="text-base font-bold text-neon-cyan mt-3 mb-2 leading-snug">{children}</h1>
  ),
  h2: ({ children }: { children?: React.ReactNode }) => (
    <h2 className="text-sm font-bold text-neon-cyan mt-3 mb-2 leading-snug">{children}</h2>
  ),
  h3: ({ children }: { children?: React.ReactNode }) => (
    <h3 className="text-sm font-semibold text-neon-purple mt-2 mb-1.5 leading-snug">{children}</h3>
  ),
  h4: ({ children }: { children?: React.ReactNode }) => (
    <h4 className="text-xs font-semibold text-neon-purple mt-2 mb-1 leading-snug">{children}</h4>
  ),
  h5: ({ children }: { children?: React.ReactNode }) => (
    <h5 className="text-xs font-medium text-slate-200 mt-2 mb-1 leading-snug">{children}</h5>
  ),
  h6: ({ children }: { children?: React.ReactNode }) => (
    <h6 className="text-xs font-medium text-slate-300 mt-2 mb-1 leading-snug">{children}</h6>
  ),
  p: ({ children }: { children?: React.ReactNode }) => (
    <p className="text-xs text-slate-200 leading-relaxed my-1.5">{children}</p>
  ),
  ul: ({ children }: { children?: React.ReactNode }) => (
    <ul className="text-xs text-slate-200 leading-relaxed my-1.5 pl-5 list-disc space-y-0.5">{children}</ul>
  ),
  ol: ({ children }: { children?: React.ReactNode }) => (
    <ol className="text-xs text-slate-200 leading-relaxed my-1.5 pl-5 list-decimal space-y-0.5">{children}</ol>
  ),
  li: ({ children }: { children?: React.ReactNode }) => (
    <li className="text-slate-200">{children}</li>
  ),
  table: ({ children }: { children?: React.ReactNode }) => (
    <div className="overflow-x-auto my-2">
      <table className="w-full text-xs border-collapse border border-neon-purple/40">{children}</table>
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
    <code className={`${className ?? ''} px-1 py-0.5 rounded bg-space-900/80 text-neon-green font-mono text-[11px]`}>{children}</code>
  ),
  pre: ({ children }: { children?: React.ReactNode }) => (
    <pre className="my-2 p-3 rounded-md bg-space-900/90 border border-neon-purple/20 overflow-x-auto [&_code]:bg-transparent [&_code]:p-0 [&_code]:border-0 [&_code]:text-neon-green">{children}</pre>
  ),
  blockquote: ({ children }: { children?: React.ReactNode }) => (
    <blockquote className="my-2 pl-3 border-l-2 border-neon-purple/50 bg-neon-purple/5 py-1 text-slate-300 text-xs">{children}</blockquote>
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

/** QA 数据结构（对应后端 QaManagementVO） */
interface QaItem {
  /** 问答 ID */
  qaManagementId?: number | string;
  /** 问题 */
  question?: string;
  /** 答案内容 */
  answerContent?: string;
  /** 分类名称 */
  categoryName?: string;
  /** 分类 code */
  categoryCode?: string;
  /** 图片 URL 列表 */
  imgUrls?: string[];
  /** 文档编码（用于查找 downloadUrls 中的下载链接） */
  docCode?: string;
  [k: string]: unknown;
}

/** 列表数据结构（兼容多种字段名） */
interface QaListData {
  /** SSE 通道外层字段 */
  list?: QaItem[];
  /** 兼容字段 records（MyBatis-Plus PageVO 默认字段） */
  records?: QaItem[];
  /** 兼容字段 qaList */
  qaList?: QaItem[];
  total?: number;
  pageNo?: number;
  pageSize?: number;
  /** 文件下载链接映射（key=docCode, value=downloadUrl），由后端 frontendExtra 合并而来 */
  downloadUrls?: Record<string, string>;
  [k: string]: unknown;
}

/**
 * 从 SSE data 中提取 QA 列表（兼容 list/records/qaList 多种字段名）。
 * @param data SSE 返回的业务数据
 * @returns QA 列表数组
 */
function extractQaList(data: Record<string, unknown>): QaItem[] {
  const raw = data as QaListData;
  if (Array.isArray(raw.list)) return raw.list;
  if (Array.isArray(raw.records)) return raw.records;
  if (Array.isArray(raw.qaList)) return raw.qaList;
  return [];
}

/**
 * 单个问答卡片组件（可展开/收起）。
 *
 * @param qa          问答数据
 * @param index       卡片索引（用于动画延迟）
 * @param downloadUrl 文件下载链接（由后端 frontendExtra 提供，LLM 看不到）
 */
function QaCard({ qa, index, downloadUrl }: { qa: QaItem; index: number; downloadUrl?: string }) {
  /** 是否展开答案 */
  const [expanded, setExpanded] = useState(index === 0);

  return (
    <div
      className="rounded-xl glass-panel border border-neon-purple/20 hover:border-neon-purple/40 transition-all overflow-hidden"
      style={{
        animation: `panel-anim-right 0.4s ease-out ${index * 0.08}s both`,
      }}
    >
      {/* 问题标题栏（点击展开/收起） */}
      <button
        onClick={() => setExpanded(prev => !prev)}
        className="w-full flex items-start gap-2.5 p-3 text-left hover:bg-white/5 transition-colors"
      >
        {/* 问题图标 */}
        <HelpCircle size={14} className="text-neon-cyan flex-shrink-0 mt-0.5" />
        {/* 问题文本 */}
        <span className="flex-1 text-sm text-slate-100 font-medium leading-relaxed">
          {qa.question || '(无问题)'}
        </span>
        {/* 展开/收起箭头 */}
        {expanded
          ? <ChevronUp size={14} className="text-slate-400 flex-shrink-0 mt-1" />
          : <ChevronDown size={14} className="text-slate-400 flex-shrink-0 mt-1" />
        }
      </button>

      {/* 答案内容（展开时显示） */}
      {expanded && (
        <div className="px-3 pb-3 pt-1 space-y-2">
          {/* 分隔线 */}
          <div className="h-px bg-gradient-to-r from-neon-purple/30 via-neon-cyan/20 to-transparent" />

          {/* 分类标签（如有） */}
          {qa.categoryName && (
            <div className="flex items-center gap-1">
              <Tag size={10} className="text-neon-purple" />
              <span className="text-[10px] text-neon-purple/80 bg-neon-purple/10 px-1.5 py-0.5 rounded-full">
                {qa.categoryName}
              </span>
            </div>
          )}

          {/* 答案内容（Markdown 渲染，支持 GFM 表格/列表/代码块等） */}
          {qa.answerContent && (
            <div className="markdown-body text-slate-200">
              <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS}>
                {qa.answerContent}
              </ReactMarkdown>
            </div>
          )}

          {/* 图片列表（如有） */}
          {qa.imgUrls && qa.imgUrls.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-2">
              {qa.imgUrls.map((url, i) => (
                <img
                  key={i}
                  src={url}
                  alt={`图${i + 1}`}
                  className="w-16 h-16 rounded-lg object-cover border border-white/10 hover:border-neon-cyan/40 transition-colors cursor-pointer"
                  onError={(e) => {
                    (e.target as HTMLImageElement).style.display = 'none';
                  }}
                />
              ))}
            </div>
          )}

          {/* 文件下载链接（由后端 frontendExtra 提供，LLM 看不到，避免 LLM 在回复中拼接链接） */}
          {downloadUrl && (
            <a
              href={downloadUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-[11px] text-neon-cyan/80 bg-neon-cyan/5 border border-neon-cyan/20 hover:border-neon-cyan/50 hover:bg-neon-cyan/10 px-2.5 py-1 rounded-md transition-all"
            >
              <Download size={11} />
              <span>下载源文件{qa.categoryName ? `：${qa.categoryName}` : ''}</span>
            </a>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * 百问百答列表页主组件。
 *
 * @param props.data SSE 返回的业务数据（含 QA 列表）
 */
export default function QaListPage({ data }: RoutePageProps) {
  /** 当前页码 */
  const [currentPage, setCurrentPage] = useState(1);

  /** 提取 QA 列表 */
  const qaList = useMemo(() => extractQaList(data), [data]);

  /** 提取文件下载链接映射（key=docCode, value=downloadUrl） */
  const downloadUrls = useMemo(() => {
    const raw = data as QaListData;
    return raw.downloadUrls ?? {};
  }, [data]);

  /** 总条数 */
  const total = useMemo(() => {
    const raw = data as QaListData;
    return raw.total ?? qaList.length;
  }, [data, qaList]);

  /** 总页数 */
  const totalPages = Math.max(1, Math.ceil(qaList.length / PAGE_SIZE));

  /** 当前页的 QA 列表 */
  const pagedList = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return qaList.slice(start, start + PAGE_SIZE);
  }, [qaList, currentPage]);

  // 空结果
  if (qaList.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-8 gap-2">
        <RefreshCw size={24} className="text-slate-600" />
        <p className="text-xs text-slate-500">暂无相关问答</p>
        <p className="text-[10px] text-slate-600">试试换个关键词搜索</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2.5">
      {/* 结果计数 */}
      <div className="flex items-center justify-between px-1">
        <span className="text-[10px] text-slate-400 font-mono">
          共 {total} 条问答
        </span>
        <span className="text-[10px] text-slate-600 font-mono">
          第 {currentPage}/{totalPages} 页
        </span>
      </div>

      {/* 问答卡片列表 */}
      <div className="space-y-2">
        {pagedList.map((qa, i) => (
          <QaCard
            key={qa.qaManagementId ?? i}
            qa={qa}
            index={i}
            downloadUrl={qa.docCode ? downloadUrls[qa.docCode] : undefined}
          />
        ))}
      </div>

      {/* 分页器 */}
      {totalPages > 1 && (
        <div className="pt-2">
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            onPageChange={setCurrentPage}
          />
        </div>
      )}
    </div>
  );
}
