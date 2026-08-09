import { useState, useEffect, useCallback, useRef } from 'react';
import { RefreshCw, Power, Info, Activity, Zap } from 'lucide-react';
import DeviceImage from './DeviceImage';
import type { RoutePageProps } from './DynamicPage';
import { controlDevice, getDeviceDetail } from '../api/client';
import {
  useSpkSchema,
  AttributeControl,
  ReadOnlyAttrRow,
  extractDeviceAttrs,
  type SpkAttribute,
} from '../shared/spk-schema';
// 重新导出 extractDeviceAttrs，保持 DeviceListPage 现有 import 兼容
export { extractDeviceAttrs };
import { ATTR_ON_OFF, type DeviceAttributes } from '../shared/device';

// ===== 常量 =====

/** 乐观值保护窗口时长（毫秒）：HDL 硬件状态更新有延迟，控制后 5 秒内乐观值优先于真实值，避免 UI 闪烁 */
const OPTIMISTIC_TTL_MS = 5000;

/** 从 attributes 中按 key 取值，兼容数组 [{key,value}] 与对象 {key:value} 两种形态。
 *  兼容 HDL /device/info 返回的数组值格式：value 为数组时取第一个元素。 */
function getAttrValue(attributes: DeviceAttributes, key: string): string | undefined {
  if (!attributes) return undefined;
  if (Array.isArray(attributes)) {
    const found = attributes.find((a: { key: string; value: unknown }) => a && a.key === key);
    if (found?.value == null) return undefined;
    // HDL 可能返回 value 为数组（如 ["on"]），取第一个元素
    const v = Array.isArray(found.value) && found.value.length > 0 ? found.value[0] : found.value;
    return v != null ? String(v) : undefined;
  }
  return attributes[key] != null ? String(attributes[key]) : undefined;
}

// ===== 设备控制面板（schema 驱动） =====

/**
 * 设备控制面板（导出供 DeviceListPage 复用）
 *
 * 根据 spk-schemas.json 动态渲染所有可写属性的控制控件：
 *  - enum + WR：按钮组（如 on_off 开/关、mode 模式、fan_speed 风速）
 *  - number + WR：滑块 + 数字显示（如 brightness、set_temp、cct、position）
 *  - color + WR：色盘选择器（如 rgb、rgbw、rgbcw）
 *  - string + WR：文本输入框
 *  - access=R：只读展示（如 current_temp 当前温度）
 *
 * 所有控制操作直接调用 controlDevice API，控制成功后通过 onOptimisticUpdate 乐观更新 UI。
 */
export function DeviceControlPanel({
  deviceId, gatewayId, spk, deviceName, attributes, isOn, onOptimisticUpdate,
}: {
  deviceId: string;
  gatewayId: string;
  spk: string;
  deviceName: string;
  attributes: DeviceAttributes;
  isOn: boolean;
  onOptimisticUpdate?: (partial: Record<string, unknown>) => void;
}) {
  const schema = useSpkSchema(spk);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>();

  /** 调用 controlDevice API 控制单个属性 */
  const doControl = async (key: string, value: string) => {
    if (loading) return;
    setLoading(true);
    setError(undefined);
    try {
      const res = await controlDevice({
        deviceId,
        gatewayId,
        attributes: [{ key, value }],
        spk,
        deviceName,
      });
      if (res.success) {
        // 控制成功：乐观更新立即反映在 DeviceImage 上（如 RGB 开红色立即变红、关灯立即黑掉）
        onOptimisticUpdate?.({ [key]: value });
      } else {
        setError(res.message || '控制失败');
      }
    } catch (e) {
      setError('控制失败');
    } finally {
      setLoading(false);
    }
  };

  /** 颜色选择回调：根据 format 重组为 "r,g,b" / "r,g,b,w" / "r,g,b,c,w" 格式并下发 */
  const handleColorPick = (attr: SpkAttribute, rgb: [number, number, number]) => {
    const [r, g, b] = rgb;
    const format = attr.format || 'r,g,b';
    const channelCount = format.split(',').length;
    // 解析当前值，保留 w/c 通道
    const currentVal = getAttrValue(attributes, attr.key) || '';
    const parts = currentVal.split(',').map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n));
    const c = parts[3] ?? 0;
    const w2 = parts[4] ?? 0;
    const w = parts[3] ?? 0;
    if (channelCount >= 5) {
      doControl(attr.key, `${r},${g},${b},${c},${w2}`);
    } else if (channelCount === 4) {
      doControl(attr.key, `${r},${g},${b},${w}`);
    } else {
      doControl(attr.key, `${r},${g},${b}`);
    }
  };

  // schema 加载中
  if (!schema) {
    return (
      <div className="rounded-xl glass-panel border border-white/10 p-3">
        <div className="text-xs text-slate-400 flex items-center gap-1.5">
          <RefreshCw size={12} className="animate-spin text-cyan-400" />
          加载设备物模型...
        </div>
      </div>
    );
  }

  // 分离主控开关（on_off）、其他可写属性、只读属性
  const mainSwitchAttr = schema.attributes.find(a => a.key === ATTR_ON_OFF && (a.access === 'WR' || a.access === 'W'));
  const writableAttrs = schema.attributes.filter(a => a.key !== ATTR_ON_OFF && (a.access === 'WR' || a.access === 'W'));
  const readonlyAttrs = schema.attributes.filter(a => a.access === 'R');

  return (
    <div className="space-y-3">
      {error && (
        <div className="text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded-lg px-3 py-2">{error}</div>
      )}

      {/* 主控开关（on_off）— 大号 Toggle，关闭时所有子控件半透明禁用 */}
      {mainSwitchAttr && (
        <div className="rounded-xl glass-panel border border-white/10 p-3.5 glow-on-hover">
          <AttributeControl
            attr={mainSwitchAttr}
            value={getAttrValue(attributes, mainSwitchAttr.key)}
            loading={loading}
            onControl={doControl}
            onColorPick={handleColorPick}
            isMainSwitch
          />
        </div>
      )}

      {/* 子控件区域：主控开关关闭时半透明禁用 */}
      <div
        className="rounded-xl glass-panel border border-white/10 p-3.5 space-y-3.5 transition-all duration-300"
        style={{
          opacity: isOn ? 1 : 0.4,
          pointerEvents: isOn ? 'auto' : 'none',
        }}
      >
        <div className="flex items-center gap-1.5 text-xs font-medium text-slate-200">
          <Zap size={12} className="text-cyan-400" />
          设备控制
          {loading && <RefreshCw size={12} className="text-cyan-400 animate-spin ml-1" />}
        </div>

        {/* 可写属性控制面板 */}
        {writableAttrs.map(attr => (
          <div key={attr.key} className="glow-on-hover">
            <AttributeControl
              attr={attr}
              value={getAttrValue(attributes, attr.key)}
              loading={loading}
              onControl={doControl}
              onColorPick={handleColorPick}
            />
          </div>
        ))}

        {writableAttrs.length === 0 && (
          <div className="text-xs text-slate-500 text-center py-2">无可配置属性</div>
        )}
      </div>

      {/* 状态卡片：只读属性展示 */}
      {readonlyAttrs.length > 0 && (
        <div className="rounded-xl glass-panel border border-white/10 p-3.5 space-y-2">
          <div className="flex items-center gap-1.5 text-xs font-medium text-slate-200 mb-1">
            <Activity size={12} className="text-cyan-400" />
            状态信息
          </div>
          {readonlyAttrs.map(attr => (
            <ReadOnlyAttrRow key={attr.key} attr={attr} value={getAttrValue(attributes, attr.key)} />
          ))}
        </div>
      )}
    </div>
  );
}

// ===== 单个设备详情卡片（核心） =====

/**
 * 单个设备的详情卡片
 *
 * 全新设计：毛玻璃 + 深色渐变 + 大号设备可视化 + 主控开关 + 数值滑块组 + 模式选择器 + 状态卡片
 *
 * 布局：
 *  - 顶部：设备头部（图标 + 名称 + 在线状态呼吸灯 + 刷新按钮）
 *  - 中部：大号设备可视化（280x280，毛玻璃卡片包裹）
 *  - 底部：设备控制面板（主控开关 + 子控件 + 状态卡片）
 *
 * 挂载时调用 getDeviceDetail 获取实时属性，控制成功后乐观更新立即反映在 DeviceImage 上。
 */
function DeviceStatusCard({ data }: { data: Record<string, unknown> }) {
  const { deviceId, gatewayId, online, spk } = data as {
    deviceId: string; gatewayId: string; online: boolean; spk: string;
  };
  // HDL 设备详情/列表接口返回 name，设备控制接口返回 deviceName，兼容两者
  const deviceName = (data.name ?? data.deviceName) as string | undefined;
  // 初始化属性：优先取 status（当前状态值），回退 attributes（属性定义）
  // HDL 报文：status=[{key,value}] 的 value 是标量（如 "99"）；attributes=[{key,value}] 的 value 是枚举数组
  const [liveAttributes, setLiveAttributes] = useState<DeviceAttributes>(
    (data.status ?? data.attributes) as DeviceAttributes
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>();
  // 刷新请求的取消控制器（卸载或重新刷新时取消上一个请求）
  const abortRef = useRef<AbortController | null>(null);

  // 乐观值保护窗口：记录最近控制的属性 key -> { value, expireAt }
  // HDL 设备硬件状态更新有延迟（约1-3秒），控制后立即刷新 getDeviceDetail 会返回旧值，
  // 用乐观值覆盖真实值，并在保护窗口内（5秒）不被刷新拉取的旧值覆盖，避免 UI 闪烁回旧状态。
  const optimisticRef = useRef<Map<string, { value: unknown; expireAt: number }>>(new Map());

  /**
   * 刷新设备属性（拉取真实状态）
   *
   * 刷新策略：以真实属性为基础，但未过期的乐观值优先（不被覆盖）。
   * 这样控制成功后立即刷新也不会把 UI 闪回旧状态。
   *
   * 注意：HDL /device/info REST API 返回 data 是数组 [{deviceId, name, status, ...}]，
   * 需先提取第一个设备对象，再读取其 status 字段。
   */
  const refresh = useCallback(async () => {
    if (!deviceId || !gatewayId) return;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setError(undefined);
    try {
      const res = await getDeviceDetail({ deviceId, gatewayId }, controller.signal);
      if (controller.signal.aborted) return; // 已被取消，静默返回
      if (res.success && res.data) {
        let d: Record<string, unknown> = res.data as unknown as Record<string, unknown>;
        // HDL /device/info 返回 data 可能是数组 [{deviceId, name, status, ...}]，
        // 也可能是包装对象 {list: [...]}，需先提取单个设备对象
        if (Array.isArray(d)) {
          d = d[0] as Record<string, unknown>;
        } else if (d && Array.isArray(d.list)) {
          d = (d.list as Array<Record<string, unknown>>)[0];
        }
        // 优先取 status（当前状态值，value 是标量如 "99"），回退 attributes（属性定义）
        const realAttrs = (d.status ?? d.attributes ?? d) as DeviceAttributes;

        // 清理已过期的乐观值
        const now = Date.now();
        for (const [k, entry] of optimisticRef.current) {
          if (now > entry.expireAt) {
            optimisticRef.current.delete(k);
          }
        }

        // 合并：以真实属性为基础，未过期的乐观值覆盖真实值
        setLiveAttributes(() => {
          if (Array.isArray(realAttrs)) {
            const merged = realAttrs.map((a: { key: string; value: unknown }) => {
              if (a && a.key != null) {
                const opt = optimisticRef.current.get(a.key);
                if (opt && now <= opt.expireAt) {
                  return { ...a, value: opt.value };
                }
              }
              return a;
            });
            // 补充真实属性中不存在但乐观值有的 key
            for (const [k, entry] of optimisticRef.current) {
              if (now <= entry.expireAt && !merged.some((a: { key: string; value: unknown }) => a && a.key === k)) {
                merged.push({ key: k, value: entry.value });
              }
            }
            return merged;
          } else {
            // 对象形态：合并乐观值
            const merged: Record<string, unknown> = { ...realAttrs as Record<string, unknown> };
            for (const [k, entry] of optimisticRef.current) {
              if (now <= entry.expireAt) {
                merged[k] = entry.value;
              }
            }
            return merged;
          }
        });
      } else if (!res.success) {
        setError(res.message || '获取设备属性失败');
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return; // 静默取消
      setError('获取设备属性失败');
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, [deviceId, gatewayId]);

  useEffect(() => {
    refresh();
    return () => abortRef.current?.abort();
  }, [refresh]);

  /**
   * 乐观更新属性（控制成功后立即反映在 UI 上，不等 getDeviceDetail 返回）
   *
   * 控制流程：用户操作控制面板 → controlDevice API 成功 → 乐观更新 liveAttributes
   * → DeviceImage 立即变色/变暗/开关 → 保护窗口内刷新拉取的旧值不会覆盖乐观值。
   */
  const updateAttributes = useCallback((partial: Record<string, unknown>) => {
    const now = Date.now();
    // 记录乐观更新的 key 和过期时间，刷新时保护窗口内不被真实值覆盖
    for (const [k, v] of Object.entries(partial)) {
      optimisticRef.current.set(k, { value: v, expireAt: now + OPTIMISTIC_TTL_MS });
    }
    setLiveAttributes((prev: DeviceAttributes) => {
      // 兼容数组 [{key, value}] 和对象 {key: value} 两种形态
      if (Array.isArray(prev)) {
        const updated = prev.map(a => {
          if (a && a.key != null && partial[a.key] != null) {
            return { ...a, value: partial[a.key] };
          }
          return a;
        });
        // 补充数组中不存在的 key
        for (const [k, v] of Object.entries(partial)) {
          if (!updated.some(a => a && a.key === k)) {
            updated.push({ key: k, value: v });
          }
        }
        return updated;
      }
      return { ...(prev as Record<string, unknown>), ...partial };
    });
  }, []);

  // 提取 DeviceImage 可视化所需属性（开关、亮度、温度、颜色、风速、色温等）
  // 每次 liveAttributes 变化都会重新提取，确保控制后 DeviceImage 立即联动变化
  const attrProps = extractDeviceAttrs(liveAttributes);

  return (
    <div
      className="rounded-2xl overflow-hidden relative glass-premium card-in"
    >
      {/* 径向光晕背景（根据设备状态动态变化） */}
      <div
        className="absolute inset-0 pointer-events-none transition-opacity duration-500"
        style={{
          background: attrProps.isOn
            ? `radial-gradient(ellipse at 50% 30%, ${attrProps.color ? `rgba(${attrProps.color.join(',')}, 0.15)` : 'rgba(0, 212, 255, 0.12)'} 0%, transparent 60%)`
            : 'transparent',
          opacity: attrProps.isOn ? 1 : 0,
        }}
      />

      {/* 头部：图标 + 设备名 + spk + 在线状态呼吸灯 + 刷新按钮 */}
      <div className="relative flex items-center gap-2.5 px-4 py-3 border-b border-white/5">
        <div
          className="rounded-lg p-2 flex-shrink-0"
          style={{
            background: 'linear-gradient(135deg, rgba(0, 212, 255, 0.15) 0%, rgba(123, 47, 190, 0.15) 100%)',
            border: '1px solid rgba(0, 212, 255, 0.2)',
          }}
        >
          <Info size={16} className="text-cyan-300" />
        </div>
        <h4 className="font-semibold text-slate-100 flex-1 truncate text-sm">{deviceName}</h4>
        {spk && (
          <span
            className="text-[10px] font-mono px-1.5 py-0.5 rounded flex-shrink-0"
            style={{
              background: 'rgba(123, 47, 190, 0.15)',
              border: '1px solid rgba(123, 47, 190, 0.3)',
              color: '#c084fc',
            }}
          >
            {spk}
          </span>
        )}
        {/* 在线状态呼吸灯 */}
        <span className="flex items-center gap-1.5 flex-shrink-0">
          <span className="relative flex">
            <span
              className={`w-2 h-2 rounded-full ${online ? 'bg-green-400' : 'bg-slate-500'}`}
              style={online ? { boxShadow: '0 0 8px rgba(74, 222, 128, 0.8)' } : undefined}
            />
            {online && (
              <span
                className="absolute inset-0 rounded-full bg-green-400 animate-ping"
                style={{ animationDuration: '2s' }}
              />
            )}
          </span>
          <span className={`text-[10px] font-mono ${online ? 'text-green-400' : 'text-slate-500'}`}>
            {online ? '在线' : '离线'}
          </span>
        </span>
        <button
          onClick={() => refresh()}
          disabled={loading}
          title="刷新"
          className="flex-shrink-0 w-7 h-7 flex items-center justify-center rounded-md glass-panel text-slate-400 hover:text-cyan-300 hover:border-cyan-400/40 transition-all disabled:opacity-50 border border-white/10 touch-feedback"
        >
          <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
        </button>
      </div>

      {error && (
        <div className="mx-4 mt-3 text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded-lg px-3 py-2">{error}</div>
      )}

      {/* 设备可视化：大号 DeviceImage（280x280），毛玻璃卡片包裹
          控制成功后通过乐观更新立即反映变化，关灯立即变黑，开红色立即变红 */}
      {spk && (
        <div className="relative px-4 py-4 flex items-center justify-center">
          <div
            className="rounded-xl overflow-hidden"
            style={{
              width: 280,
              height: 280,
              background: 'rgba(255, 255, 255, 0.03)',
              backdropFilter: 'blur(20px)',
              border: '1px solid rgba(255, 255, 255, 0.08)',
              boxShadow: 'inset 0 0 20px rgba(0, 0, 0, 0.3)',
            }}
          >
            <div style={{ width: '100%', height: '100%' }} className="flex items-center justify-center">
              <DeviceImage spk={spk} {...attrProps} />
            </div>
          </div>
        </div>
      )}

      {/* 控制面板（schema 驱动，直接调用 controlDevice API，不走对话流程）
          控制成功后：1) 乐观更新立即反映在 DeviceImage  2) 用户可手动点刷新拉取真实属性 */}
      {spk && deviceId && gatewayId && (
        <div className="px-4 pb-4">
          <DeviceControlPanel
            deviceId={deviceId}
            gatewayId={gatewayId}
            spk={spk}
            deviceName={deviceName ?? ''}
            attributes={liveAttributes}
            isOn={attrProps.isOn}
            onOptimisticUpdate={updateAttributes}
          />
        </div>
      )}
    </div>
  );
}

// ===== 默认导出：设备状态页（支持单设备/多设备） =====

/**
 * 设备状态/详情页面
 *
 * 支持两种数据来源：
 *  1. 对话流程返回的单设备详情：data 中有 deviceId/deviceName/spk/online/attributes 等
 *  2. 多设备控制返回的结果：data.multiDevice=true，data.devices 为设备数组
 */
export default function DeviceStatusPage({ data }: RoutePageProps) {
  const payload = data as Record<string, unknown>;

  // 多设备控制场景：渲染顶部提示 + 每个设备的详情卡片
  if (payload.multiDevice && Array.isArray(payload.devices)) {
    return (
      <div className="animate-fade-in space-y-3">
        <div className="flex items-center gap-2 text-sm">
          <Power size={16} className="neon-text-purple" />
          <span className="gradient-text font-semibold">多设备控制成功</span>
          <span className="text-xs text-slate-400 font-mono">{(payload.devices as unknown[]).length} 台设备已控制</span>
        </div>
        <div className="space-y-3">
          {(payload.devices as Array<Record<string, unknown>>).map((d, i) => (
            <DeviceStatusCard key={(d.deviceId as string) ?? i} data={d} />
          ))}
        </div>
      </div>
    );
  }

  // 单设备控制后端统一返回 devices 数组（与多设备路径一致）
  // 单设备场景从 devices[0] 取设备数据，兼容旧版直接返回设备对象的格式
  let singleDeviceData: Record<string, unknown> = payload;
  if (Array.isArray(payload.devices) && payload.devices.length > 0) {
    singleDeviceData = (payload.devices as Array<Record<string, unknown>>)[0];
  }

  // 单设备场景
  return (
    <div className="animate-fade-in space-y-3">
      <DeviceStatusCard data={singleDeviceData} />
    </div>
  );
}
