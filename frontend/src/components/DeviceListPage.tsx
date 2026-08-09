import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Cpu, Wifi, WifiOff, ChevronLeft, ChevronRight, Info, RefreshCw,
  SlidersHorizontal, ChevronDown, ChevronUp,
} from 'lucide-react';
import DeviceImage from './DeviceImage';
import { getDeviceDetail, getDeviceList } from '../api/client';
import { extractDeviceAttrs, DeviceControlPanel } from './DeviceStatusPage';
import {
  ATTR_ON_OFF,
  VALUE_ON,
  getSpkLabel,
  getStatusValue,
  rgbToHex,
} from '../shared/device';

/** 设备列表每页条数（前端客户端分页） */
const PAGE_SIZE = 5;

/** 设备列表中的设备项数据结构 */
interface DeviceItem {
  deviceId?: string;
  name?: string;
  deviceName?: string;
  online?: boolean;
  spk?: string;
  gatewayId?: string;
  /** 当前状态值列表（value 为标量，如 "99"、"on"） */
  status?: Array<{ key: string; value: string }>;
  /** 属性定义列表（value 为枚举数组，如 ["on","off"]） */
  attributes?: Array<{ key: string; data_type: string; value: unknown[]; min?: number; max?: number; sort?: number }>;
  productName?: string;
  roomInfos?: Array<{ roomName: string }>;
  [k: string]: unknown;
}

/** 设备列表数据结构 */
interface DeviceListData {
  total?: number;
  devices?: DeviceItem[];
  list?: DeviceItem[];
}

/** 点击设备详情回调 */
export type DeviceDetailAction = (device: {
  deviceId: string;
  gatewayId: string;
  deviceName: string;
  spk?: string;
  online?: boolean;
}) => void;

interface DeviceListPageProps {
  data: Record<string, unknown>;
  onDeviceDetail?: DeviceDetailAction;
}

/**
 * 设备列表页
 *
 * 每个卡片：
 *  - 左侧 DeviceImage（根据 status 实时渲染开关/亮度/颜色）
 *  - 右侧设备名 + 类型标签 + 在线状态 + 当前属性状态徽章
 *  - 「刷新」按钮：重新查询设备最新状态
 *  - 「控制」按钮：展开 DeviceControlPanel（与设备详情页相同的 schema 驱动控制面板）
 *  - 「详情」按钮：跳转 DeviceStatusPage 完整详情
 *
 * 挂载时自动重新查询所有设备的最新状态，确保展示的是真实状态而非旧缓存。
 */
export default function DeviceListPage({ data, onDeviceDetail }: DeviceListPageProps) {
  const payload = data as unknown as DeviceListData;
  const initialDevices = payload.devices ?? payload.list ?? [];
  const initialTotal = payload.total ?? initialDevices.length;

  // 当 SSE 报文未携带 devices 时，通过 REST 接口自行拉取
  const [originalDevices, setOriginalDevices] = useState<DeviceItem[]>(initialDevices);
  const [total, setTotal] = useState<number>(initialTotal);
  // 标记是否正在通过 REST 拉取设备列表
  const [loadingList, setLoadingList] = useState<boolean>(initialDevices.length === 0);

  // 前端客户端分页
  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(originalDevices.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const start = (currentPage - 1) * PAGE_SIZE;
  const pageItems = originalDevices.slice(start, start + PAGE_SIZE);

  // 重新查询后的设备数据（含最新 status）
  const [refreshedDevices, setRefreshedDevices] = useState<Record<string, DeviceItem>>({});
  // 展开的设备 ID 集合
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  // 全局刷新中
  const [globalRefreshing, setGlobalRefreshing] = useState(false);
  // 标记是否已经执行过首次查询（避免重复查询）
  const initialLoadedRef = useRef(false);
  // 保存 originalDevices 的稳定引用（避免 props 引用变化导致重复查询）
  const originalDevicesRef = useRef<DeviceItem[]>(originalDevices);
  // 首次接收到非空设备列表时同步到 ref
  if (!initialLoadedRef.current && originalDevices.length > 0) {
    originalDevicesRef.current = originalDevices;
  }

  /**
   * 当 SSE 报文未携带 devices 时，通过 /api/device/list REST 接口拉取设备列表。
   * 精简报文模式：后端仅返回 routePath=/device/list，由前端自行查询设备数据。
   */
  useEffect(() => {
    if (initialDevices.length > 0) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await getDeviceList();
        if (cancelled) return;
        if (res.success && res.data) {
          const devices = (res.data.devices ?? []) as DeviceItem[];
          setOriginalDevices(devices);
          setTotal(res.data.total ?? devices.length);
          originalDevicesRef.current = devices;
        }
      } catch {
        // 静默失败，保留空列表
      } finally {
        if (!cancelled) setLoadingList(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * 重新查询单个设备的最新状态。
   *
   * 调用 getDeviceDetail REST API 获取设备详情，
   * 返回的 status 字段是设备当前真实状态值。
   */
  const refreshDevice = useCallback(async (device: DeviceItem): Promise<DeviceItem | null> => {
    const deviceId = String(device.deviceId ?? '');
    const gatewayId = String(device.gatewayId ?? '');
    if (!deviceId || !gatewayId || deviceId === '-') return null;
    try {
      const res = await getDeviceDetail({ deviceId, gatewayId });
      if (res.success && res.data) {
        const d = res.data as unknown as Record<string, unknown>;
        // HDL /device/info 返回 data 是数组，取第一个
        let dev: Record<string, unknown> | undefined;
        if (Array.isArray(d)) {
          dev = d[0] as Record<string, unknown>;
        } else if (d && Array.isArray(d.list)) {
          dev = (d.list as Array<Record<string, unknown>>)[0];
        } else {
          dev = d;
        }
        if (dev) {
          return {
            ...device,
            ...dev,
            name: (dev.name as string) ?? device.name ?? (dev.deviceName as string),
            gatewayId: (dev.gatewayId as string) ?? gatewayId,
            spk: (dev.spk as string) ?? device.spk,
            online: (dev.online as boolean) ?? device.online,
            status: (dev.status as DeviceItem['status']) ?? device.status,
            attributes: (dev.attributes as DeviceItem['attributes']) ?? device.attributes,
          };
        }
      }
    } catch {
      // 静默失败
    }
    return null;
  }, []);

  /**
   * 批量重新查询所有设备的最新状态。
   *
   * 并行调用 getDeviceDetail，将结果存入 refreshedDevices。
   * 使用 originalDevicesRef 避免 props 引用变化导致重复触发。
   * 通过 skipLoaded 标记控制是否跳过已加载的状态（首次加载必查，手动刷新必查）。
   */
  const refreshAllDevices = useCallback(async (skipLoaded: boolean = true) => {
    // 若已加载且非手动触发，则跳过（避免重复查询）
    if (skipLoaded && initialLoadedRef.current) return;
    if (globalRefreshing) return;
    const devices = originalDevicesRef.current;
    if (devices.length === 0) return;
    setGlobalRefreshing(true);
    try {
      const results = await Promise.all(
        devices.map(d => refreshDevice(d).then(r => [String(d.deviceId ?? ''), r] as const))
      );
      const map: Record<string, DeviceItem> = {};
      for (const [id, dev] of results) {
        if (dev) map[id] = dev;
      }
      setRefreshedDevices(map);
      // 标记已加载，后续不再重复查询
      initialLoadedRef.current = true;
    } finally {
      setGlobalRefreshing(false);
    }
  }, [refreshDevice, globalRefreshing]);

  // 挂载时自动重新查询所有设备状态（只查一次）
  // 依赖空数组确保只在组件挂载时执行一次
  useEffect(() => {
    refreshAllDevices(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** 获取设备当前数据（优先 refreshedDevices，回退原始数据） */
  const getDevice = useCallback((d: DeviceItem): DeviceItem => {
    const id = String(d.deviceId ?? '');
    return refreshedDevices[id] ?? d;
  }, [refreshedDevices]);

  /** 切换展开/收起控制面板 */
  const toggleExpand = useCallback((deviceId: string) => {
    setExpandedIds(prev => {
      const next = new Set(prev);
      if (next.has(deviceId)) next.delete(deviceId);
      else next.add(deviceId);
      return next;
    });
  }, []);

  /** 重新查询单个设备（卡片刷新按钮） */
  const handleRefreshOne = useCallback(async (device: DeviceItem) => {
    const dev = await refreshDevice(device);
    if (dev) {
      setRefreshedDevices(prev => ({ ...prev, [String(device.deviceId ?? '')]: dev }));
    }
  }, [refreshDevice]);

  return (
    <div className="animate-fade-in space-y-3">
      {/* 标题栏 + 全局刷新按钮 */}
      <div className="flex items-center gap-2 text-sm">
        <Cpu size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">设备列表</span>
        <span className="text-xs text-slate-400 font-mono">共 {total} 台</span>
        <button
          onClick={() => refreshAllDevices(false)}
          disabled={globalRefreshing}
          title="重新查询所有设备状态"
          className="ml-auto flex items-center gap-1 px-2 py-0.5 rounded-lg text-[10px] glass-panel text-cyan-300 border border-cyan-400/30 hover:border-cyan-400/60 transition-all disabled:opacity-50"
        >
          <RefreshCw size={11} className={globalRefreshing ? 'animate-spin' : ''} />
          {globalRefreshing ? '查询中...' : '刷新全部'}
        </button>
      </div>

      {loadingList ? (
        <div className="glass rounded-xl px-4 py-6 text-center text-sm text-cyan-300 flex items-center justify-center gap-2">
          <RefreshCw size={14} className="animate-spin" />
          正在查询设备列表...
        </div>
      ) : originalDevices.length === 0 ? (
        <div className="glass rounded-xl px-4 py-6 text-center text-sm text-slate-400">
          暂无设备
        </div>
      ) : (
        <div className="space-y-2.5">
          {pageItems.map((d, i) => {
            const deviceId = String(d.deviceId ?? '');
            // 合并重新查询后的数据
            const merged = getDevice(d);
            const online = merged.online ?? false;
            const name = merged.name ?? merged.deviceName ?? '未命名设备';
            const spkLabel = getSpkLabel(merged.spk);
            // 用合并后的 status 提取属性
            const attrProps = extractDeviceAttrs(merged);
            const liveStatus = merged.status ?? [];
            const isExpanded = expandedIds.has(deviceId);

            // 当前状态徽章数据
            const currentOnOff = getStatusValue(liveStatus, ATTR_ON_OFF);
            const currentBrightness = getStatusValue(liveStatus, 'brightness');
            const currentRgb = getStatusValue(liveStatus, 'rgb') ?? getStatusValue(liveStatus, 'rgbw') ?? getStatusValue(liveStatus, 'rgbcw');
            const currentCct = getStatusValue(liveStatus, 'cct');
            const rgbHex = currentRgb ? rgbToHex(currentRgb) : undefined;

            return (
              <div
                key={deviceId || i}
                className={`glass rounded-xl overflow-hidden border transition-all animate-card-in ${
                  online ? 'border-neon-green/20' : 'border-slate-600/20'
                }`}
                style={{ animationDelay: `${i * 60}ms` }}
              >
                {/* 设备卡片主体 */}
                <div className="flex items-center gap-3 p-3">
                  {/* DeviceImage 可视化（根据真实 status 渲染） */}
                  <div
                    className="flex-shrink-0 rounded-xl p-1 overflow-hidden"
                    style={{
                      background: 'rgba(15, 23, 42, 0.5)',
                      border: '1px solid rgba(0, 212, 255, 0.15)',
                    }}
                  >
                    {merged.spk ? (
                      <div style={{ width: 90, height: 90 }} className="flex items-center justify-center">
                        <DeviceImage spk={merged.spk} {...attrProps} />
                      </div>
                    ) : (
                      <div className="flex items-center justify-center text-slate-500" style={{ width: 90, height: 90 }}>
                        <Cpu size={28} />
                      </div>
                    )}
                  </div>

                  {/* 设备信息 */}
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium text-slate-100 truncate">{name}</div>
                    <div className="text-[10px] text-slate-500 font-mono truncate mt-0.5">
                      ID: {deviceId}
                    </div>
                    <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                      {merged.spk && (
                        <span className="text-[10px] text-neon-purple font-mono px-1.5 py-0.5 rounded bg-neon-purple/10 border border-neon-purple/20">
                          {spkLabel}
                        </span>
                      )}
                      <span className={`flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[10px] font-mono border ${
                        online
                          ? 'text-neon-green border-neon-green/30 bg-neon-green/5'
                          : 'text-slate-400 border-slate-500/30 bg-slate-500/5'
                      }`}>
                        {online ? <Wifi size={9} /> : <WifiOff size={9} />}
                        {online ? '在线' : '离线'}
                      </span>
                      {/* 当前状态徽章（基于真实 status） */}
                      {currentOnOff && (
                        <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded border ${
                          currentOnOff === VALUE_ON
                            ? 'text-neon-green border-neon-green/30 bg-neon-green/5'
                            : 'text-slate-400 border-slate-500/30 bg-slate-500/5'
                        }`}>
                          {currentOnOff === VALUE_ON ? '● 开' : '○ 关'}
                        </span>
                      )}
                      {currentBrightness != null && currentOnOff === VALUE_ON && (
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded text-cyan-300 border border-cyan-400/30 bg-cyan-400/5">
                          亮度 {currentBrightness}%
                        </span>
                      )}
                      {rgbHex && currentOnOff === VALUE_ON && (
                        <span className="flex items-center gap-1 text-[10px] font-mono px-1.5 py-0.5 rounded text-slate-300 border border-white/10">
                          <span
                            className="w-2.5 h-2.5 rounded-full inline-block"
                            style={{ backgroundColor: rgbHex, boxShadow: `0 0 4px ${rgbHex}` }}
                          />
                          {currentRgb}
                        </span>
                      )}
                      {currentCct != null && currentOnOff === VALUE_ON && (
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded text-amber-300 border border-amber-400/30 bg-amber-400/5">
                          CCT {currentCct}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* 右侧按钮组 */}
                  <div className="flex-shrink-0 flex flex-col gap-1.5">
                    {/* 刷新按钮 */}
                    <button
                      onClick={() => handleRefreshOne(d)}
                      title="重新查询此设备状态"
                      className="flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] glass-panel text-slate-300 border-white/10 hover:border-cyan-400/40 transition-all"
                    >
                      <RefreshCw size={11} />
                    </button>
                    {/* 控制按钮 */}
                    <button
                      onClick={() => toggleExpand(deviceId)}
                      disabled={!deviceId || !merged.gatewayId || !merged.spk}
                      title="属性控制"
                      className={`flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] font-medium border transition-all disabled:opacity-30 disabled:cursor-not-allowed ${
                        isExpanded
                          ? 'text-white border-transparent'
                          : 'glass-panel text-cyan-300 border-cyan-400/30 hover:border-cyan-400/60'
                      }`}
                      style={isExpanded ? {
                        background: 'linear-gradient(135deg, #00D4FF 0%, #7B2FBE 100%)',
                      } : undefined}
                    >
                      <SlidersHorizontal size={11} />
                      {isExpanded ? '收起' : '控制'}
                      {isExpanded ? <ChevronUp size={10} /> : <ChevronDown size={10} />}
                    </button>
                    {/* 详情按钮 */}
                    {onDeviceDetail && deviceId && merged.gatewayId && deviceId !== '-' && (
                      <button
                        onClick={() => onDeviceDetail({
                          deviceId,
                          gatewayId: String(merged.gatewayId),
                          deviceName: name,
                          spk: merged.spk,
                          online,
                        })}
                        className="flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] glass-panel text-slate-300 border-white/10 hover:border-cyan-400/40 transition-all"
                      >
                        <Info size={11} />
                        详情
                      </button>
                    )}
                  </div>
                </div>

                {/* 内联控制面板（展开时显示，复用 DeviceStatusPage 的 DeviceControlPanel） */}
                {isExpanded && deviceId && merged.gatewayId && merged.spk && (
                  <div className="border-t border-white/5 px-3 py-3 bg-black/20">
                    <InlineControlWrapper
                      device={merged}
                      liveStatus={liveStatus}
                    />
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 分页控件 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 pt-1">
          <button
            onClick={() => setPage(p => Math.max(1, p - 1))}
            disabled={currentPage <= 1}
            className="glass-panel rounded-lg p-1.5 text-neon-cyan hover-neon disabled:opacity-40 disabled:cursor-not-allowed transition-all"
          >
            <ChevronLeft size={14} />
          </button>
          <span className="text-xs text-slate-300 font-mono">
            {currentPage} / {totalPages}
          </span>
          <button
            onClick={() => setPage(p => Math.min(totalPages, p + 1))}
            disabled={currentPage >= totalPages}
            className="glass-panel rounded-lg p-1.5 text-neon-cyan hover-neon disabled:opacity-40 disabled:cursor-not-allowed transition-all"
          >
            <ChevronRight size={14} />
          </button>
        </div>
      )}

      <p className="text-xs text-slate-500 text-center">
        点击「控制」展开属性面板，或点击「详情」查看完整信息
      </p>
    </div>
  );
}

/**
 * 内联控制面板包装器。
 *
 * 包装 DeviceControlPanel（从 DeviceStatusPage 导出），提供：
 *  - 本地状态管理（liveStatus + 乐观更新）
 *  - 控制后自动重新查询设备最新状态
 */
function InlineControlWrapper({
  device, liveStatus,
}: {
  device: DeviceItem;
  liveStatus: Array<{ key: string; value: string }>;
}) {
  const deviceId = String(device.deviceId ?? '');
  const gatewayId = String(device.gatewayId ?? '');
  const spk = device.spk ?? '';
  const deviceName = device.name ?? device.deviceName ?? '';

  // 本地维护 liveAttributes（控制后乐观更新）
  const [localAttrs, setLocalAttrs] = useState<Array<{ key: string; value: string }>>(liveStatus);
  const [refreshing, setRefreshing] = useState(false);
  // 父组件传入的 status 变化时同步
  useEffect(() => {
    setLocalAttrs(liveStatus);
  }, [liveStatus]);

  // 乐观更新
  const handleOptimisticUpdate = useCallback((partial: Record<string, unknown>) => {
    setLocalAttrs((prev) => {
      if (Array.isArray(prev)) {
        const updated = prev.map(a => {
          if (a && a.key != null && partial[a.key] != null) {
            return { ...a, value: partial[a.key] as string };
          }
          return a;
        });
        for (const [k, v] of Object.entries(partial)) {
          if (!updated.some(a => a && a.key === k)) {
            updated.push({ key: k, value: v as string });
          }
        }
        return updated;
      }
      return prev;
    });
  }, []);

  // 控制后自动重新查询设备最新状态
  // 注意：DeviceControlPanel 内部会调用 controlDevice API 并通过 onOptimisticUpdate 回调更新状态
  // 此处不再重复调用 controlDevice，仅通过 onOptimisticUpdate 接收乐观更新
  // 控制后延迟重新查询真实状态（可选，乐观更新已足够）

  const handleOptimisticUpdateWithRefresh = useCallback((partial: Record<string, unknown>) => {
    handleOptimisticUpdate(partial);
    // 延迟重新查询真实状态
    setRefreshing(true);
    setTimeout(async () => {
      try {
        const detail = await getDeviceDetail({ deviceId, gatewayId });
        if (detail.success && detail.data) {
          const d = detail.data as unknown as Record<string, unknown>;
          let dev: Record<string, unknown> | undefined;
          if (Array.isArray(d)) {
            dev = d[0] as Record<string, unknown>;
          } else if (d && Array.isArray(d.list)) {
            dev = (d.list as Array<Record<string, unknown>>)[0];
          } else {
            dev = d;
          }
          if (dev?.status) {
            setLocalAttrs(dev.status as Array<{ key: string; value: string }>);
          }
        }
      } catch {
        // 静默失败
      } finally {
        setRefreshing(false);
      }
    }, 800);
  }, [deviceId, gatewayId, handleOptimisticUpdate]);

  const attrProps = extractDeviceAttrs(localAttrs);
  const isOn = attrProps.isOn;

  return (
    <div className="space-y-2">
      {refreshing && (
        <div className="text-[10px] text-cyan-300 flex items-center gap-1">
          <RefreshCw size={10} className="animate-spin" />
          正在重新查询设备状态...
        </div>
      )}
      <DeviceControlPanel
        deviceId={deviceId}
        gatewayId={gatewayId}
        spk={spk}
        deviceName={deviceName}
        attributes={localAttrs}
        isOn={isOn}
        onOptimisticUpdate={handleOptimisticUpdateWithRefresh}
      />
    </div>
  );
}
