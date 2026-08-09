/**
 * 储能电站列表页组件
 *
 * <p>展示储能电站查询结果，以卡片列表形式呈现，支持点击电站卡片查看详情。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>顶部嵌入 EnergyStation3D 储能 3D 效果展示</li>
 *   <li>每个电站卡片显示：电站名称、发电功率、今日发电量、电池容量、逆变器功率、电站状态</li>
 *   <li>点击电站卡片 → 调用 getEnergyStationDetail REST API → 内联展示 EnergyStationDetailPage</li>
 *   <li>科幻毛玻璃风格，霓虹紫青配色</li>
 *   <li>客户端分页（每页 5 条）</li>
 *   <li>空结果时显示友好提示</li>
 * </ul>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { useState, useMemo } from 'react';
import { Zap, Battery, Sun, Activity, RefreshCw, Cpu, ChevronRight } from 'lucide-react';
import { Pagination } from '../shared/components';
import type { RoutePageProps } from './DynamicPage';
import EnergyStation3D from './EnergyStation3D';
import EnergyStationDetailPage from './EnergyStationDetailPage';
import { getEnergyStationDetail, type EnergyStationDetailData } from '../api/client';

/** 每页条数（前端客户端分页） */
const PAGE_SIZE = 5;

/**
 * 电站状态映射常量。
 * <p>后端返回的状态码 → 中文标签。</p>
 * <ul>
 *   <li>2 = 待安装</li>
 *   <li>3 = 已交付</li>
 *   <li>4 = 已交付</li>
 * </ul>
 */
const STATION_STATUS_MAP: Record<number, string> = {
  2: '待安装',
  3: '已交付',
  4: '已交付',
};

/** 电站数据结构（对应后端返回字段） */
interface EnergyStation {
  /** 电站 ID */
  homeId?: string | number;
  /** 电站名称 */
  homeName?: string;
  /** 发电功率（如 "5.2 kW"） */
  power?: string;
  /** 今日发电量（如 "32.5 kWh"） */
  todayElectricity?: string;
  /** 电池容量（如 "10 kWh"） */
  batteryCapacity?: string;
  /** 逆变器功率（如 "5 kW"） */
  invPower?: string;
  /** 电站状态码（2/3/4） */
  powerStationStatus?: number;
  /** 电池 SOC 电量百分比（0-100，用于 3D 展示） */
  batterySoc?: number;
  [k: string]: unknown;
}

/** 列表数据结构（兼容多种字段名） */
interface EnergyStationListData {
  /** SSE 通道外层字段 */
  list?: EnergyStation[];
  /** 兼容字段 records（MyBatis-Plus PageVO 默认字段） */
  records?: EnergyStation[];
  /** 兼容字段 stations */
  stations?: EnergyStation[];
  total?: number;
  pageNo?: number;
  pageSize?: number;
  [k: string]: unknown;
}

/**
 * 从 SSE data 中提取电站列表（兼容 list/records/stations 多种字段名）。
 * @param data SSE 返回的业务数据
 * @returns 电站列表数组
 */
function extractStationList(data: Record<string, unknown>): EnergyStation[] {
  const raw = data as EnergyStationListData;
  if (Array.isArray(raw.list)) return raw.list;
  if (Array.isArray(raw.records)) return raw.records;
  if (Array.isArray(raw.stations)) return raw.stations;
  return [];
}

/**
 * 获取电站状态中文标签。
 * @param status 状态码
 * @returns 中文标签（未知状态返回"未知"）
 */
function getStatusLabel(status?: number): string {
  if (status == null) return '未知';
  return STATION_STATUS_MAP[status] ?? '未知';
}

/**
 * 获取电站状态对应的样式类名。
 * @param status 状态码
 * @returns Tailwind 样式类名
 */
function getStatusStyle(status?: number): string {
  if (status === 2) return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
  if (status === 3 || status === 4) return 'bg-neon-cyan/10 text-neon-cyan border-neon-cyan/20';
  return 'bg-slate-700/40 text-slate-300 border-slate-600/30';
}

/**
 * 单个电站卡片组件。
 *
 * @param station 电站数据
 * @param index 卡片索引（用于动画延迟）
 * @param onClick 点击卡片回调
 * @param loading 是否正在加载详情
 */
function StationCard({ station, index, onClick, loading }: {
  station: EnergyStation;
  index: number;
  onClick: () => void;
  loading: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={loading}
      className="w-full rounded-xl glass-panel border border-neon-cyan/20 hover:border-neon-cyan/40 transition-all overflow-hidden p-3 text-left group disabled:opacity-50 disabled:cursor-not-allowed"
      style={{
        animation: `panel-anim-right 0.4s ease-out ${index * 0.08}s both`,
      }}
    >
      {/* 电站名称 + 状态标签 + 箭头 */}
      <div className="flex items-center justify-between gap-2 mb-2">
        <h5 className="text-sm font-medium text-slate-100 truncate flex-1 group-hover:text-neon-cyan transition-colors">
          {station.homeName || '未命名电站'}
        </h5>
        <span className={`rounded-full px-2 py-0.5 text-[10px] border font-mono ${getStatusStyle(station.powerStationStatus)}`}>
          {getStatusLabel(station.powerStationStatus)}
        </span>
        {loading ? (
          <RefreshCw size={14} className="text-neon-cyan animate-spin flex-shrink-0" />
        ) : (
          <ChevronRight size={14} className="text-slate-500 group-hover:text-neon-cyan transition-colors flex-shrink-0" />
        )}
      </div>

      {/* 数据指标网格 */}
      <div className="grid grid-cols-2 gap-2">
        {/* 发电功率 */}
        <div className="flex items-center gap-1.5">
          <Zap size={12} className="text-neon-cyan flex-shrink-0" />
          <div className="min-w-0">
            <p className="text-[10px] text-slate-500">发电功率</p>
            <p className="text-xs text-slate-200 font-mono truncate">
              {station.power || '—'}
            </p>
          </div>
        </div>
        {/* 今日发电量 */}
        <div className="flex items-center gap-1.5">
          <Sun size={12} className="text-amber-400 flex-shrink-0" />
          <div className="min-w-0">
            <p className="text-[10px] text-slate-500">今日发电量</p>
            <p className="text-xs text-slate-200 font-mono truncate">
              {station.todayElectricity || '—'}
            </p>
          </div>
        </div>
        {/* 电池容量 */}
        <div className="flex items-center gap-1.5">
          <Battery size={12} className="text-neon-purple flex-shrink-0" />
          <div className="min-w-0">
            <p className="text-[10px] text-slate-500">电池容量</p>
            <p className="text-xs text-slate-200 font-mono truncate">
              {station.batteryCapacity || '—'}
            </p>
          </div>
        </div>
        {/* 逆变器功率 */}
        <div className="flex items-center gap-1.5">
          <Cpu size={12} className="text-neon-cyan flex-shrink-0" />
          <div className="min-w-0">
            <p className="text-[10px] text-slate-500">逆变器功率</p>
            <p className="text-xs text-slate-200 font-mono truncate">
              {station.invPower || '—'}
            </p>
          </div>
        </div>
      </div>
    </button>
  );
}

/**
 * 储能电站列表页主组件。
 *
 * @param props.data SSE 返回的业务数据（含电站列表）
 */
export default function EnergyStationListPage({ data }: RoutePageProps) {
  /** 当前页码 */
  const [currentPage, setCurrentPage] = useState(1);
  /** 内联详情数据（点击电站卡片后加载） */
  const [inlineDetail, setInlineDetail] = useState<EnergyStationDetailData | null>(null);
  /** 详情加载状态 */
  const [loadingDetail, setLoadingDetail] = useState(false);
  /** 详情加载错误信息 */
  const [detailError, setDetailError] = useState<string | undefined>();

  /** 提取电站列表 */
  const stationList = useMemo(() => extractStationList(data), [data]);

  /** 总条数 */
  const total = useMemo(() => {
    const raw = data as EnergyStationListData;
    return raw.total ?? stationList.length;
  }, [data, stationList]);

  /** 总页数 */
  const totalPages = Math.max(1, Math.ceil(stationList.length / PAGE_SIZE));

  /** 当前页的电站列表 */
  const pagedList = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE;
    return stationList.slice(start, start + PAGE_SIZE);
  }, [stationList, currentPage]);

  /** 从数据中提取首个电站的 SOC（用于 3D 场景展示，无则用默认值） */
  const displaySoc = useMemo(() => {
    const first = stationList[0];
    if (first?.batterySoc != null) {
      const n = Number(first.batterySoc);
      if (Number.isFinite(n)) return n;
    }
    return 60;
  }, [stationList]);

  /** 从数据中提取首个电站的功率和发电量（用于 3D 场景展示） */
  const displayPower = stationList[0]?.power;
  const displayTodayElectricity = stationList[0]?.todayElectricity;

  /**
   * 点击电站卡片：调用 getEnergyStationDetail REST API 内联展示详情。
   * @param station 电站数据（含 homeId）
   */
  const handleClickStation = async (station: EnergyStation) => {
    if (loadingDetail) return;
    const homeId = station.homeId;
    if (homeId == null) return;
    setLoadingDetail(true);
    setDetailError(undefined);
    try {
      // 查询电站详情
      const res = await getEnergyStationDetail({ homeId: String(homeId) });
      if (res.success && res.data) {
        // 合并原始电站信息（确保 homeName 等字段存在）
        const detail: EnergyStationDetailData = {
          ...(res.data as EnergyStationDetailData),
          homeId: (res.data as EnergyStationDetailData).homeId ?? homeId,
          homeName: (res.data as EnergyStationDetailData).homeName ?? station.homeName,
        };
        setInlineDetail(detail);
      } else {
        setDetailError(res.message || '获取电站详情失败');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setDetailError(`获取电站详情失败：${msg}`);
    } finally {
      setLoadingDetail(false);
    }
  };

  // 内联展示电站详情时，直接渲染 EnergyStationDetailPage
  if (inlineDetail) {
    return (
      <div className="animate-fade-in space-y-3">
        <EnergyStationDetailPage data={inlineDetail} onBack={() => setInlineDetail(null)} />
      </div>
    );
  }

  // 空结果
  if (stationList.length === 0) {
    return (
      <div className="flex flex-col gap-3">
        {/* 3D 展示区（即使无数据也展示默认效果） */}
        <EnergyStation3D batterySoc={60} />

        <div className="flex flex-col items-center justify-center py-8 gap-2">
          <RefreshCw size={24} className="text-slate-600" />
          <p className="text-xs text-slate-500">暂无储能电站数据</p>
          <p className="text-[10px] text-slate-600">请先绑定储能设备</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3 animate-fade-in">
      {/* 顶部 3D 储能效果展示 */}
      <EnergyStation3D
        power={displayPower}
        batterySoc={displaySoc}
        todayElectricity={displayTodayElectricity}
      />

      {/* 详情加载错误提示 */}
      {detailError && (
        <div className="text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded px-2.5 py-1.5">
          {detailError}
        </div>
      )}

      {/* 结果计数 */}
      <div className="flex items-center justify-between px-1">
        <span className="text-[10px] text-slate-400 font-mono flex items-center gap-1">
          <Activity size={10} className="text-neon-cyan" />
          共 {total} 个电站
        </span>
        <span className="text-[10px] text-slate-600 font-mono">
          第 {currentPage}/{totalPages} 页
        </span>
      </div>

      {/* 电站卡片列表（点击可查看详情） */}
      <div className="space-y-2">
        {pagedList.map((station, i) => (
          <StationCard
            key={station.homeId ?? i}
            station={station}
            index={i}
            onClick={() => handleClickStation(station)}
            loading={loadingDetail}
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

      {/* 底部提示 */}
      <p className="text-xs text-slate-500 text-center">
        点击电站查看详情
      </p>
    </div>
  );
}
