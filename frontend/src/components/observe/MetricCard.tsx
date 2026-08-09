/**
 * 指标卡片组件
 *
 * 单个指标的可视化展示：
 *  - 标题（如"LLM 调用次数"）
 *  - 主数值（如 1234）
 *  - 副数值（如"平均耗时 234ms"）
 *  - 图标
 *  - 主题色（neon-cyan / neon-purple / neon-green / neon-pink）
 */

import { type ReactNode } from 'react';

/** 指标卡片主题色 */
type MetricColor = 'cyan' | 'purple' | 'green' | 'pink' | 'amber';

/** 主题色到 Tailwind class 的映射（避免动态拼接 class 名被 Tailwind 清理） */
const COLOR_CLASSES: Record<MetricColor, {
  text: string;
  border: string;
  glow: string;
  icon: string;
}> = {
  cyan: {
    text: 'text-neon-cyan',
    border: 'border-neon-cyan/30',
    glow: 'shadow-[0_0_20px_-5px_rgba(34,211,238,0.4)]',
    icon: 'text-neon-cyan',
  },
  purple: {
    text: 'text-neon-purple',
    border: 'border-neon-purple/30',
    glow: 'shadow-[0_0_20px_-5px_rgba(168,85,247,0.4)]',
    icon: 'text-neon-purple',
  },
  green: {
    text: 'text-neon-green',
    border: 'border-neon-green/30',
    glow: 'shadow-[0_0_20px_-5px_rgba(74,222,128,0.4)]',
    icon: 'text-neon-green',
  },
  pink: {
    text: 'text-neon-pink',
    border: 'border-neon-pink/30',
    glow: 'shadow-[0_0_20px_-5px_rgba(236,72,153,0.4)]',
    icon: 'text-neon-pink',
  },
  amber: {
    text: 'text-amber-400',
    border: 'border-amber-400/30',
    glow: 'shadow-[0_0_20px_-5px_rgba(251,191,36,0.4)]',
    icon: 'text-amber-400',
  },
};

/** MetricCard 属性 */
export interface MetricCardProps {
  /** 指标标题 */
  title: string;
  /** 主数值 */
  value: string | number;
  /** 副数值（描述性文字） */
  subtitle?: string;
  /** 图标节点 */
  icon?: ReactNode;
  /** 主题色 */
  color?: MetricColor;
}

/** 指标卡片组件 */
export default function MetricCard({
  title,
  value,
  subtitle,
  icon,
  color = 'cyan',
}: MetricCardProps) {
  const cls = COLOR_CLASSES[color];
  return (
    <div
      className={`glass-panel rounded-lg p-3 border ${cls.border} ${cls.glow} transition-all hover:scale-[1.02]`}
    >
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs text-slate-400 uppercase tracking-wider">{title}</span>
        {icon && <span className={cls.icon}>{icon}</span>}
      </div>
      <div className={`text-2xl font-bold ${cls.text} tabular-nums`}>{value}</div>
      {subtitle && <div className="text-xs text-slate-500 mt-0.5">{subtitle}</div>}
    </div>
  );
}
