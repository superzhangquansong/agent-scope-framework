import DeviceStatusPage from './DeviceStatusPage';
import type { RoutePageProps } from './DynamicPage';

/**
 * 设备详情查询结果页
 *
 * 该页面用于展示通过对话 SSE 流程查询到的设备详情：
 *  - 后端 result 事件携带 routePath='device-detail' + data=[{deviceId, name, spk, online, attributes, ...}]
 *  - HDL 设备详情接口返回 data 是数组，取第一个元素
 *  - 数据结构与 DeviceStatusPage 的单设备场景完全一致
 *
 * 因此本组件简化为直接复用 DeviceStatusPage 的逻辑：
 *  - 头部：设备名称 + SPK + 在线状态 + 刷新按钮
 *  - 中间：DeviceImage 大型可视化（200x200）
 *  - 底部：SPK Schema 驱动的属性控制面板（可写属性下发 controlDevice）
 *
 * 说明：
 *  - 若 data 中携带 gatewayId，则可调用 getDeviceDetail 刷新 + controlDevice 控制
 *  - 若 data 中缺少 gatewayId（仅展示场景），DeviceStatusPage 会自动仅展示属性，不渲染控制面板
 *  - 乐观更新与保护窗口逻辑均在 DeviceStatusPage 中实现，本组件无需重复
 */
export default function DeviceDetailPage({ data }: RoutePageProps) {
  // 兼容多种 data 格式：
  // 1. 直接数组（HDL 原始）：[{deviceId, name, ...}]
  // 2. 包装对象：{list: [{deviceId, name, ...}]}
  // 3. 单个对象：{deviceId, name, ...}
  let payload: Record<string, unknown>;
  if (Array.isArray(data)) {
    payload = data[0] as Record<string, unknown>;
  } else if (data && Array.isArray((data as Record<string, unknown>).list)) {
    payload = ((data as Record<string, unknown>).list as unknown[])[0] as Record<string, unknown>;
  } else {
    payload = data as Record<string, unknown>;
  }
  return <DeviceStatusPage data={payload} />;
}
