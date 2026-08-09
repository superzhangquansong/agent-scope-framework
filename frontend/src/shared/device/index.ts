/**
 * @file 设备公共模块统一导出
 *
 * 汇总 device 子模块的对外 API：
 *  - constants: 设备属性 key 与属性值常量
 *  - utils: getSpkLabel / getStatusValue / rgbToHex 工具函数
 *  - useDeviceControl: 设备控制 Hook（乐观更新 + 延迟重新查询）
 *
 * 调用方应从本文件统一引入。
 */

export * from './constants';
export * from './utils';
export type { StatusItem } from './utils';
export { useDeviceControl } from './useDeviceControl';
export type { UseDeviceControlParams, UseDeviceControlResult, DeviceAttributes } from './useDeviceControl';
