/**
 * @file SPK Schema 公共模块统一导出
 *
 * 汇总 spk-schema 子模块的对外 API：
 *  - types: SPK 物模型类型定义
 *  - useSpkSchema: Schema 加载 Hook
 *  - ColorPicker: 颜色选择器组件 + PRESET_COLORS + hexToRgb
 *  - EnumAttrControl / NumberAttrControl / ColorAttrControl / StringAttrControl: 属性子控件
 *  - ReadOnlyAttrRow: 只读属性行
 *  - AttributeControl: 属性控件分发器
 *  - extractDeviceAttrs: 设备属性提取工具
 *
 * 调用方应从本文件统一引入，避免直接引用子文件造成循环依赖。
 */

export * from './types';
export { useSpkSchema, loadSpkSchemas } from './useSpkSchema';
export { ColorPicker, PRESET_COLORS, hexToRgb } from './ColorPicker';
export type { ColorPickerProps } from './ColorPicker';
export { EnumAttrControl } from './EnumAttrControl';
export type { EnumAttrControlProps } from './EnumAttrControl';
export { NumberAttrControl } from './NumberAttrControl';
export type { NumberAttrControlProps } from './NumberAttrControl';
export { ColorAttrControl } from './ColorAttrControl';
export type { ColorAttrControlProps } from './ColorAttrControl';
export { StringAttrControl } from './StringAttrControl';
export type { StringAttrControlProps } from './StringAttrControl';
export { ReadOnlyAttrRow } from './ReadOnlyAttrRow';
export type { ReadOnlyAttrRowProps } from './ReadOnlyAttrRow';
export { AttributeControl, extractDeviceAttrs } from './AttributeControl';
export type { AttributeControlProps, DeviceAttrs } from './AttributeControl';
