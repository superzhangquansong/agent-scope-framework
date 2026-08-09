/**
 * @file 颜色选择器组件
 *
 * 提供 HDL 设备颜色属性（rgb / rgbw / rgbcw）的颜色选择 UI：
 *  - 6 个预设色块（白光 / 暖光 / 红 / 绿 / 蓝 / 紫）
 *  - 1 个自定义颜色按钮（触发隐藏 input[type=color] 色盘）
 *
 * 同时导出 PRESET_COLORS 常量与 hexToRgb 工具函数，供其他模块复用。
 *
 * 来源：从 DeviceStatusPage.tsx / SceneCreateResultPage.tsx 中提取（两处实现完全一致）。
 */

import { useRef } from 'react';

/**
 * 颜色选择器预设色块。
 *
 * 数组顺序即为 UI 展示顺序，每项包含中文名称与 RGB 三元组。
 */
export const PRESET_COLORS: Array<{ name: string; rgb: [number, number, number] }> = [
  { name: '白光', rgb: [255, 255, 255] },
  { name: '暖光', rgb: [255, 200, 150] },
  { name: '红', rgb: [255, 80, 80] },
  { name: '绿', rgb: [80, 255, 120] },
  { name: '蓝', rgb: [80, 150, 255] },
  { name: '紫', rgb: [200, 100, 255] },
];

/**
 * 将 hex 颜色字符串（#rrggbb 或 rrggbb）转换为 [r,g,b] 数组。
 *
 * 非法格式返回 [0,0,0]，保证调用方始终拿到合法三元组。
 *
 * @param hex hex 颜色字符串（如 '#ff8800' 或 'ff8800'）
 * @returns [r, g, b] 三元组，范围 0-255
 */
export function hexToRgb(hex: string): [number, number, number] {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  if (!m) return [0, 0, 0];
  return [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)];
}

/**
 * ColorPicker 属性
 */
export interface ColorPickerProps {
  /** 颜色选中回调，参数为 [r, g, b] 三元组 */
  onPick: (rgb: [number, number, number]) => void;
  /** 是否禁用（控制中、刷新中等场景） */
  disabled?: boolean;
}

/**
 * HSV 色盘 + 预设色块选择组件。
 *
 * 渲染 6 个预设色块按钮 + 1 个圆锥渐变的"自定义颜色"按钮。
 * 点击预设色块直接触发 onPick(rgb)；
 * 点击自定义按钮触发隐藏的 input[type=color]，用户在系统色盘中选择后通过 hexToRgb 转换并回调 onPick。
 *
 * @param props ColorPickerProps
 */
export function ColorPicker({ onPick, disabled }: ColorPickerProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <div className="flex gap-1.5 items-center flex-wrap">
      {PRESET_COLORS.map(c => (
        <button
          key={c.name}
          onClick={() => onPick(c.rgb)}
          disabled={disabled}
          className="w-7 h-7 rounded-full border-2 border-white/30 shadow-sm hover:scale-110 transition-transform disabled:opacity-50"
          style={{ backgroundColor: `rgb(${c.rgb.join(',')})` }}
          title={c.name}
        />
      ))}
      {/* 自定义颜色按钮：触发隐藏的 input[type=color] */}
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={disabled}
        title="自定义颜色"
        className="w-7 h-7 rounded-full border-2 border-white/30 shadow-sm hover:scale-110 transition-transform disabled:opacity-50 relative overflow-hidden"
        style={{
          background: 'conic-gradient(from 0deg, #ff0000, #ffff00, #00ff00, #00ffff, #0000ff, #ff00ff, #ff0000)',
        }}
      >
        <input
          ref={inputRef}
          type="color"
          className="absolute inset-0 opacity-0 cursor-pointer"
          onChange={(e) => onPick(hexToRgb(e.target.value))}
          tabIndex={-1}
          aria-label="自定义颜色"
        />
      </button>
    </div>
  );
}
