/**
 * @file enum 属性控件
 *
 * 渲染 enum 类型属性（如 on_off 开/关、mode 模式、fan_speed 风速）的按钮组控件。
 *
 * 设计要点：
 *  - 主控开关（on_off + isMainSwitch）：渲染为大号 Toggle，霓虹渐变开关
 *  - on_off 非主控：渲染为隐藏的胶囊按钮组（与主控开关配合，避免重复展示）
 *  - 普通 enum：渲染为胶囊按钮组，选中态使用渐变背景
 *
 * 来源：从 DeviceStatusPage.tsx 中提取。
 */

import { motion } from 'framer-motion';
import type { SpkAttribute } from './types';

/** 开关属性值：开 */
const VALUE_ON = 'on';
/** 开关属性值：关 */
const VALUE_OFF = 'off';

/**
 * EnumAttrControl 属性
 */
export interface EnumAttrControlProps {
  /** 属性定义 */
  attr: SpkAttribute;
  /** 当前属性值 */
  value: string | undefined;
  /** 是否处于控制中（禁用按钮 + 动画反馈） */
  loading: boolean;
  /** 控制回调，参数为 (attrKey, attrValue) */
  onControl: (key: string, value: string) => void;
  /** 是否为 on_off 开关属性（决定胶囊按钮组的特殊样式） */
  isOnOff?: boolean;
  /** 是否为主控开关（决定渲染为大号 Toggle，仅 on_off 属性适用） */
  isMainSwitch?: boolean;
}

/**
 * enum 属性按钮组控件。
 *
 * 渲染策略：
 *  1. isOnOff && isMainSwitch：渲染大号 Toggle，控制电源开关
 *  2. isOnOff && !isMainSwitch：渲染隐藏块（避免与主控开关重复展示）
 *  3. 普通 enum：渲染胶囊按钮组，每个枚举值一个按钮，选中态高亮
 *
 * @param props EnumAttrControlProps
 */
export function EnumAttrControl({
  attr,
  value,
  loading,
  onControl,
  isOnOff,
  isMainSwitch,
}: EnumAttrControlProps) {
  // 主控开关：渲染为大号 Toggle
  if (isOnOff && isMainSwitch) {
    const isOn = value === VALUE_ON;
    return (
      <div className="flex items-center justify-between py-2">
        <div className="flex flex-col">
          <span className="text-sm font-medium text-slate-100">电源</span>
          <span className="text-[10px] text-slate-400">{isOn ? '已开启' : '已关闭'}</span>
        </div>
        <motion.button
          onClick={() => onControl(attr.key, isOn ? VALUE_OFF : VALUE_ON)}
          disabled={loading}
          className="relative w-16 h-9 rounded-full transition-all duration-300 disabled:opacity-50"
          style={{
            background: isOn
              ? 'linear-gradient(135deg, #00D4FF 0%, #7B2FBE 100%)'
              : 'rgba(58, 63, 78, 0.8)',
            boxShadow: isOn
              ? '0 0 20px rgba(0, 212, 255, 0.5), inset 0 0 10px rgba(255,255,255,0.2)'
              : 'inset 0 2px 6px rgba(0,0,0,0.4)',
          }}
          whileTap={{ scale: 0.95 }}
        >
          <motion.div
            className="absolute top-1 w-7 h-7 rounded-full bg-white shadow-lg"
            animate={{ x: isOn ? 28 : 4 }}
            transition={{ type: 'spring', stiffness: 500, damping: 30 }}
            style={{
              boxShadow: isOn
                ? '0 0 12px rgba(0, 212, 255, 0.6), 0 2px 4px rgba(0,0,0,0.3)'
                : '0 2px 4px rgba(0,0,0,0.3)',
            }}
          />
        </motion.button>
      </div>
    );
  }

  // 普通 enum：胶囊按钮组（模式选择器样式）
  return (
    <div className={isOnOff ? 'hidden' : ''}>
      <div className="text-xs text-slate-400 mb-1.5 flex items-center gap-1.5">
        {attr.desc}
      </div>
      <div className="flex gap-1.5 flex-wrap">
        {attr.enumerations!.map(opt => {
          const active = value === opt.value;
          return (
            <motion.button
              key={opt.value}
              onClick={() => onControl(attr.key, opt.value)}
              disabled={loading}
              whileTap={{ scale: 0.95 }}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-all disabled:opacity-50 ${
                active
                  ? 'text-white border-transparent'
                  : 'glass-panel text-slate-300 border-white/10 hover:border-cyan-400/40'
              }`}
              style={active ? {
                background: 'linear-gradient(135deg, #00D4FF 0%, #7B2FBE 100%)',
                boxShadow: '0 0 12px rgba(0, 212, 255, 0.4)',
              } : undefined}
            >
              {opt.desc}
            </motion.button>
          );
        })}
      </div>
    </div>
  );
}
