/**
 * @file 分页组件
 *
 * 通用前端客户端分页 UI 组件，提供：
 *  - 上一页 / 下一页按钮
 *  - 当前页 / 总页数指示
 *  - 页码输入框 + 跳转按钮（回车或点击跳转）
 *
 * 仅在 totalPages > 1 时渲染，单页场景返回 null。
 *
 * 来源：从 SceneListPage.tsx 和 ProductListPage.tsx 中提取的重复分页逻辑（两处实现完全一致）。
 */

import { useState, useEffect } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

/**
 * Pagination 属性
 */
export interface PaginationProps {
  /** 当前页码（从 1 开始） */
  currentPage: number;
  /** 总页数 */
  totalPages: number;
  /** 翻页回调，参数为目标页码（已钳制到 [1, totalPages]） */
  onPageChange: (page: number) => void;
}

/**
 * 通用分页组件。
 *
 * 使用示例：
 * ```tsx
 * {totalPages > 1 && (
 *   <Pagination
 *     currentPage={currentPage}
 *     totalPages={totalPages}
 *     onPageChange={goToPage}
 *   />
 * )}
 * ```
 *
 * 内部维护页码输入框的本地状态，外部 currentPage 变化时同步。
 * 输入框支持回车跳转，也提供「跳转」按钮。
 *
 * @param props PaginationProps
 */
export function Pagination({ currentPage, totalPages, onPageChange }: PaginationProps) {
  const [pageInput, setPageInput] = useState(String(currentPage));

  // 外部 currentPage 变化时同步输入框（如切换查询关键词后产品列表更新导致页码重置）
  useEffect(() => {
    setPageInput(String(currentPage));
  }, [currentPage]);

  // 单页场景不渲染分页
  if (totalPages <= 1) return null;

  /** 跳转到指定页码（钳制到合法范围） */
  const goToPage = (p: number) => {
    const target = Math.min(Math.max(1, p), totalPages);
    if (target === currentPage) return;
    onPageChange(target);
  };

  return (
    <div className="flex items-center justify-center gap-2 pt-2 text-xs text-slate-300">
      {/* 上一页 */}
      <button
        onClick={() => goToPage(currentPage - 1)}
        disabled={currentPage <= 1}
        className="glass-panel rounded-lg p-1.5 text-neon-cyan hover-neon disabled:opacity-40 disabled:cursor-not-allowed transition-all"
      >
        <ChevronLeft size={14} />
      </button>
      {/* 当前页 / 总页数 */}
      <span className="tabular-nums font-mono">
        第 {currentPage} / {totalPages} 页
      </span>
      {/* 页码输入框：回车跳转 */}
      <input
        type="text"
        value={pageInput}
        onChange={(e) => setPageInput(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            const p = parseInt(pageInput, 10);
            if (!isNaN(p)) goToPage(p);
          }
        }}
        className="w-12 text-center rounded-md glass-panel border border-neon-purple/15 px-1 py-1 text-xs focus:outline-none focus:border-neon-purple/50 text-slate-200 font-mono"
        placeholder={String(currentPage)}
      />
      {/* 跳转按钮 */}
      <button
        onClick={() => { const p = parseInt(pageInput, 10); if (!isNaN(p)) goToPage(p); }}
        className="px-2 py-1 rounded-md glass-panel border border-neon-purple/15 text-slate-300 hover-neon"
      >
        跳转
      </button>
      {/* 下一页 */}
      <button
        onClick={() => goToPage(currentPage + 1)}
        disabled={currentPage >= totalPages}
        className="glass-panel rounded-lg p-1.5 text-neon-cyan hover-neon disabled:opacity-40 disabled:cursor-not-allowed transition-all"
      >
        <ChevronRight size={14} />
      </button>
    </div>
  );
}
