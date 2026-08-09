import { useState, useEffect, useMemo } from 'react';
import { Package, ChevronLeft, ChevronRight } from 'lucide-react';

/** 图片轮播默认间隔（毫秒） */
const DEFAULT_CAROUSEL_INTERVAL_MS = 3000;

/**
 * 解析图片列表
 * 支持三种输入：数组、单个 URL 字符串、逗号分隔的多 URL 字符串
 */
function parseImageList(images: string[] | string | undefined | null): string[] {
  if (!images) return [];
  if (Array.isArray(images)) return images.filter(Boolean) as string[];
  if (typeof images === 'string') {
    return images.split(',').map(s => s.trim()).filter(Boolean);
  }
  return [];
}

export interface ImageCarouselProps {
  /** 图片列表，支持数组、单 URL 字符串、逗号分隔多 URL 字符串 */
  images?: string[] | string;
  /** 图片 alt 文本 */
  alt?: string;
  /** 自定义 className（用于控制尺寸/圆角等） */
  className?: string;
  /** 自动轮播间隔（毫秒），默认 3000 */
  autoPlayMs?: number;
}

/**
 * 图片轮播组件
 *
 * - 支持 3 种输入格式：string[] 数组、string 单 URL、逗号分隔多 URL 字符串
 * - 多图自动轮播（默认 3 秒间隔）、左右箭头切换、底部指示点
 * - 无图时显示 Package 占位图标（适配科幻霓虹深色主题）
 */
export default function ImageCarousel({ images, alt = '', className, autoPlayMs = DEFAULT_CAROUSEL_INTERVAL_MS }: ImageCarouselProps) {
  const list = useMemo(() => parseImageList(images), [images]);
  const [idx, setIdx] = useState(0);

  // 图片列表变化时重置索引
  useEffect(() => { setIdx(0); }, [list]);

  // 自动轮播：多张图片时按 autoPlayMs 间隔自动切换
  useEffect(() => {
    if (list.length <= 1) return;
    const timer = setInterval(() => {
      setIdx(i => (i + 1) % list.length);
    }, autoPlayMs);
    return () => clearInterval(timer);
  }, [list.length, autoPlayMs]);

  // 无图：显示占位图标
  if (list.length === 0) {
    return (
      <div className={`flex items-center justify-center bg-slate-700/40 border border-neon-purple/15 ${className || ''}`}>
        <Package size={20} className="text-slate-500" />
      </div>
    );
  }

  // 单图：直接展示
  if (list.length === 1) {
    return <img src={list[0]} alt={alt} className={`object-cover ${className || ''}`} />;
  }

  // 多图：轮播展示
  return (
    <div className={`relative overflow-hidden ${className || ''}`}>
      <img src={list[idx]} alt={alt} className="w-full h-full object-cover transition-opacity duration-300" />
      {/* 左箭头 */}
      <button
        onClick={(e) => { e.stopPropagation(); setIdx(i => (i - 1 + list.length) % list.length); }}
        className="absolute left-1 top-1/2 -translate-y-1/2 w-6 h-6 flex items-center justify-center rounded-full bg-black/40 text-neon-cyan hover:bg-black/60 transition-colors border border-neon-cyan/30"
        aria-label="上一张"
      >
        <ChevronLeft size={14} />
      </button>
      {/* 右箭头 */}
      <button
        onClick={(e) => { e.stopPropagation(); setIdx(i => (i + 1) % list.length); }}
        className="absolute right-1 top-1/2 -translate-y-1/2 w-6 h-6 flex items-center justify-center rounded-full bg-black/40 text-neon-cyan hover:bg-black/60 transition-colors border border-neon-cyan/30"
        aria-label="下一张"
      >
        <ChevronRight size={14} />
      </button>
      {/* 底部指示点 */}
      <div className="absolute bottom-1 left-1/2 -translate-x-1/2 flex gap-1">
        {list.map((_, i) => (
          <button
            key={i}
            onClick={(e) => { e.stopPropagation(); setIdx(i); }}
            className={`h-1.5 rounded-full transition-all ${i === idx ? 'bg-neon-cyan w-3' : 'bg-slate-400/60 w-1.5'}`}
            aria-label={`第 ${i + 1} 张`}
          />
        ))}
      </div>
    </div>
  );
}
