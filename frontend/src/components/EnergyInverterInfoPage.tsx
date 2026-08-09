/**
 * 逆变器实时数据页组件
 *
 * <p>展示储能逆变器实时数据，包括电池SOC、充放电功率、光伏发电功率、负载功率等。</p>
 *
 * <p>关键字段：</p>
 * <ul>
 *   <li>batterySoc — 电池 SOC（0-100）</li>
 *   <li>batteryPowerNow — 当前电池功率（正值=充电，负值=放电，单位 kW）</li>
 *   <li>powerPvNow — 当前光伏发电功率</li>
 *   <li>powerLoadNow — 当前负载功率</li>
 *   <li>gridPhaseTypeDesc — 当前电价阶段描述（峰/平/谷）</li>
 * </ul>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { Zap, Sun, Battery, Home, Activity, TrendingUp } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import type { EnergyInverterInfoData } from '../api/client';

/** 逆变器实时数据结构 */
interface InverterInfo extends EnergyInverterInfoData {}

/**
 * 安全解析数字值。
 * @param value 原始值（可能是 string / number / undefined）
 * @returns 数字值（解析失败返回 NaN）
 */
function toNumber(value: unknown): number {
  if (value == null) return NaN;
  if (typeof value === 'number') return value;
  const n = parseFloat(String(value).trim());
  return isNaN(n) ? NaN : n;
}

/**
 * 格式化功率值（带正负号和单位）。
 * @param value 功率值
 * @param unit 单位（默认 kW）
 * @returns 格式化字符串
 */
function formatPower(value: unknown, unit = 'kW'): string {
  const n = toNumber(value);
  if (isNaN(n)) return '—';
  const sign = n > 0 ? '+' : '';
  return `${sign}${n.toFixed(2)} ${unit}`;
}

/**
 * 格式化数值（不带符号）。
 * @param value 数值
 * @param unit 单位
 * @returns 格式化字符串
 */
function formatValue(value: unknown, unit = ''): string {
  const n = toNumber(value);
  if (isNaN(n)) return '—';
  return `${n.toFixed(1)}${unit ? ' ' + unit : ''}`;
}

/**
 * 获取功率状态标签（充电/放电/空闲）。
 * @param power 电池功率
 * @returns 状态标签和颜色
 */
function getPowerStatus(power: unknown): { label: string; color: string } {
  const n = toNumber(power);
  if (isNaN(n)) return { label: '未知', color: 'text-slate-400' };
  if (n > 0.05) return { label: '充电中', color: 'text-neon-cyan' };
  if (n < -0.05) return { label: '放电中', color: 'text-neon-purple' };
  return { label: '空闲', color: 'text-slate-400' };
}

/** 信息行组件 Props */
interface InfoRowProps {
  icon: React.ReactNode;
  label: string;
  value?: string | number | null;
  valueClassName?: string;
}

/** 信息行组件 */
function InfoRow({ icon, label, value, valueClassName }: InfoRowProps) {
  return (
    <div className="flex items-center gap-2 py-1.5">
      <span className="text-slate-500 flex-shrink-0">{icon}</span>
      <span className="text-[11px] text-slate-400 flex-shrink-0 w-20">{label}</span>
      <span className={`text-xs font-mono flex-1 truncate text-right ${valueClassName ?? 'text-slate-200'}`}>
        {value ?? '—'}
      </span>
    </div>
  );
}

/** 信息卡片组件 */
function InfoCard({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="rounded-xl glass-panel border border-neon-cyan/20 overflow-hidden">
      <div className="flex items-center gap-1.5 px-3 py-2 border-b border-neon-purple/15 bg-white/5">
        <span className="text-neon-cyan">{icon}</span>
        <span className="text-xs font-medium text-slate-100">{title}</span>
      </div>
      <div className="px-3 py-1">{children}</div>
    </div>
  );
}

/**
 * 逆变器实时数据页主组件。
 * @param props.data SSE 返回的逆变器数据
 */
export default function EnergyInverterInfoPage({ data }: RoutePageProps) {
  // 兼容多种 data 格式
  let info: InverterInfo;
  if (Array.isArray(data)) {
    info = data[0] as InverterInfo;
  } else if (data && Array.isArray((data as Record<string, unknown>).list)) {
    info = ((data as Record<string, unknown>).list as unknown[])[0] as InverterInfo;
  } else {
    info = data as InverterInfo;
  }

  // 电池功率状态
  const powerStatus = getPowerStatus(info.batteryPowerNow);
  // 电池 SOC 数值
  const socValue = toNumber(info.batterySoc);

  return (
    <div className="flex flex-col gap-3 animate-fade-in">
      {/* 标题 */}
      <div className="flex items-center gap-2 px-1">
        <Activity size={16} className="text-neon-cyan" />
        <span className="text-sm font-semibold gradient-text">逆变器实时数据</span>
        {/* 充放电状态标签 */}
        <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full border bg-white/5 ${powerStatus.color} border-current/30`}>
          {powerStatus.label}
        </span>
      </div>

      {/* 电池状态（核心） */}
      <InfoCard title="电池状态" icon={<Battery size={12} />}>
        {/* 电池 SOC 进度条 */}
        {!isNaN(socValue) && (
          <div className="py-2">
            <div className="flex items-center justify-between mb-1">
              <span className="text-[11px] text-slate-400">电池 SOC</span>
              <span className="text-xs font-mono text-neon-cyan">{socValue.toFixed(1)}%</span>
            </div>
            <div className="h-2 bg-slate-700/50 rounded-full overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-500"
                style={{
                  width: `${Math.max(0, Math.min(100, socValue))}%`,
                  background: socValue > 50
                    ? 'linear-gradient(90deg, #06b6d4, #22d3ee)'
                    : socValue > 20
                    ? 'linear-gradient(90deg, #f59e0b, #fbbf24)'
                    : 'linear-gradient(90deg, #ef4444, #f87171)',
                }}
              />
            </div>
          </div>
        )}
        <InfoRow
          icon={<Zap size={11} />}
          label="电池功率"
          value={formatPower(info.batteryPowerNow)}
          valueClassName={powerStatus.color}
        />
      </InfoCard>

      {/* 功率信息 */}
      <InfoCard title="功率信息" icon={<Zap size={12} />}>
        <InfoRow
          icon={<Sun size={11} />}
          label="光伏功率"
          value={formatPower(info.powerPvNow)}
          valueClassName="text-neon-cyan"
        />
        <InfoRow
          icon={<Home size={11} />}
          label="负载功率"
          value={formatPower(info.powerLoadNow)}
          valueClassName="text-orange-400"
        />
        <InfoRow
          icon={<Zap size={11} />}
          label="电表功率"
          value={formatPower(info.powerRNow)}
        />
      </InfoCard>

      {/* 发电量信息 */}
      <InfoCard title="发电量信息" icon={<TrendingUp size={12} />}>
        <InfoRow
          icon={<Sun size={11} />}
          label="今日发电"
          value={formatValue(info.totalElectricityPvToday, 'kWh')}
          valueClassName="text-neon-cyan"
        />
        <InfoRow
          icon={<Home size={11} />}
          label="今日用电"
          value={formatValue(info.totalElectricityToday, 'kWh')}
        />
      </InfoCard>

      {/* 电价阶段 */}
      {info.gridPhaseTypeDesc && (
        <div className="flex items-center gap-2 px-3 py-2 rounded-xl glass-panel border border-neon-purple/20">
          <Zap size={12} className="text-neon-purple" />
          <span className="text-[11px] text-slate-400">当前电价阶段</span>
          <span className="text-xs font-mono text-neon-purple ml-auto">
            {info.gridPhaseTypeDesc}
          </span>
        </div>
      )}
    </div>
  );
}
