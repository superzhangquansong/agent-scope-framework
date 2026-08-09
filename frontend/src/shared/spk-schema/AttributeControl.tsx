/**
 * @file 属性控件分发器 + 设备属性提取工具
 *
 * 导出两部分：
 *  1. AttributeControl 组件：根据 SpkAttribute.type + access 分发到具体子控件
 *     （EnumAttrControl / NumberAttrControl / ColorAttrControl / StringAttrControl / ReadOnlyAttrRow）
 *  2. extractDeviceAttrs 工具函数：从设备对象或属性数组中提取 DeviceImage 所需属性
 *
 * 来源：从 DeviceStatusPage.tsx 中提取。
 */

import type { SpkAttribute } from './types';
import { EnumAttrControl } from './EnumAttrControl';
import { NumberAttrControl } from './NumberAttrControl';
import { ColorAttrControl } from './ColorAttrControl';
import { StringAttrControl } from './StringAttrControl';
import { ATTR_ON_OFF, VALUE_ON, ATTR_RGB } from '../device/constants';
import type { DeviceAttributes } from '../device/useDeviceControl';

/** RGBW 颜色属性 key（light.rgbw） */
const ATTR_RGBW = 'rgbw';
/** RGBCW 颜色属性 key（light.rgbcw） */
const ATTR_RGBCW = 'rgbcw';
/** 色温属性 key */
const ATTR_CCT = 'cct';

/** RGBWAF 6 通道（light.rgbwaf 用独立通道控制） */
const RGBWAF_CHANNELS = ['R', 'G', 'B', 'W', 'A', 'F'] as const;

/**
 * AttributeControl 属性
 */
export interface AttributeControlProps {
  /** 属性定义 */
  attr: SpkAttribute;
  /** 当前属性值 */
  value: string | undefined;
  /** 是否处于控制中（禁用子控件） */
  loading: boolean;
  /** 控制回调，参数为 (attrKey, attrValue) */
  onControl: (key: string, value: string) => void;
  /** 颜色选中回调，参数为 (attr, rgb) */
  onColorPick: (attr: SpkAttribute, rgb: [number, number, number]) => void;
  /** 是否为主控开关（仅 on_off 属性生效，渲染为大号 Toggle） */
  isMainSwitch?: boolean;
}

/**
 * 单个属性控件：根据 type + access 渲染对应 UI。
 *
 * 分发策略：
 *  - enum + WR/W：按钮组（on_off 主控开关渲染为大号 Toggle）
 *  - number + WR：滑块（access=W 只写按键跳过，无持续值不展示）
 *  - color + WR：色盘
 *  - string + WR：文本输入框
 *  - bool：简化为开/关按钮组
 *  - 其他（无匹配类型）：返回 null
 *
 * @param props AttributeControlProps
 */
export function AttributeControl({
  attr, value, loading, onControl, onColorPick, isMainSwitch,
}: AttributeControlProps) {
  // enum 类型：按钮组
  if (attr.type === 'enum' && attr.enumerations) {
    return <EnumAttrControl attr={attr} value={value} loading={loading} onControl={onControl} isOnOff={attr.key === ATTR_ON_OFF} isMainSwitch={isMainSwitch} />;
  }
  // number 类型：滑块（access=W 只写按键跳过，无持续值）
  if (attr.type === 'number') {
    if (attr.access === 'W') return null;
    return <NumberAttrControl attr={attr} value={value} loading={loading} onControl={onControl} />;
  }
  // color 类型：色盘
  if (attr.type === 'color') {
    return <ColorAttrControl attr={attr} value={value} loading={loading} onColorPick={onColorPick} />;
  }
  // string 类型：文本输入
  if (attr.type === 'string') {
    return <StringAttrControl attr={attr} value={value} loading={loading} onControl={onControl} />;
  }
  // bool 类型：简化为开/关按钮组
  if (attr.type === 'bool') {
    return (
      <EnumAttrControl
        attr={{ ...attr, type: 'enum', enumerations: [{ value: 'true', desc: '开' }, { value: 'false', desc: '关' }] }}
        value={value}
        loading={loading}
        onControl={onControl}
      />
    );
  }
  return null;
}

/**
 * extractDeviceAttrs 返回值结构
 *
 * 提取后的属性集合，供 DeviceImage 可视化组件渲染设备状态。
 */
export interface DeviceAttrs {
  /** 是否开启 */
  isOn: boolean;
  /** 亮度（0-100） */
  brightness?: number;
  /** 当前温度（字符串形式） */
  temperature?: string;
  /** 百分比位置（窗帘等设备） */
  percent?: number;
  /** 模式（如 'cool' / 'heat'） */
  mode?: string;
  /** 风速 */
  fanSpeed?: string;
  /** RGB 颜色三元组 */
  color?: [number, number, number];
  /** 多通道（RGBWAF）数值 */
  channels?: Record<string, number>;
  /** 色温（CCT） */
  cct?: number;
}

/**
 * 从设备对象或属性数组中提取 DeviceImage 所需属性。
 *
 * HDL 设备报文包含两个关键字段：
 *  - attributes: 属性定义列表，value 是枚举值数组（如 ["on","off"]）或空数组，表示"可选值"而非"当前值"
 *  - status: 当前状态值列表，value 是标量（如 "99"、"on"），表示设备当前实际状态
 *
 * 本函数优先读取 status（当前状态），回退到 attributes（属性定义）：
 *  1. 传入设备对象（含 status/attributes 字段）：优先取 status，回退 attributes
 *  2. 传入数组（status 或 attributes 数组）：直接解析
 *  3. 传入对象（{key: value}）：直接使用
 *
 * 兼容 HDL /device/info 返回的数组值格式：value 为数组时取第一个元素。
 *
 * 解析开关、亮度、温度、颜色、色温、通道等，并做色温→RGB 的兜底转换。
 *
 * @param source 设备对象、属性数组或属性对象
 * @returns 提取后的属性集合
 */
export function extractDeviceAttrs(source: DeviceAttributes | undefined): DeviceAttrs {
  if (!source) return { isOn: false };

  // 如果传入的是设备对象（含 status 或 attributes 字段），优先取 status（当前状态值）
  // status 的 value 是标量（如 "99"），attributes 的 value 是枚举数组（如 [] 或 ["on","off"]）
  let attributes: DeviceAttributes | Record<string, unknown> = source;
  if (typeof source === 'object' && !Array.isArray(source)) {
    const src = source as Record<string, unknown>;
    // 设备对象：优先 status，回退 attributes
    if (src.status && Array.isArray(src.status) && src.status.length > 0) {
      attributes = src.status as DeviceAttributes;
    } else if (src.attributes && Array.isArray(src.attributes)) {
      attributes = src.attributes as DeviceAttributes;
    }
  }

  const obj: Record<string, unknown> = Array.isArray(attributes)
    ? attributes.reduce((acc: Record<string, unknown>, a: { key: string; value: unknown }) => {
        if (a && a.key != null) {
          // HDL /device/info 的 attributes 字段 value 可能是数组（如 ["on","off"]），取第一个元素
          // HDL /device/info 的 status 字段 value 是标量（如 "99"），直接使用
          let v = a.value;
          if (Array.isArray(v) && v.length > 0) {
            v = v[0];
          }
          acc[String(a.key)] = v;
        }
        return acc;
      }, {})
    : attributes as Record<string, unknown>;

  // 开关：HDL 用 on_off='on'/'off'，兼容旧 power/switch/on/Power 等
  const rawOn = obj.on_off ?? obj.power ?? obj.switch ?? obj.on ?? obj.isOn ?? obj.Power;
  const isOn = rawOn === true || rawOn === 1 || rawOn === '1' ||
    (typeof rawOn === 'string' && rawOn.toLowerCase() === VALUE_ON) || rawOn === 'true';

  const brightnessRaw = obj.brightness;
  const brightness = brightnessRaw != null ? Number(brightnessRaw) : undefined;

  // 空调当前温度优先取 current_temp，回退 temperature
  const temperatureRaw = obj.current_temp ?? obj.temperature;
  const temperature = temperatureRaw != null ? String(temperatureRaw) : undefined;

  const percentRaw = obj.percent ?? obj.position;
  const percent = percentRaw != null ? Number(percentRaw) : undefined;

  const mode = obj.mode != null ? String(obj.mode) : undefined;
  const fanSpeed = obj.fan_speed != null ? String(obj.fan_speed) : undefined;

  const color = Array.isArray(obj.color) ? (obj.color as [number, number, number]) : undefined;

  // 色温 cct
  const cctRaw = obj[ATTR_CCT];
  const cct = cctRaw != null ? Number(cctRaw) : undefined;

  // RGB/RGBW/RGBCW 字符串解析（"r,g,b" / "r,g,b,w" / "r,g,b,c,w"）
  let parsedColor: [number, number, number] | undefined = color;
  const rgbRawStr = obj[ATTR_RGB] ?? obj[ATTR_RGBW] ?? obj[ATTR_RGBCW];
  if (typeof rgbRawStr === 'string' && rgbRawStr.trim()) {
    const parts = rgbRawStr.split(',').map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n));
    if (parts.length >= 3) {
      parsedColor = [parts[0], parts[1], parts[2]];
    }
  }

  // 通道：兼容 obj.channels 对象，以及大写独立通道 R/G/B/W/A/F（light.rgbwaf）
  let channels = obj.channels && typeof obj.channels === 'object' && !Array.isArray(obj.channels)
    ? obj.channels as Record<string, number>
    : undefined;

  const hasUpperChannels = RGBWAF_CHANNELS.some(k => obj[k] != null);
  if (hasUpperChannels) {
    const merged: Record<string, number> = { ...(channels || {}) };
    for (const k of RGBWAF_CHANNELS) {
      if (obj[k] != null) {
        const n = Number(obj[k]);
        if (!isNaN(n)) merged[k] = n;
      }
    }
    channels = merged;
  }

  // 若尚未得到 color，但有 R/G/B 通道，则派生 color 用于可视化回显
  if (!parsedColor && channels) {
    const r = channels.R;
    const g = channels.G;
    const b = channels.B;
    if (r != null && g != null && b != null) {
      parsedColor = [r, g, b];
    }
  }

  // 色温 → 颜色转换（light.cct 设备：低色温暖黄，高色温冷蓝）
  if (!parsedColor && cct != null && !isNaN(cct)) {
    const ratio = Math.min(1, Math.max(0, cct / 65535));
    parsedColor = [
      Math.round(255 + (180 - 255) * ratio), // 255→180
      Math.round(180 + (210 - 180) * ratio), // 180→210
      Math.round(90 + (255 - 90) * ratio),   // 90→255
    ];
  }

  return {
    isOn,
    brightness: brightness != null && !isNaN(brightness) ? brightness : undefined,
    temperature,
    percent: percent != null && !isNaN(percent) ? percent : undefined,
    mode,
    fanSpeed,
    color: parsedColor,
    channels,
    cct: cct != null && !isNaN(cct) ? cct : undefined,
  };
}
