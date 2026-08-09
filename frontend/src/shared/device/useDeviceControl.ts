/**
 * @file 设备控制 Hook
 *
 * 封装 HDL 设备控制的「乐观更新 + 延迟重新查询」逻辑，
 * 消除 DeviceListPage 的 InlineControlWrapper 与 DeviceStatusPage 的 DeviceStatusCard 中重复的状态管理代码。
 *
 * 核心机制：
 *  1. 乐观更新：控制成功后立即更新本地 liveAttributes，UI 即时反映（如关灯立即变黑）
 *  2. TTL 保护窗口：记录乐观值的过期时间（默认 5 秒），保护窗口内刷新拉取的旧值不会覆盖乐观值，
 *     避免 HDL 硬件状态更新延迟（约 1-3 秒）导致的 UI 闪烁回旧状态
 *  3. 延迟重新查询：控制后延迟若干毫秒重新调用 getDeviceDetail 拉取真实状态，
 *     超过保护窗口后的刷新才会用真实值替换乐观值
 *
 * 兼容两种属性形态：
 *  - 数组 [{key, value}]：HDL /device/info 的 status/attributes 字段
 *  - 对象 {key: value}：前端约定的简化形态
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { getDeviceDetail } from '../../api/client';

/** 乐观值保护窗口时长（毫秒）：HDL 硬件状态更新有延迟，控制后 5 秒内乐观值优先于真实值，避免 UI 闪烁 */
const OPTIMISTIC_TTL_MS = 5000;

/** 控制后延迟重新查询的时长（毫秒）：给硬件状态更新留出时间，避免立即查询仍拿到旧值 */
const REFRESH_DELAY_MS = 800;

/**
 * 设备属性集合类型。
 *
 * 兼容 HDL 接口返回的两种形态：
 *  - 数组 [{ key, value }]：status / attributes 字段
 *  - 对象 { key: value }：前端约定的简化形态
 */
export type DeviceAttributes = Array<{ key: string; value: unknown }> | Record<string, unknown>;

/**
 * 乐观值缓存条目
 */
interface OptimisticEntry {
  /** 乐观值 */
  value: unknown;
  /** 过期时间戳（毫秒） */
  expireAt: number;
}

/**
 * useDeviceControl 参数
 */
export interface UseDeviceControlParams {
  /** 设备 ID */
  deviceId: string;
  /** 网关 ID */
  gatewayId: string;
  /** 初始属性（status 数组或 attributes 数组，亦可为对象形态） */
  initialAttributes: DeviceAttributes;
  /**
   * 是否启用控制后延迟重新查询（默认 true）。
   * - InlineControlWrapper 场景：true，控制后 800ms 自动刷新拉取真实状态
   * - DeviceStatusCard 场景：可设为 false，由用户手动点击刷新按钮
   */
  enableDelayedRefresh?: boolean;
  /**
   * 乐观值保护窗口时长（毫秒，默认 5000）。
   * 保护窗口内的刷新请求不会用真实值覆盖乐观值，避免 UI 闪烁。
   */
  optimisticTtlMs?: number;
}

/**
 * useDeviceControl 返回值
 */
export interface UseDeviceControlResult {
  /** 当前实时属性（已合并未过期乐观值） */
  liveAttributes: DeviceAttributes;
  /** 是否正在重新查询 */
  refreshing: boolean;
  /** 错误信息 */
  error: string | undefined;
  /** 手动刷新设备属性（拉取真实状态，未过期乐观值仍优先） */
  refresh: () => Promise<void>;
  /**
   * 乐观更新属性（控制成功后调用）。
   * 1. 记录乐观值与过期时间到 optimisticRef
   * 2. 立即更新 liveAttributes（UI 即时反映）
   * 3. 若 enableDelayedRefresh=true，延迟 REFRESH_DELAY_MS 后自动 refresh()
   *
   * @param partial 要更新的属性键值对（如 { on_off: 'on' }）
   */
  optimisticUpdate: (partial: Record<string, unknown>) => void;
}

/**
 * 设备控制 Hook：封装乐观更新 + 延迟重新查询逻辑。
 *
 * 使用示例：
 * ```ts
 * const { liveAttributes, refreshing, error, refresh, optimisticUpdate } = useDeviceControl({
 *   deviceId: device.deviceId,
 *   gatewayId: device.gatewayId,
 *   initialAttributes: device.status ?? device.attributes,
 * });
 * // 控制成功后调用 optimisticUpdate({ on_off: 'on' })
 * ```
 *
 * @param params UseDeviceControlParams
 * @returns UseDeviceControlResult
 */
export function useDeviceControl({
  deviceId,
  gatewayId,
  initialAttributes,
  enableDelayedRefresh = true,
  optimisticTtlMs = OPTIMISTIC_TTL_MS,
}: UseDeviceControlParams): UseDeviceControlResult {
  const [liveAttributes, setLiveAttributes] = useState<DeviceAttributes>(initialAttributes);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | undefined>();

  // 乐观值保护窗口：记录最近控制的属性 key -> { value, expireAt }
  // HDL 设备硬件状态更新有延迟（约1-3秒），控制后立即刷新 getDeviceDetail 会返回旧值，
  // 用乐观值覆盖真实值，并在保护窗口内（默认5秒）不被刷新拉取的旧值覆盖，避免 UI 闪烁回旧状态。
  const optimisticRef = useRef<Map<string, OptimisticEntry>>(new Map());
  // 刷新请求的取消控制器（卸载或重新刷新时取消上一个请求）
  const abortRef = useRef<AbortController | null>(null);

  /**
   * 刷新设备属性（拉取真实状态）。
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
    setRefreshing(true);
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
      if (!controller.signal.aborted) setRefreshing(false);
    }
  }, [deviceId, gatewayId]);

  /**
   * 乐观更新属性（控制成功后立即反映在 UI 上，不等 getDeviceDetail 返回）。
   *
   * 控制流程：用户操作控制面板 → controlDevice API 成功 → 调用本函数乐观更新 liveAttributes
   * → DeviceImage 立即变色/变暗/开关 → 保护窗口内刷新拉取的旧值不会覆盖乐观值。
   *
   * 若 enableDelayedRefresh=true，更新后会延迟 REFRESH_DELAY_MS 自动调用 refresh 拉取真实状态。
   *
   * @param partial 要更新的属性键值对
   */
  const optimisticUpdate = useCallback((partial: Record<string, unknown>) => {
    const now = Date.now();
    // 记录乐观更新的 key 和过期时间，刷新时保护窗口内不被真实值覆盖
    for (const [k, v] of Object.entries(partial)) {
      optimisticRef.current.set(k, { value: v, expireAt: now + optimisticTtlMs });
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

    // 控制后延迟重新查询真实状态（可选）
    if (enableDelayedRefresh) {
      setTimeout(() => {
        refresh();
      }, REFRESH_DELAY_MS);
    }
  }, [enableDelayedRefresh, optimisticTtlMs, refresh]);

  // 卸载时取消进行中的刷新请求
  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  return {
    liveAttributes,
    refreshing,
    error,
    refresh,
    optimisticUpdate,
  };
}
