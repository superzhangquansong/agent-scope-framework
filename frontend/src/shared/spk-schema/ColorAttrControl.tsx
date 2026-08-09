/**
 * @file color 属性控件
 *
 * 渲染 color 类型属性（如 rgb "r,g,b"、rgbw "r,g,b,w"、rgbcw "r,g,b,c,w"）的色盘控件。
 *
 * 设计要点：
 *  - 左侧显示属性描述 + 当前颜色色块预览
 *  - 右侧 ColorPicker 提供预设色块 + 自定义色盘
 *  - 选中颜色后通过 onColorPick 回调，由父组件按 format 重组多通道字符串后下发
 *
 * 来源：从 DeviceStatusPage.tsx 中提取。
 */

import { useMemo } from 'react';
import type { SpkAttribute } from './types';
import { ColorPicker } from './ColorPicker';

/**
 * ColorAttrControl 属性
 */
export interface ColorAttrControlProps {
  /** 属性定义（含 format 通道格式） */
  attr: SpkAttribute;
  /** 当前属性值（如 '255,128,0' / '255,128,0,80'） */
  value: string | undefined;
  /** 是否处于控制中（禁用色盘） */
  loading: boolean;
  /** 颜色选中回调，参数为 (attr, rgb) */
  onColorPick: (attr: SpkAttribute, rgb: [number, number, number]) => void;
}

/**
 * color 属性色盘控件。
 *
 * 解析当前 value 字符串（如 '255,128,0'）为 RGB 三元组，
 * 渲染色块预览 + ColorPicker 色盘，选中颜色后回调 onColorPick。
 *
 * value 为空或解析失败时使用白色作为预览默认值。
 *
 * @param props ColorAttrControlProps
 */
export function ColorAttrControl({ attr, value, loading, onColorPick }: ColorAttrControlProps) {
  const currentColor: [number, number, number] = useMemo(() => {
    if (!value) return [255, 255, 255];
    const parts = value.split(',').map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n));
    return parts.length >= 3 ? [parts[0], parts[1], parts[2]] : [255, 255, 255];
  }, [value]);

  return (
    <div>
      <div className="text-xs text-slate-400 mb-1.5 flex items-center gap-2">
        <span>{attr.desc}</span>
        <span
          className="inline-block w-4 h-4 rounded-full border border-white/30"
          style={{
            backgroundColor: `rgb(${currentColor.join(',')})`,
            boxShadow: `0 0 8px rgba(${currentColor.join(',')}, 0.6)`,
          }}
        />
      </div>
      <ColorPicker onPick={(rgb) => onColorPick(attr, rgb)} disabled={loading} />
    </div>
  );
}
