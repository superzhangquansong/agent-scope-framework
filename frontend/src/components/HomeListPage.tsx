import { useState, useEffect } from 'react';
import { Home, Cpu, Wifi, ShieldCheck, RefreshCw } from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import { getHomeList } from '../api/client';

/** 房屋信息数据结构 */
interface HomeItem {
  /** HDL 原始字段名 homeId */
  homeId?: string;
  /** HDL 原始字段名 homeName */
  homeName?: string;
  /** HDL 原始字段名 homeType（BUSPRO/KNX/ZIGBEE） */
  homeType?: string;
  /** HDL 原始字段名 deviceCount */
  deviceCount?: number;
  /** HDL 原始字段名 isRemoteControl */
  isRemoteControl?: boolean;
  [k: string]: unknown;
}

/** 房屋列表数据结构 */
interface HomeListData {
  /** 房屋总数 */
  total?: number;
  /** SSE 链路外层字段名 homes（behaviors.yml 映射） */
  homes?: HomeItem[];
}

/** 房屋类型中文标签映射 */
function getHomeTypeLabel(homeType?: string): string {
  if (!homeType) return '未知';
  const upper = homeType.toUpperCase();
  switch (upper) {
    case 'BUSPRO':
      return 'Buspro';
    case 'KNX':
      return 'KNX';
    case 'ZIGBEE':
      return 'ZigBee';
    default:
      return homeType;
  }
}

/**
 * 房屋列表页
 *
 * - 接收 data={homes, total}，渲染房屋卡片列表
 * - 当 SSE 报文未携带 homes 时，通过 /api/home/list REST 接口自行拉取
 * - 每个卡片：房屋名称 + 类型标签 + 设备数量 + 远程控制标识
 */
export default function HomeListPage({ data }: RoutePageProps) {
  const payload = data as unknown as HomeListData;
  const initialHomes = payload.homes ?? [];
  const initialTotal = payload.total ?? initialHomes.length;

  // 当 SSE 报文未携带 homes 时，通过 REST 接口自行拉取
  const [homes, setHomes] = useState<HomeItem[]>(initialHomes);
  const [total, setTotal] = useState<number>(initialTotal);
  const [loadingList, setLoadingList] = useState<boolean>(initialHomes.length === 0);

  /**
   * 当 SSE 报文未携带 homes 时，通过 /api/home/list REST 接口拉取房屋列表。
   * 精简报文模式：后端仅返回 routePath=/home/list，由前端自行查询房屋数据。
   */
  useEffect(() => {
    if (initialHomes.length > 0) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await getHomeList();
        if (cancelled) return;
        if (res.success && res.data) {
          setHomes((res.data.homes ?? []) as HomeItem[]);
          setTotal(res.data.total ?? 0);
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

  return (
    <div className="animate-fade-in space-y-4">
      {/* 标题栏 */}
      <div className="flex items-center gap-2 text-sm">
        <Home size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">房屋列表</span>
        <span className="text-xs text-slate-400 font-mono">共 {total} 个</span>
      </div>

      {loadingList ? (
        <div className="glass rounded-xl px-4 py-8 text-center text-sm text-cyan-300 flex items-center justify-center gap-2">
          <RefreshCw size={14} className="animate-spin" />
          正在查询房屋列表...
        </div>
      ) : homes.length === 0 ? (
        <div className="glass rounded-xl px-4 py-8 text-center text-sm text-slate-400">
          暂无房屋数据
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-2.5">
          {homes.map((h, i) => (
            <div
              key={h.homeId ?? i}
              className="group glass rounded-xl p-3 hover-neon card-shadow flex items-center gap-3 animate-card-in border border-neon-purple/15 transition-all"
              style={{ animationDelay: `${i * 60}ms` }}
            >
              {/* 房屋图标 */}
              <div className="w-12 h-12 rounded-lg bg-neon-purple/10 border border-neon-purple/20 flex items-center justify-center flex-shrink-0">
                <Home size={20} className="text-neon-purple" />
              </div>

              {/* 房屋信息 */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <h5 className="text-sm font-medium text-slate-100 truncate group-hover:text-neon-cyan transition-colors">
                    {h.homeName ?? '未命名房屋'}
                  </h5>
                  {h.homeType && (
                    <span className="rounded bg-neon-cyan/10 px-1.5 py-0.5 text-[10px] text-neon-cyan border border-neon-cyan/20 font-mono">
                      {getHomeTypeLabel(h.homeType)}
                    </span>
                  )}
                </div>
                <div className="mt-1.5 flex items-center gap-3 text-[11px] text-slate-400">
                  {h.deviceCount != null && (
                    <span className="flex items-center gap-1">
                      <Cpu size={11} />
                      {h.deviceCount} 台设备
                    </span>
                  )}
                  {h.isRemoteControl && (
                    <span className="flex items-center gap-1 text-neon-green">
                      <Wifi size={11} />
                      远程控制
                    </span>
                  )}
                </div>
              </div>

              {/* 远程控制图标 */}
              {h.isRemoteControl && (
                <div className="flex-shrink-0 text-neon-green/60">
                  <ShieldCheck size={16} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
