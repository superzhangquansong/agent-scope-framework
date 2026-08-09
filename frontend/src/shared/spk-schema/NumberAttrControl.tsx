/**
 * @file number 属性控件
 *
 * 渲染 number 类型属性（如 brightness 0-100%、set_temp 16-32℃、cct 0-65535K）的滑块控件。
 *
 * 设计要点：
 *  - 渐变进度条 + 原生 range input（透明背景叠加在进度条之上）
 *  - 数值标签使用弹簧动画（每次拖动触发 scale 1.15 → 1 弹回）
 *  - onMouseUp / onTouchEnd 时触发 onControl 下发控制指令（避免拖动中频繁请求）
 *
 * 来源：从 DeviceStatusPage.tsx 中提取。
 */

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import type { SpkAttribute } from './types';

/**
 * NumberAttrControl 属性
 */
export interface NumberAttrControlProps {
  /** 属性定义（含 min/max/step/unit 等元信息） */
  attr: SpkAttribute;
  /** 当前属性值（字符串形式，如 '99'） */
  value: string | undefined;
  /** 是否处于控制中（禁用滑块） */
  loading: boolean;
  /** 控制回调，参数为 (attrKey, attrValue) */
  onControl: (key: string, value: string) => void;
}

/**
 * number 属性滑块控件。
 *
 * 内部状态：
 *  - localVal：本地拖动中的数值（实时更新，仅拖动结束才下发）
 *  - bounce：数值标签弹动动画 key（每次拖动 +1 触发 framer-motion 重渲染）
 *
 * 外部 value 变化时（如刷新拉取到真实状态）会同步到 localVal。
 *
 * @param props NumberAttrControlProps
 */
export function NumberAttrControl({ attr, value, loading, onControl }: NumberAttrControlProps) {
  const min = attr.min ?? 0;
  const max = attr.max ?? 100;
  const step = attr.step ?? 1;
  const numValue = value != null ? Number(value) : min;
  const [localVal, setLocalVal] = useState(isNaN(numValue) ? min : numValue);
  // 数值标签弹动效果触发器
  const [bounce, setBounce] = useState(0);

  useEffect(() => { setLocalVal(isNaN(numValue) ? min : numValue); }, [numValue, min]);

  // 滑块进度百分比（用于渐变进度条）
  const progressPercent = ((localVal - min) / (max - min)) * 100;

  return (
    <div>
      <div className="flex justify-between text-xs mb-1.5 items-center">
        <span className="text-slate-300">{attr.desc}</span>
        <motion.span
          key={bounce}
          initial={{ scale: 1.15 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 600, damping: 15 }}
          className="font-mono text-cyan-300 font-semibold"
          style={{ textShadow: '0 0 8px rgba(0, 212, 255, 0.5)' }}
        >
          {localVal}{attr.unit ?? ''}
        </motion.span>
      </div>
      <div className="relative">
        {/* 滑块轨道背景 */}
        <div className="absolute inset-0 h-1.5 top-1/2 -translate-y-1/2 rounded-full bg-slate-700/60" />
        {/* 滑块进度渐变 */}
        <div
          className="absolute h-1.5 top-1/2 -translate-y-1/2 rounded-full transition-all duration-200"
          style={{
            width: `${progressPercent}%`,
            background: 'linear-gradient(90deg, #00D4FF 0%, #7B2FBE 100%)',
            boxShadow: '0 0 8px rgba(0, 212, 255, 0.4)',
          }}
        />
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={localVal}
          disabled={loading}
          onChange={(e) => {
            setLocalVal(Number(e.target.value));
            setBounce(b => b + 1);
          }}
          onMouseUp={() => onControl(attr.key, String(localVal))}
          onTouchEnd={() => onControl(attr.key, String(localVal))}
          className="relative w-full h-1.5 appearance-none cursor-pointer bg-transparent slider-neon disabled:opacity-50"
          style={{ background: 'transparent' }}
        />
      </div>
    </div>
  );
}
