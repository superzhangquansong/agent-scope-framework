/**
 * 储能电站详情页组件
 *
 * <p>展示单个储能电站的详细信息，包括基本信息、电气参数、地址信息及分时电价。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>顶部嵌入 EnergyStation3D 储能 3D 效果展示</li>
 *   <li>使用 glass-panel 卡片分组展示：基本信息、电气参数、地址信息</li>
 *   <li>分时电价列表（如有）单独展示</li>
 *   <li>科幻毛玻璃风格，霓虹紫青配色</li>
 * </ul>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { useMemo } from 'react';
import { Home, Zap, MapPin, Calendar, DollarSign, Clock, Cpu, Sun, ArrowLeft } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import EnergyStation3D from './EnergyStation3D';

/** 详情页扩展 Props（支持可选的返回回调） */
interface DetailPageProps extends RoutePageProps {
  /** 返回回调（列表页内联展示时传入，点击返回按钮回到列表） */
  onBack?: () => void;
}

/**
 * 电站类型映射常量。
 * <p>后端返回的类型码 → 中文标签。</p>
 * <ul>
 *   <li>5 = 逆变器</li>
 *   <li>6 = BMS（电池管理系统）</li>
 * </ul>
 */
const STATION_TYPE_MAP: Record<number, string> = {
  5: '逆变器',
  6: 'BMS',
};

/** 分时电价数据结构 */
interface TimeOfUsePricing {
  /** 时段开始时间（如 "08:00"） */
  startTime?: string;
  /** 时段结束时间（如 "12:00"） */
  endTime?: string;
  /** 电价 */
  price?: number | string;
  /** 电价类型（如 "峰"/"平"/"谷"） */
  type?: string;
  [k: string]: unknown;
}

/** 电站详情数据结构 */
interface EnergyStationDetail {
  /** 电站名称 */
  homeName?: string;
  /** 电站类型码（5=逆变器/6=BMS） */
  powerStationType?: number;
  /** 并网类型文本（如 "单相"/"三相"） */
  gridTypeText?: string;
  /** 装机容量（如 "10 kW"） */
  installedCapacity?: string;
  /** 投产日期（如 "2024-01-15"） */
  productionTime?: string;
  /** 电价（如 0.6） */
  electrovalence?: number | string;
  /** 货币单位文本（如 "元"/"CNY"） */
  monetaryUnitText?: string;
  /** 地址 */
  address?: string;
  /** 创建时间（如 "2024-01-10 10:30:00"） */
  createTime?: string;
  /** 电池 SOC 电量百分比（0-100，用于 3D 展示） */
  batterySoc?: number;
  /** 发电功率（用于 3D 展示） */
  power?: string;
  /** 今日发电量（用于 3D 展示） */
  todayElectricity?: string;
  /** 分时电价列表 */
  timeOfUsePricings?: TimeOfUsePricing[];
  [k: string]: unknown;
}

/**
 * 获取电站类型中文标签。
 * @param type 类型码
 * @returns 中文标签（未知类型返回"未知"）
 */
function getStationTypeLabel(type?: number): string {
  if (type == null) return '未知';
  return STATION_TYPE_MAP[type] ?? '未知';
}

/**
 * 格式化电价显示（电价 + 货币单位）。
 * @param detail 电站详情
 * @returns 格式化后的电价字符串
 */
function formatElectrovalence(detail: EnergyStationDetail): string {
  if (detail.electrovalence == null) return '—';
  const unit = detail.monetaryUnitText || '元';
  return `${detail.electrovalence} ${unit}/kWh`;
}

/** 信息行组件 Props */
interface InfoRowProps {
  /** 图标 */
  icon: React.ReactNode;
  /** 标签 */
  label: string;
  /** 值 */
  value?: string | number | null;
}

/**
 * 信息行组件（图标 + 标签 + 值）。
 */
function InfoRow({ icon, label, value }: InfoRowProps) {
  return (
    <div className="flex items-center gap-2 py-1.5">
      <span className="text-slate-500 flex-shrink-0">{icon}</span>
      <span className="text-[11px] text-slate-400 flex-shrink-0 w-20">{label}</span>
      <span className="text-xs text-slate-200 font-mono flex-1 truncate text-right">
        {value ?? '—'}
      </span>
    </div>
  );
}

/** 信息卡片组件 Props */
interface InfoCardProps {
  /** 卡片标题 */
  title: string;
  /** 标题图标 */
  icon: React.ReactNode;
  /** 卡片内容 */
  children: React.ReactNode;
}

/**
 * 信息卡片组件（标题 + 内容区）。
 */
function InfoCard({ title, icon, children }: InfoCardProps) {
  return (
    <div className="rounded-xl glass-panel border border-neon-cyan/20 overflow-hidden">
      {/* 卡片标题栏 */}
      <div className="flex items-center gap-1.5 px-3 py-2 border-b border-neon-purple/15 bg-white/5">
        <span className="text-neon-cyan">{icon}</span>
        <span className="text-xs font-medium text-slate-100">{title}</span>
      </div>
      {/* 卡片内容 */}
      <div className="px-3 py-1">
        {children}
      </div>
    </div>
  );
}

/**
 * 储能电站详情页主组件。
 *
 * @param props.data SSE 返回的业务数据（含电站详情字段）
 */
export default function EnergyStationDetailPage({ data, onBack }: DetailPageProps) {
  // 兼容多种 data 格式：直接对象 / {list: [对象]} / 数组取第一个
  let detail: EnergyStationDetail;
  if (Array.isArray(data)) {
    detail = data[0] as EnergyStationDetail;
  } else if (data && Array.isArray((data as Record<string, unknown>).list)) {
    detail = ((data as Record<string, unknown>).list as unknown[])[0] as EnergyStationDetail;
  } else {
    detail = data as EnergyStationDetail;
  }

  /** 提取 SOC（用于 3D 展示，无则用默认值 60） */
  const batterySoc = useMemo(() => {
    if (detail.batterySoc != null) {
      const n = Number(detail.batterySoc);
      if (Number.isFinite(n)) return n;
    }
    return 60;
  }, [detail.batterySoc]);

  /** 分时电价列表 */
  const pricings = detail.timeOfUsePricings ?? [];

  return (
    <div className="flex flex-col gap-3 animate-fade-in">
      {/* 顶部 3D 储能效果展示 */}
      <EnergyStation3D
        power={detail.power}
        batterySoc={batterySoc}
        todayElectricity={detail.todayElectricity}
      />

      {/* 返回按钮（仅列表页内联展示时显示） */}
      {onBack && (
        <button
          onClick={onBack}
          className="flex items-center gap-1 text-xs text-slate-400 hover:text-neon-cyan transition-colors px-1"
        >
          <ArrowLeft size={12} />
          返回电站列表
        </button>
      )}

      {/* 电站名称标题 */}
      <div className="flex items-center gap-2 px-1">
        <Home size={16} className="text-neon-cyan" />
        <span className="text-sm font-semibold gradient-text">
          {detail.homeName || '储能电站详情'}
        </span>
        <span className="text-[10px] text-slate-500 bg-slate-700/40 px-1.5 py-0.5 rounded-full border border-slate-600/30 font-mono">
          {getStationTypeLabel(detail.powerStationType)}
        </span>
      </div>

      {/* 基本信息 */}
      <InfoCard title="基本信息" icon={<Cpu size={12} />}>
        <InfoRow
          icon={<Home size={11} />}
          label="电站名称"
          value={detail.homeName}
        />
        <InfoRow
          icon={<Cpu size={11} />}
          label="电站类型"
          value={getStationTypeLabel(detail.powerStationType)}
        />
        <InfoRow
          icon={<Calendar size={11} />}
          label="投产日期"
          value={detail.productionTime}
        />
        <InfoRow
          icon={<Clock size={11} />}
          label="创建时间"
          value={detail.createTime}
        />
      </InfoCard>

      {/* 电气参数 */}
      <InfoCard title="电气参数" icon={<Zap size={12} />}>
        <InfoRow
          icon={<Sun size={11} />}
          label="装机容量"
          value={detail.installedCapacity}
        />
        <InfoRow
          icon={<Zap size={11} />}
          label="并网类型"
          value={detail.gridTypeText}
        />
        <InfoRow
          icon={<DollarSign size={11} />}
          label="电价"
          value={formatElectrovalence(detail)}
        />
      </InfoCard>

      {/* 地址信息 */}
      <InfoCard title="地址信息" icon={<MapPin size={12} />}>
        <InfoRow
          icon={<MapPin size={11} />}
          label="安装地址"
          value={detail.address}
        />
      </InfoCard>

      {/* 分时电价列表（如有） */}
      {pricings.length > 0 && (
        <div className="rounded-xl glass-panel border border-neon-purple/20 overflow-hidden">
          {/* 标题栏 */}
          <div className="flex items-center gap-1.5 px-3 py-2 border-b border-neon-purple/15 bg-white/5">
            <DollarSign size={12} className="text-neon-purple" />
            <span className="text-xs font-medium text-slate-100">分时电价</span>
            <span className="text-[10px] text-slate-500 font-mono ml-auto">
              {pricings.length} 个时段
            </span>
          </div>
          {/* 电价列表 */}
          <div className="divide-y divide-white/5">
            {pricings.map((p, i) => (
              <div key={i} className="flex items-center justify-between px-3 py-2">
                <div className="flex items-center gap-2">
                  <Clock size={11} className="text-slate-500" />
                  <span className="text-xs text-slate-300 font-mono">
                    {p.startTime || '—'} ~ {p.endTime || '—'}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  {p.type && (
                    <span className="text-[10px] text-neon-cyan bg-neon-cyan/10 px-1.5 py-0.5 rounded-full border border-neon-cyan/20 font-mono">
                      {p.type}
                    </span>
                  )}
                  <span className="text-xs text-slate-200 font-mono">
                    {p.price ?? '—'} {detail.monetaryUnitText || '元'}/kWh
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
