/**
 * 电池使用报告页组件
 *
 * <p>展示储能电池使用报告，包括评分、充放电量、光伏充电占比、利用率、循环次数等关键指标。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>顶部嵌入 EnergyStation3D 储能 3D 效果展示</li>
 *   <li>评分卡片突出展示（评分 + 描述）</li>
 *   <li>数据卡片网格布局（2 列），每个数据项一个 glass 小卡片</li>
 *   <li>充电来源展示（电网充电 + 光伏充电）</li>
 *   <li>科幻毛玻璃风格，霓虹紫青配色</li>
 * </ul>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { useMemo } from 'react';
import {
  Star,
  BatteryCharging,
  Battery,
  Sun,
  Gauge,
  Repeat,
  Zap,
  Activity,
  TrendingUp,
} from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import EnergyStation3D from './EnergyStation3D';

/** 充电来源数据结构 */
interface ElectricitySource {
  /** 电网充电量 */
  grid?: number | string;
  /** 光伏充电量 */
  pv?: number | string;
  [k: string]: unknown;
}

/** 电池报告数据结构 */
interface BatteryReportData {
  /** 评分（0-100 或 0-5） */
  score?: number | string;
  /** 评分描述（如 "优秀"/"良好"） */
  scoreDesc?: string;
  /** 充电量（如 "50.2 kWh"） */
  chargeElectricity?: number | string;
  /** 放电量（如 "45.8 kWh"） */
  dischargeElectricity?: number | string;
  /** 光伏充电量（如 "30.5 kWh"） */
  chargeElectricityByPv?: number | string;
  /** 光伏充电占比（如 "60%" 或 0.6） */
  chargeElectricityByPvRatio?: number | string;
  /** 额定能量（如 "10 kWh"） */
  rateCapacity?: number | string;
  /** 利用率（如 "85%" 或 0.85） */
  useRatio?: number | string;
  /** 循环次数（兼容字段 cycle） */
  cycle?: number | string;
  /** 循环次数（兼容字段 cycleTimes） */
  cycleTimes?: number | string;
  /** 充电来源明细 */
  electricitySource?: ElectricitySource;
  /** 电池 SOC 电量百分比（用于 3D 展示） */
  batterySoc?: number;
  [k: string]: unknown;
}

/**
 * 将值格式化为显示字符串。
 * <p>兼容数字和字符串，null/undefined 返回 "—"。</p>
 * @param value 值
 * @param suffix 后缀（如 "kWh"）
 * @returns 格式化后的字符串
 */
function formatValue(value: unknown, suffix?: string): string {
  if (value == null || value === '') return '—';
  const str = String(value);
  if (suffix) return `${str} ${suffix}`;
  return str;
}

/**
 * 格式化百分比显示。
 * <p>兼容小数（0.6 → 60%）和已带百分号的字符串（"60%"）。</p>
 * @param ratio 比率值
 * @returns 格式化后的百分比字符串
 */
function formatRatio(ratio: number | string | undefined): string {
  if (ratio == null) return '—';
  if (typeof ratio === 'string') {
    // 已包含 % 符号则直接返回
    if (ratio.includes('%')) return ratio;
    // 尝试解析为数字
    const n = parseFloat(ratio);
    if (!isNaN(n)) {
      return n <= 1 ? `${(n * 100).toFixed(1)}%` : `${n.toFixed(1)}%`;
    }
    return ratio;
  }
  // 数字：<=1 视为小数比率，>1 视为已乘 100 的百分比
  return ratio <= 1 ? `${(ratio * 100).toFixed(1)}%` : `${ratio.toFixed(1)}%`;
}

/** 数据卡片 Props */
interface DataCardProps {
  /** 图标 */
  icon: React.ReactNode;
  /** 图标颜色类名 */
  iconColor: string;
  /** 标签 */
  label: string;
  /** 值 */
  value: string;
}

/**
 * 数据卡片组件（glass 小卡片，用于网格布局中的单个数据项）。
 */
function DataCard({ icon, iconColor, label, value }: DataCardProps) {
  return (
    <div className="rounded-lg glass border border-neon-cyan/15 p-2.5 hover-neon transition-all">
      <div className="flex items-center gap-1.5 mb-1">
        <span className={iconColor}>{icon}</span>
        <span className="text-[10px] text-slate-400">{label}</span>
      </div>
      <p className="text-sm text-slate-100 font-mono font-medium truncate">
        {value}
      </p>
    </div>
  );
}

/**
 * 电池使用报告页主组件。
 *
 * @param props.data SSE 返回的业务数据（含电池报告字段）
 */
export default function EnergyBatteryReportPage({ data }: RoutePageProps) {
  // 兼容多种 data 格式
  let report: BatteryReportData;
  if (Array.isArray(data)) {
    report = data[0] as BatteryReportData;
  } else if (data && Array.isArray((data as Record<string, unknown>).list)) {
    report = ((data as Record<string, unknown>).list as unknown[])[0] as BatteryReportData;
  } else {
    report = data as BatteryReportData;
  }

  /** 提取循环次数（兼容 cycle / cycleTimes 字段） */
  const cycleTimes = useMemo(() => {
    const val = report.cycleTimes ?? report.cycle;
    return formatValue(val, '次');
  }, [report.cycle, report.cycleTimes]);

  /** 提取 SOC（用于 3D 展示，无则用默认值） */
  const batterySoc = useMemo(() => {
    if (report.batterySoc != null) {
      const n = Number(report.batterySoc);
      if (Number.isFinite(n)) return n;
    }
    return 60;
  }, [report.batterySoc]);

  /** 充电来源明细 */
  const source = report.electricitySource ?? {};

  return (
    <div className="flex flex-col gap-3 animate-fade-in">
      {/* 顶部 3D 储能效果展示 */}
      <EnergyStation3D batterySoc={batterySoc} />

      {/* 标题栏 */}
      <div className="flex items-center gap-2 px-1">
        <BatteryCharging size={16} className="text-neon-cyan" />
        <span className="text-sm font-semibold gradient-text">电池使用报告</span>
      </div>

      {/* 评分卡片（突出展示） */}
      <div className="rounded-xl glass-panel border border-neon-purple/30 p-3 flex items-center gap-3">
        {/* 评分图标 */}
        <div className="flex-shrink-0 w-12 h-12 rounded-full bg-neon-purple/10 border border-neon-purple/30 flex items-center justify-center">
          <Star size={24} className="text-neon-purple" />
        </div>
        {/* 评分数值 + 描述 */}
        <div className="flex-1 min-w-0">
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-bold text-slate-100 font-mono">
              {formatValue(report.score)}
            </span>
            <span className="text-xs text-slate-400">分</span>
          </div>
          <p className="text-xs text-neon-cyan mt-0.5">
            {report.scoreDesc || '暂无评分描述'}
          </p>
        </div>
      </div>

      {/* 数据卡片网格（2 列） */}
      <div className="grid grid-cols-2 gap-2">
        {/* 充电量 */}
        <DataCard
          icon={<BatteryCharging size={12} />}
          iconColor="text-neon-cyan"
          label="充电量"
          value={formatValue(report.chargeElectricity, 'kWh')}
        />
        {/* 放电量 */}
        <DataCard
          icon={<Battery size={12} />}
          iconColor="text-neon-purple"
          label="放电量"
          value={formatValue(report.dischargeElectricity, 'kWh')}
        />
        {/* 光伏充电量 */}
        <DataCard
          icon={<Sun size={12} />}
          iconColor="text-amber-400"
          label="光伏充电量"
          value={formatValue(report.chargeElectricityByPv, 'kWh')}
        />
        {/* 光伏充电占比 */}
        <DataCard
          icon={<TrendingUp size={12} />}
          iconColor="text-amber-400"
          label="光伏充电占比"
          value={formatRatio(report.chargeElectricityByPvRatio as number | string | undefined)}
        />
        {/* 额定能量 */}
        <DataCard
          icon={<Zap size={12} />}
          iconColor="text-neon-cyan"
          label="额定能量"
          value={formatValue(report.rateCapacity, 'kWh')}
        />
        {/* 利用率 */}
        <DataCard
          icon={<Gauge size={12} />}
          iconColor="text-neon-purple"
          label="利用率"
          value={formatRatio(report.useRatio as number | string | undefined)}
        />
        {/* 循环次数 */}
        <DataCard
          icon={<Repeat size={12} />}
          iconColor="text-neon-cyan"
          label="循环次数"
          value={cycleTimes}
        />
      </div>

      {/* 充电来源明细 */}
      {report.electricitySource && (
        <div className="rounded-xl glass-panel border border-neon-cyan/20 overflow-hidden">
          {/* 标题栏 */}
          <div className="flex items-center gap-1.5 px-3 py-2 border-b border-neon-purple/15 bg-white/5">
            <Activity size={12} className="text-neon-cyan" />
            <span className="text-xs font-medium text-slate-100">充电来源</span>
          </div>
          {/* 来源明细 */}
          <div className="grid grid-cols-2 gap-2 p-3">
            {/* 电网充电 */}
            <div className="flex items-center gap-2 rounded-lg bg-white/5 p-2">
              <Zap size={14} className="text-neon-cyan flex-shrink-0" />
              <div className="min-w-0">
                <p className="text-[10px] text-slate-500">电网充电</p>
                <p className="text-xs text-slate-200 font-mono truncate">
                  {formatValue(source.grid, 'kWh')}
                </p>
              </div>
            </div>
            {/* 光伏充电 */}
            <div className="flex items-center gap-2 rounded-lg bg-white/5 p-2">
              <Sun size={14} className="text-amber-400 flex-shrink-0" />
              <div className="min-w-0">
                <p className="text-[10px] text-slate-500">光伏充电</p>
                <p className="text-xs text-slate-200 font-mono truncate">
                  {formatValue(source.pv, 'kWh')}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
