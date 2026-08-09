/**
 * 省钱分析报告页组件
 *
 * <p>展示储能电站省钱分析报告，包括总节省金额、各节省来源（高电价/低电价/光伏发电）及日节省明细。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>顶部嵌入 EnergyStation3D 储能 3D 效果展示</li>
 *   <li>总节省金额突出展示（金额 + 货币符号）</li>
 *   <li>三个节省来源卡片：高电价节省、低电价节省、光伏发电节省</li>
 *   <li>日节省列表（如有，展示前 5 条）</li>
 *   <li>科幻毛玻璃风格，霓虹紫青配色</li>
 * </ul>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { useMemo } from 'react';
import {
  PiggyBank,
  TrendingDown,
  Sun,
  Zap,
  Calendar,
  DollarSign,
  BarChart3,
} from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import EnergyStation3D from './EnergyStation3D';

/** 日节省明细数据结构 */
interface DaySaving {
  /** 日期（如 "2024-01-15"） */
  date?: string;
  /** 节省金额 */
  amount?: number | string;
  /** 高电价节省 */
  high?: number | string;
  /** 低电价节省 */
  low?: number | string;
  /** 光伏发电节省 */
  pv?: number | string;
  [k: string]: unknown;
}

/** 节省来源数据结构（高电价/低电价/光伏发电） */
interface SavingsSource {
  /** 节省金额 */
  amount?: number | string;
  /** 占比 */
  ratio?: number | string;
  [k: string]: unknown;
}

/** 省钱分析报告数据结构 */
interface SavingsReportData {
  /** 总节省金额 */
  amount?: number | string;
  /** 货币符号（如 "¥"/"$"） */
  monetarySymbol?: string;
  /** 节省来源汇总（含 high/low/pv） */
  total?: {
    high?: SavingsSource;
    low?: SavingsSource;
    pv?: SavingsSource;
    [k: string]: unknown;
  };
  /** 总结文本 */
  summary?: string;
  /** 日节省明细列表 */
  days?: DaySaving[];
  /** 电池 SOC 电量百分比（用于 3D 展示） */
  batterySoc?: number;
  [k: string]: unknown;
}

/** 日节省列表最大展示条数 */
const MAX_DAY_ITEMS = 5;

/**
 * 格式化金额显示（金额 + 货币符号）。
 * @param amount 金额
 * @param symbol 货币符号
 * @returns 格式化后的金额字符串
 */
function formatAmount(amount: number | string | undefined, symbol?: string): string {
  if (amount == null || amount === '') return '—';
  return `${symbol || ''}${amount}`;
}

/**
 * 格式化百分比显示。
 * <p>兼容小数（0.6 → 60%）和已带百分号的字符串。</p>
 * @param ratio 比率值
 * @returns 格式化后的百分比字符串
 */
function formatRatio(ratio: number | string | undefined): string {
  if (ratio == null) return '—';
  if (typeof ratio === 'string') {
    if (ratio.includes('%')) return ratio;
    const n = parseFloat(ratio);
    if (!isNaN(n)) {
      return n <= 1 ? `${(n * 100).toFixed(1)}%` : `${n.toFixed(1)}%`;
    }
    return ratio;
  }
  return ratio <= 1 ? `${(ratio * 100).toFixed(1)}%` : `${ratio.toFixed(1)}%`;
}

/** 节省来源卡片 Props */
interface SourceCardProps {
  /** 图标 */
  icon: React.ReactNode;
  /** 图标颜色类名 */
  iconColor: string;
  /** 边框颜色类名 */
  borderColor: string;
  /** 标题 */
  title: string;
  /** 金额 */
  amount: string;
  /** 占比 */
  ratio: string;
}

/**
 * 节省来源卡片组件（展示单个节省来源的金额和占比）。
 */
function SourceCard({ icon, iconColor, borderColor, title, amount, ratio }: SourceCardProps) {
  return (
    <div className={`rounded-lg glass border ${borderColor} p-2.5 hover-neon transition-all`}>
      <div className="flex items-center gap-1.5 mb-1.5">
        <span className={iconColor}>{icon}</span>
        <span className="text-[10px] text-slate-400">{title}</span>
      </div>
      <p className="text-sm text-slate-100 font-mono font-medium truncate">
        {amount}
      </p>
      <p className="text-[10px] text-slate-500 font-mono mt-0.5">
        占比 {ratio}
      </p>
    </div>
  );
}

/**
 * 省钱分析报告页主组件。
 *
 * @param props.data SSE 返回的业务数据（含省钱分析字段）
 */
export default function EnergySavingsReportPage({ data }: RoutePageProps) {
  // 兼容多种 data 格式
  let report: SavingsReportData;
  if (Array.isArray(data)) {
    report = data[0] as SavingsReportData;
  } else if (data && Array.isArray((data as Record<string, unknown>).list)) {
    report = ((data as Record<string, unknown>).list as unknown[])[0] as SavingsReportData;
  } else {
    report = data as SavingsReportData;
  }

  /** 货币符号（默认 ¥） */
  const symbol = report.monetarySymbol || '¥';

  /** 提取节省来源（兼容 total 嵌套或顶层字段） */
  const sources = report.total ?? {};
  const highSource = sources.high as SavingsSource | undefined;
  const lowSource = sources.low as SavingsSource | undefined;
  const pvSource = sources.pv as SavingsSource | undefined;

  /** 日节省列表（最多展示前 5 条） */
  const dayList = useMemo(() => {
    const days = report.days ?? [];
    return days.slice(0, MAX_DAY_ITEMS);
  }, [report.days]);

  /** 提取 SOC（用于 3D 展示，无则用默认值） */
  const batterySoc = useMemo(() => {
    if (report.batterySoc != null) {
      const n = Number(report.batterySoc);
      if (Number.isFinite(n)) return n;
    }
    return 60;
  }, [report.batterySoc]);

  return (
    <div className="flex flex-col gap-3 animate-fade-in">
      {/* 顶部 3D 储能效果展示 */}
      <EnergyStation3D batterySoc={batterySoc} />

      {/* 标题栏 */}
      <div className="flex items-center gap-2 px-1">
        <PiggyBank size={16} className="text-neon-cyan" />
        <span className="text-sm font-semibold gradient-text">省钱分析报告</span>
      </div>

      {/* 总节省金额（突出展示） */}
      <div className="rounded-xl glass-panel border border-neon-purple/30 p-3 flex items-center gap-3">
        {/* 金额图标 */}
        <div className="flex-shrink-0 w-12 h-12 rounded-full bg-neon-purple/10 border border-neon-purple/30 flex items-center justify-center">
          <DollarSign size={24} className="text-neon-purple" />
        </div>
        {/* 金额数值 + 标签 */}
        <div className="flex-1 min-w-0">
          <p className="text-[10px] text-slate-400">总节省金额</p>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-bold text-slate-100 font-mono">
              {formatAmount(report.amount as number | string | undefined, symbol)}
            </span>
          </div>
        </div>
      </div>

      {/* 三个节省来源卡片 */}
      <div className="grid grid-cols-3 gap-2">
        {/* 高电价节省 */}
        <SourceCard
          icon={<TrendingDown size={12} />}
          iconColor="text-neon-cyan"
          borderColor="border-neon-cyan/20"
          title="高电价节省"
          amount={formatAmount(highSource?.amount, symbol)}
          ratio={formatRatio(highSource?.ratio as number | string | undefined)}
        />
        {/* 低电价节省 */}
        <SourceCard
          icon={<Zap size={12} />}
          iconColor="text-neon-purple"
          borderColor="border-neon-purple/20"
          title="低电价节省"
          amount={formatAmount(lowSource?.amount, symbol)}
          ratio={formatRatio(lowSource?.ratio as number | string | undefined)}
        />
        {/* 光伏发电节省 */}
        <SourceCard
          icon={<Sun size={12} />}
          iconColor="text-amber-400"
          borderColor="border-amber-500/20"
          title="光伏发电节省"
          amount={formatAmount(pvSource?.amount, symbol)}
          ratio={formatRatio(pvSource?.ratio as number | string | undefined)}
        />
      </div>

      {/* 总结文本（如有） */}
      {report.summary && (
        <div className="rounded-xl glass-panel border border-neon-cyan/20 p-3">
          <div className="flex items-start gap-2">
            <BarChart3 size={12} className="text-neon-cyan flex-shrink-0 mt-0.5" />
            <div>
              <p className="text-[10px] text-slate-400 mb-1">分析总结</p>
              <p className="text-xs text-slate-200 leading-relaxed">
                {report.summary}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* 日节省列表（如有，展示前 5 条） */}
      {dayList.length > 0 && (
        <div className="rounded-xl glass-panel border border-neon-purple/20 overflow-hidden">
          {/* 标题栏 */}
          <div className="flex items-center gap-1.5 px-3 py-2 border-b border-neon-purple/15 bg-white/5">
            <Calendar size={12} className="text-neon-purple" />
            <span className="text-xs font-medium text-slate-100">日节省明细</span>
            <span className="text-[10px] text-slate-500 font-mono ml-auto">
              显示前 {dayList.length} 条
            </span>
          </div>
          {/* 日节省列表 */}
          <div className="divide-y divide-white/5">
            {dayList.map((day, i) => (
              <div key={i} className="flex items-center justify-between px-3 py-2">
                <div className="flex items-center gap-2">
                  <Calendar size={11} className="text-slate-500" />
                  <span className="text-xs text-slate-300 font-mono">
                    {day.date || '—'}
                  </span>
                </div>
                <span className="text-xs text-slate-200 font-mono">
                  {formatAmount(day.amount, symbol)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
