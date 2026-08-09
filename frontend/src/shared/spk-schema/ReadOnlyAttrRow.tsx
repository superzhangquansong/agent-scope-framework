/**
 * @file 只读属性行组件
 *
 * 渲染 access=R 只读属性（如 current_temp 当前温度、battery_percentage 电池电量）的展示行。
 *
 * 设计要点：
 *  - 左侧属性描述 + Activity 图标
 *  - 右侧属性值（enum 类型会查找对应 desc，未命中回退原始 value）
 *  - 数值带霓虹光晕文字阴影
 *
 * 来源：从 DeviceStatusPage.tsx 中提取。
 */

import { Activity } from 'lucide-react';
import type { SpkAttribute } from './types';

/**
 * ReadOnlyAttrRow 属性
 */
export interface ReadOnlyAttrRowProps {
  /** 属性定义 */
  attr: SpkAttribute;
  /** 当前属性值 */
  value: string | undefined;
}

/**
 * 只读属性展示行。
 *
 * 渲染策略：
 *  - enum 类型且 value 命中 enumerations：展示对应 desc
 *  - 其他情况：展示原始 value，空值展示 '--'
 *  - 数值后追加单位（unit 字段）
 *
 * @param props ReadOnlyAttrRowProps
 */
export function ReadOnlyAttrRow({ attr, value }: ReadOnlyAttrRowProps) {
  let displayValue = value ?? '--';
  if (attr.type === 'enum' && attr.enumerations && value) {
    const found = attr.enumerations.find(e => e.value === value);
    displayValue = found ? found.desc : value;
  }
  return (
    <div className="flex items-center justify-between rounded-lg glass-panel px-3 py-2 border border-white/10">
      <span className="text-xs text-slate-400 flex items-center gap-1.5">
        <Activity size={11} className="text-cyan-400/60" />
        {attr.desc}
      </span>
      <span className="text-xs text-cyan-300 font-mono font-medium" style={{ textShadow: '0 0 6px rgba(0, 212, 255, 0.3)' }}>
        {displayValue}{attr.unit && value ? attr.unit : ''}
      </span>
    </div>
  );
}
