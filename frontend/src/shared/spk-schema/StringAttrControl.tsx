/**
 * @file string 属性控件
 *
 * 渲染 string 类型属性（如设备名称、自定义参数）的文本输入控件。
 *
 * 设计要点：
 *  - 本地维护输入值，外部 value 变化时同步
 *  - 「设置」按钮触发 onControl 下发，避免每次按键都请求
 *  - 空值时禁用「设置」按钮
 *
 * 来源：从 DeviceStatusPage.tsx 中提取。
 */

import { useState, useEffect } from 'react';
import type { SpkAttribute } from './types';

/**
 * StringAttrControl 属性
 */
export interface StringAttrControlProps {
  /** 属性定义 */
  attr: SpkAttribute;
  /** 当前属性值 */
  value: string | undefined;
  /** 是否处于控制中（禁用输入框 + 按钮） */
  loading: boolean;
  /** 控制回调，参数为 (attrKey, attrValue) */
  onControl: (key: string, value: string) => void;
}

/**
 * string 属性文本输入控件。
 *
 * 内部 localVal 受控输入，外部 value 变化时通过 useEffect 同步。
 * 点击「设置」按钮触发 onControl 下发当前 localVal。
 *
 * @param props StringAttrControlProps
 */
export function StringAttrControl({ attr, value, loading, onControl }: StringAttrControlProps) {
  const [localVal, setLocalVal] = useState(value ?? '');
  useEffect(() => { setLocalVal(value ?? ''); }, [value]);
  return (
    <div>
      <div className="text-xs text-slate-400 mb-1">{attr.desc}</div>
      <div className="flex gap-1.5">
        <input
          type="text"
          value={localVal}
          disabled={loading}
          onChange={(e) => setLocalVal(e.target.value)}
          className="flex-1 px-2 py-1 text-xs glass-panel border border-white/10 rounded-md focus:outline-none focus:border-cyan-400/50 text-slate-200 disabled:opacity-50"
        />
        <button
          onClick={() => onControl(attr.key, localVal)}
          disabled={loading || !localVal}
          className="px-2 py-1 text-xs border border-cyan-400/40 rounded-md hover:bg-cyan-400/10 disabled:opacity-50 text-cyan-300"
        >
          设置
        </button>
      </div>
    </div>
  );
}
