/**
 * 储能故障排查页组件（全量扫描模式）
 *
 * <p>展示全量扫描所有电站后的故障诊断结果，包括：</p>
 * <ul>
 *   <li>扫描汇总：共扫描 N 个电站，发现 M 个故障</li>
 *   <li>故障电站列表：每个电站一张卡片，显示电站名称 + 故障描述 + SOC 对比 + 状态对比</li>
 *   <li>全部正常时显示绿色"运行正常"提示</li>
 * </ul>
 *
 * <p>诊断规则：</p>
 * <ul>
 *   <li>备电SOC > 电池SOC → 应放电（batteryPowerNow 应为负值）</li>
 *   <li>备电SOC < 电池SOC → 应充电（batteryPowerNow 应为正值）</li>
 *   <li>实际状态与预期不符 → 故障</li>
 * </ul>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { AlertTriangle, CheckCircle, Zap, Battery, Shield, Activity, Sun, Home } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import type { EnergyFaultDiagnosisData, FaultStationItem, EnergyInverterInfoData } from '../api/client';

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

/**
 * 安全解析数字值。
 */
function toNumber(value: unknown): number {
  if (value == null) return NaN;
  if (typeof value === 'number') return value;
  const n = parseFloat(String(value).trim());
  return isNaN(n) ? NaN : n;
}

/**
 * 格式化功率值（带正负号和单位）。
 */
function formatPower(value: unknown): string {
  const n = toNumber(value);
  if (isNaN(n)) return '—';
  const sign = n > 0 ? '+' : '';
  return `${sign}${n.toFixed(2)} kW`;
}

/**
 * 单个故障电站卡片组件。
 *
 * @param station 故障电站诊断结果
 * @param index 电站序号（用于显示编号）
 */
function FaultStationCard({ station, index }: { station: FaultStationItem; index: number }) {
  const batterySoc = toNumber(station.batterySoc);
  const backupSoc = toNumber(station.backupSoc);
  const batteryPowerNow = toNumber(station.batteryPowerNow);
  const inverterData = (station.inverterData ?? {}) as EnergyInverterInfoData;
  const homeName = station.homeName || '未命名电站';

  return (
    <div className="rounded-xl glass-panel border border-red-500/30 overflow-hidden">
      {/* ===== 电站名称 + 故障标识 ===== */}
      <div className="flex items-center gap-2 px-3 py-2.5 bg-red-500/10 border-b border-red-500/20">
        <AlertTriangle size={16} className="text-red-400 flex-shrink-0" />
        <Home size={13} className="text-red-300 flex-shrink-0" />
        <span className="text-sm font-semibold text-red-300 truncate flex-1">
          {index + 1}. {homeName}
        </span>
        <span className="text-[10px] px-1.5 py-0.5 rounded bg-red-500/20 text-red-400 flex-shrink-0">
          故障
        </span>
      </div>

      {/* ===== 故障描述 ===== */}
      <div className="px-3 py-2 border-b border-white/5">
        <div className="text-[11px] text-slate-300 leading-relaxed break-all">
          {station.faultDesc || '—'}
        </div>
      </div>

      {/* ===== SOC 对比 ===== */}
      <div className="px-3 py-2 border-b border-white/5">
        {/* 电池 SOC */}
        {!isNaN(batterySoc) && batterySoc >= 0 && (
          <div className="py-1">
            <div className="flex items-center justify-between mb-0.5">
              <span className="text-[11px] text-slate-400">电池 SOC</span>
              <span className="text-xs font-mono text-neon-cyan">{batterySoc.toFixed(1)}%</span>
            </div>
            <div className="h-1.5 bg-slate-700/50 rounded-full overflow-hidden">
              <div
                className="h-full rounded-full bg-gradient-to-r from-cyan-500 to-cyan-300 transition-all duration-500"
                style={{ width: `${Math.max(0, Math.min(100, batterySoc))}%` }}
              />
            </div>
          </div>
        )}
        {/* 备电 SOC */}
        {!isNaN(backupSoc) && backupSoc >= 0 && (
          <div className="py-1">
            <div className="flex items-center justify-between mb-0.5">
              <span className="text-[11px] text-slate-400">备电 SOC</span>
              <span className="text-xs font-mono text-neon-purple">{backupSoc.toFixed(1)}%</span>
            </div>
            <div className="h-1.5 bg-slate-700/50 rounded-full overflow-hidden">
              <div
                className="h-full rounded-full bg-gradient-to-r from-purple-500 to-purple-300 transition-all duration-500"
                style={{ width: `${Math.max(0, Math.min(100, backupSoc))}%` }}
              />
            </div>
          </div>
        )}
        {((isNaN(batterySoc) || batterySoc < 0) && (isNaN(backupSoc) || backupSoc < 0)) && (
          <div className="py-1 text-center text-[11px] text-slate-500">暂无 SOC 数据</div>
        )}
      </div>

      {/* ===== 状态对比 + 逆变器数据 ===== */}
      <div className="px-3 py-1">
        <InfoRow
          icon={<Zap size={11} />}
          label="预期动作"
          value={station.expectedAction}
          valueClassName="text-neon-cyan"
        />
        <InfoRow
          icon={<Activity size={11} />}
          label="实际状态"
          value={station.actualState}
          valueClassName="text-red-400"
        />
        <InfoRow
          icon={<Zap size={11} />}
          label="电池功率"
          value={formatPower(batteryPowerNow)}
          valueClassName={batteryPowerNow > 0 ? 'text-neon-cyan' : batteryPowerNow < 0 ? 'text-neon-purple' : 'text-slate-400'}
        />
        {/* 逆变器实时数据（透传展示） */}
        {inverterData && Object.keys(inverterData).length > 0 && (
          <>
            <InfoRow
              icon={<Sun size={11} />}
              label="光伏功率"
              value={formatPower(inverterData.powerPvNow)}
              valueClassName="text-neon-cyan"
            />
            <InfoRow
              icon={<Zap size={11} />}
              label="负载功率"
              value={formatPower(inverterData.powerLoadNow)}
              valueClassName="text-orange-400"
            />
            {inverterData.gridPhaseTypeDesc && (
              <InfoRow
                icon={<Zap size={11} />}
                label="电价阶段"
                value={inverterData.gridPhaseTypeDesc}
                valueClassName="text-neon-purple"
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}

/**
 * 储能故障排查页主组件（全量扫描模式）。
 * @param props.data SSE 返回的故障诊断结果（含 faultStations 列表）
 */
export default function EnergyFaultDiagnosisPage({ data }: RoutePageProps) {
  // 解析全量扫描结果
  const diagnosis = data as EnergyFaultDiagnosisData;
  const faultStations: FaultStationItem[] = diagnosis.faultStations ?? [];
  const totalStations = toNumber(diagnosis.totalStations);
  const faultCount = toNumber(diagnosis.faultCount);
  const allNormal = diagnosis.allNormal === true || faultStations.length === 0;

  return (
    <div className="flex flex-col gap-3 animate-fade-in">
      {/* 标题 */}
      <div className="flex items-center gap-2 px-1">
        <Activity size={16} className="text-neon-cyan" />
        <span className="text-sm font-semibold gradient-text">故障排查结果</span>
      </div>

      {/* ===== 扫描汇总 ===== */}
      <div className={`rounded-xl glass-panel border overflow-hidden ${
        allNormal ? 'border-green-500/40' : 'border-orange-500/40'
      }`}>
        <div className={`flex items-center gap-2 px-3 py-3 ${
          allNormal ? 'bg-green-500/10' : 'bg-orange-500/10'
        }`}>
          {allNormal ? (
            <CheckCircle size={20} className="text-green-400 flex-shrink-0" />
          ) : (
            <AlertTriangle size={20} className="text-orange-400 flex-shrink-0" />
          )}
          <div className="flex-1 min-w-0">
            <div className={`text-sm font-semibold ${allNormal ? 'text-green-400' : 'text-orange-400'}`}>
              {allNormal ? '全部正常运行' : `发现 ${isNaN(faultCount) ? faultStations.length : faultCount} 个故障电站`}
            </div>
            <div className="text-[11px] text-slate-300 mt-0.5">
              {isNaN(totalStations)
                ? '扫描完成'
                : `共扫描 ${totalStations} 个电站，故障 ${isNaN(faultCount) ? faultStations.length : faultCount} 个`}
            </div>
          </div>
        </div>
      </div>

      {/* ===== 故障电站列表 ===== */}
      {faultStations.length > 0 ? (
        <div className="flex flex-col gap-2.5">
          {faultStations.map((station, idx) => (
            <FaultStationCard key={station.homeId ?? idx} station={station} index={idx} />
          ))}
        </div>
      ) : (
        /* ===== 全部正常时的 SOC 示意图 ===== */
        <div className="rounded-xl glass-panel border border-green-500/30 overflow-hidden">
          <div className="flex items-center gap-1.5 px-3 py-2 border-b border-neon-purple/15 bg-white/5">
            <Shield size={12} className="text-green-400" />
            <span className="text-xs font-medium text-slate-100">诊断结论</span>
          </div>
          <div className="px-3 py-3">
            <div className="flex items-center gap-2">
              <CheckCircle size={14} className="text-green-400 flex-shrink-0" />
              <span className="text-xs text-green-300">
                所有电站电池充放电状态正常，未发现故障
              </span>
            </div>
            <div className="mt-2 flex items-center gap-2">
              <Battery size={14} className="text-neon-cyan flex-shrink-0" />
              <span className="text-[11px] text-slate-400">
                诊断规则：备电SOC &gt; 电池SOC 应放电，备电SOC &lt; 电池SOC 应充电
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
