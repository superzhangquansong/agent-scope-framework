/**
 * 3D 户型图组件（增强版）
 *
 * <p>使用 react-three-fiber + @react-three/drei + three.js 展示一个通用户型（客厅+卧室+厨房）。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>3D 户型展示：三色纹理地面、墙壁带门洞、半透明天花板、丰富家具</li>
 *   <li>设备 3D 模型：灯光、风扇、空调、窗帘、开关</li>
 *   <li>设备控制动效：接收 deviceEvent 后匹配设备并播放对应动画</li>
 *   <li>相机智能聚焦：deviceEvent 到达后相机平滑移动到设备附近，10 秒无操作回归</li>
 *   <li>设备提取展示：1 秒后将设备提取到户型图上方单独放大展示，附带信息卡片</li>
 *   <li>灯光效果增强：RGB 灯带亮红时整个房间被染色，调光灯明显照亮家具</li>
 *   <li>科幻风格：发光边缘、霓虹色、半透明材质、粒子效果</li>
 *   <li>局部 ErrorBoundary：3D 加载失败不影响其他功能</li>
 * </ul>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { Suspense, useEffect, useRef, useState, useMemo, useCallback, Component, ReactNode, Fragment } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { OrbitControls, ContactShadows, Html } from '@react-three/drei';
import * as THREE from 'three';

// ==================== 类型定义 ====================

/** 设备属性 */
export interface DeviceAttributes {
  /**
   * 开关状态。
   * <p>HDL 设备返回的 on_off 可能是多种类型：</p>
   * <ul>
   *   <li>number: 0=关, 1=开</li>
   *   <li>string: "on"=开, "off"=关, "1"=开, "0"=关</li>
   *   <li>boolean: true=开, false=关</li>
   * </ul>
   * <p>使用时需用宽松比较判断是否开启。</p>
   */
  on_off?: number | string | boolean;
  /** RGB 颜色，如 {r:255,g:0,b:0} 或 "#ff0000" 或 "255,0,0" */
  rgb?: { r: number; g: number; b: number } | string;
  /** 亮度 0-100 */
  brightness?: number;
  /** 色温（单位 K，如 3000=暖黄, 6500=冷白） */
  cct?: number;
  /** 风扇速度 1-5 */
  speed?: number;
  /** 摆头角度 */
  swing?: number;
  /** 窗帘位置 0-100（0=全关，100=全开） */
  position?: number;
}

/** 设备控制事件 */
export interface DeviceControlEvent {
  /** 设备名称（如"客厅灯"、"卧室风扇"） */
  deviceName: string;
  /**
   * SPK 物模型编码（如 'light.rgb' / 'light.cct' / 'hvac.fan'）。
   * <p>用于精准匹配设备类型，避免设备名关键词匹配的歧义。</p>
   * <p>spk 来源：HDL 设备列表/详情接口返回的物模型字段。</p>
   */
  spk?: string;
  /** 设备属性 */
  attributes: DeviceAttributes;
}

/** 设备类型 */
type DeviceType = 'rgb_strip' | 'dimming_light' | 'cct_light' | 'fan' | 'ac' | 'curtain' | 'switch';

/** 房间类型 */
type RoomType = 'living' | 'bedroom' | 'kitchen';

/** 设备配置（3D 场景中的设备定义） */
interface DeviceConfig {
  /** 唯一 ID */
  id: string;
  /** 显示名称 */
  name: string;
  /** 设备类型 */
  type: DeviceType;
  /**
   * SPK 物模型编码（如 'light.rgb' / 'light.cct'）。
   * <p>用于与控制事件中的 spk 字段精准匹配，避免设备名关键词匹配的歧义。</p>
   */
  spk: string;
  /** 3D 空间位置 [x, y, z] */
  position: [number, number, number];
  /** 所在房间 */
  room: RoomType;
}

/** HomePlan3D 组件 Props */
interface HomePlan3DProps {
  /** 最近一次设备控制事件（变化时触发对应设备动效） */
  deviceEvent?: DeviceControlEvent | null;
  /** 自定义尺寸（px） */
  size?: number;
  /** 关闭户型图回调（用户点击 X 按钮时触发，返回主 3D 模型） */
  onClose?: () => void;
}

// ==================== 常量定义 ====================

/** 霓虹紫色（墙壁边缘、标签描边） */
const NEON_PURPLE = '#a855f7';
/** 霓虹青色（标签文字、辅助色） */
const NEON_CYAN = '#06b6d4';
/** 深色背景材质颜色 */
const DARK_BG = '#0a0a1e';
/** 墙壁高度 */
const WALL_HEIGHT = 4;
/** 地面宽度（X 方向） */
const FLOOR_WIDTH = 20;
/** 地面深度（Z 方向） */
const FLOOR_DEPTH = 12;

/** 客厅地面基础色（暖灰） */
const LIVING_FLOOR_COLOR = '#3a3530';
/** 卧室地面基础色（浅木色） */
const BEDROOM_FLOOR_COLOR = '#4a3a28';
/** 厨房地面基础色（浅蓝灰） */
const KITCHEN_FLOOR_COLOR = '#2a3038';

/**
 * 全部设备列表（增强版：14 个设备，每房间至少 3 个）。
 * <p>户型 20(x) x 12(z)，分 3 个房间：
 * 客厅 x:-10~2 z:-6~6（宽 12 深 12）；
 * 卧室 x:2~10 z:-6~0（宽 8 深 6）；
 * 厨房 x:2~10 z:0~6（宽 8 深 6）。</p>
 *
 * <p>设备分布原则：</p>
 * <ul>
 *   <li>灯类（吊灯/吸顶灯/筒灯）：天花板，照亮整个房间</li>
 *   <li>落地灯：墙角，提供局部照明</li>
 *   <li>台灯：床头柜上方，提供局部照明</li>
 *   <li>RGB 灯带：天花板边缘，可染色整个房间</li>
 *   <li>风扇/排气扇：天花板中央，覆盖整个房间</li>
 *   <li>空调：北墙高处，俯视房间</li>
 *   <li>窗帘：北墙边（z=-5.85），贴墙</li>
 *   <li>开关：内墙边（x=2 附近），便于操作</li>
 * </ul>
 */
const DEVICES: DeviceConfig[] = [
  // === 客厅设备（6 个）===
  // RGB 灯带（light.rgb，沿天花板边缘铺设，颜色可控）
  { id: 'living_rgb_strip', name: '客厅RGB灯带', type: 'rgb_strip', spk: 'light.rgb', position: [-4, 3.8, 0], room: 'living' },
  // 调光灯泡（light.dimming，吊灯位于沙发上方，亮度可控）
  { id: 'living_ceiling_light', name: '客厅吊灯', type: 'dimming_light', spk: 'light.dimming', position: [-7, 3.2, -2], room: 'living' },
  // 色温灯泡（light.cct，落地灯放在客厅东南角，亮度+色温可控）
  { id: 'living_floor_light', name: '客厅落地灯', type: 'cct_light', spk: 'light.cct', position: [-8.5, 2.2, 4], room: 'living' },
  // 电风扇（hvac.fan，吊扇位于客厅中央偏东）
  { id: 'living_fan', name: '客厅风扇', type: 'fan', spk: 'hvac.fan', position: [-1, 3.4, -2], room: 'living' },
  // 开合帘（curtain.trietex.norm，北墙中央）
  { id: 'living_curtain', name: '客厅窗帘', type: 'curtain', spk: 'curtain.trietex', position: [-4, 2, -5.85], room: 'living' },
  // 开关灯（light.switch，客厅东墙靠近过道）
  { id: 'living_switch', name: '客厅开关', type: 'switch', spk: 'light.switch', position: [1.85, 1.2, 0], room: 'living' },
  // === 卧室设备（5 个）===
  // 色温灯泡（light.cct，吸顶灯位于卧室中央）
  { id: 'bedroom_ceiling_light', name: '卧室吸顶灯', type: 'cct_light', spk: 'light.cct', position: [6, 3.5, -3], room: 'bedroom' },
  // 空调（hvac.ac，挂在卧室北墙高处）
  { id: 'bedroom_ac', name: '卧室空调', type: 'ac', spk: 'hvac.ac', position: [6, 3.2, -5.8], room: 'bedroom' },
  // 开合帘（curtain.trietex.norm，卧室北墙西侧）
  { id: 'bedroom_curtain', name: '卧室窗帘', type: 'curtain', spk: 'curtain.trietex', position: [3, 2, -5.85], room: 'bedroom' },
  // 开关灯（light.switch，卧室西墙靠近门洞）
  { id: 'bedroom_switch', name: '卧室开关', type: 'switch', spk: 'light.switch', position: [2.15, 1.2, -3], room: 'bedroom' },
  // 调光灯泡（light.dimming，床头台灯，放在左侧床头柜上）
  { id: 'bedroom_lamp', name: '卧室台灯', type: 'dimming_light', spk: 'light.dimming', position: [3.7, 1.0, -4.5], room: 'bedroom' },
  // === 厨房设备（3 个）===
  // 调光灯泡（light.dimming，筒灯位于厨房中央天花板）
  { id: 'kitchen_spot_light', name: '厨房筒灯', type: 'dimming_light', spk: 'light.dimming', position: [6, 3.5, 3], room: 'kitchen' },
  // 开关灯（light.switch，厨房西墙靠近门洞）
  { id: 'kitchen_switch', name: '厨房开关', type: 'switch', spk: 'light.switch', position: [2.15, 1.2, 3], room: 'kitchen' },
  // 排气扇（hvac.fan，厨房东北角天花板，排除油烟）
  { id: 'kitchen_exhaust_fan', name: '厨房排气扇', type: 'fan', spk: 'hvac.fan', position: [9, 3.4, 4], room: 'kitchen' },
];

// ==================== 工具函数 ====================

/**
 * 解析 RGB 颜色值为 THREE.Color。
 * @param rgb RGB 对象（{r,g,b}）或颜色字符串（"#ff0000"）
 * @returns THREE.Color 实例
 */
function parseRgbColor(rgb: DeviceAttributes['rgb']): THREE.Color {
  // 创建默认颜色实例
  const color = new THREE.Color();
  if (!rgb) {
    // 未指定颜色时默认暖白色
    color.set('#ffeecc');
  } else if (typeof rgb === 'string') {
    // HDL 返回 "r,g,b" 格式（如 "0,0,255"），需特殊处理
    if (rgb.includes(',')) {
      const parts = rgb.split(',').map(s => parseInt(s.trim(), 10));
      if (parts.length === 3 && parts.every(n => Number.isFinite(n))) {
        color.setRGB(parts[0] / 255, parts[1] / 255, parts[2] / 255);
      } else {
        color.set('#ffeecc');
      }
    } else {
      // 标准 hex 或颜色名（如 "#ff0000" 或 "red"）
      color.set(rgb);
    }
  } else {
    // 对象格式（如 {r:255, g:0, b:0}），需归一化到 0-1
    color.setRGB(rgb.r / 255, rgb.g / 255, rgb.b / 255);
  }
  return color;
}

/**
 * 将 RGB 颜色值转换为 hex 字符串（用于信息卡片显示）。
 * @param rgb RGB 对象或字符串
 * @returns hex 字符串（如 "#ff0000"）
 */
function rgbToHex(rgb: DeviceAttributes['rgb']): string {
  if (typeof rgb === 'string') {
    if (rgb.includes(',')) {
      // "255,0,0" 格式 → "#ff0000"
      const parts = rgb.split(',').map(s => parseInt(s.trim(), 10));
      if (parts.length === 3 && parts.every(n => Number.isFinite(n))) {
        return '#' + parts.map(n => Math.max(0, Math.min(255, n)).toString(16).padStart(2, '0')).join('');
      }
      return '#ffeecc';
    }
    // 已经是 hex 或颜色名
    return rgb;
  } else if (rgb && typeof rgb === 'object') {
    return '#' + [rgb.r, rgb.g, rgb.b]
      .map(n => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0'))
      .join('');
  }
  return '#ffeecc';
}

/**
 * 程序生成地面纹理（木地板或瓷砖）。
 * <p>使用 CanvasTexture 在内存中绘制纹理，避免外部资源依赖。</p>
 * <p>纹理设置 RepeatWrapping，repeat=3 让纹理在地面重复铺贴。</p>
 * @param baseColor 基础颜色（hex 字符串）
 * @param type 纹理类型：'wood' 木地板（横向木条+木纹曲线） / 'tile' 瓷砖（方格）
 * @returns THREE.CanvasTexture 实例
 */
function makeFloorTexture(baseColor: string, type: 'wood' | 'tile'): THREE.Texture {
  // 创建 256x256 的 canvas
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');
  // canvas 2D 上下文不可用时返回空纹理（避免崩溃）
  if (!ctx) {
    return new THREE.Texture();
  }

  // 填充基础颜色
  ctx.fillStyle = baseColor;
  ctx.fillRect(0, 0, 256, 256);

  if (type === 'wood') {
    // 木地板：横向木条分隔线
    ctx.strokeStyle = 'rgba(0,0,0,0.45)';
    ctx.lineWidth = 2;
    for (let y = 0; y <= 256; y += 32) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(256, y);
      ctx.stroke();
    }
    // 木纹细节（贝塞尔曲线，模拟木纹质感）
    for (let i = 0; i < 35; i++) {
      ctx.strokeStyle = `rgba(0,0,0,${0.08 + Math.random() * 0.18})`;
      ctx.lineWidth = 1;
      ctx.beginPath();
      const y = Math.random() * 256;
      ctx.moveTo(0, y);
      // 贝塞尔曲线绘制不规则木纹
      ctx.bezierCurveTo(
        85, y + (Math.random() - 0.5) * 12,
        170, y + (Math.random() - 0.5) * 12,
        256, y
      );
      ctx.stroke();
    }
    // 节疤点（小圆点模拟木节）
    for (let i = 0; i < 5; i++) {
      ctx.fillStyle = `rgba(0,0,0,${0.2 + Math.random() * 0.2})`;
      ctx.beginPath();
      ctx.arc(Math.random() * 256, Math.random() * 256, 2 + Math.random() * 3, 0, Math.PI * 2);
      ctx.fill();
    }
  } else if (type === 'tile') {
    // 瓷砖：方格分隔线
    ctx.strokeStyle = 'rgba(0,0,0,0.55)';
    ctx.lineWidth = 2;
    for (let y = 0; y <= 256; y += 64) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(256, y);
      ctx.stroke();
    }
    for (let x = 0; x <= 256; x += 64) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, 256);
      ctx.stroke();
    }
    // 瓷砖表面光泽点
    for (let i = 0; i < 30; i++) {
      ctx.fillStyle = `rgba(255,255,255,${0.05 + Math.random() * 0.1})`;
      ctx.beginPath();
      ctx.arc(Math.random() * 256, Math.random() * 256, 1 + Math.random() * 2, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  // 创建 CanvasTexture 并设置重复
  const texture = new THREE.CanvasTexture(canvas);
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.RepeatWrapping;
  texture.repeat.set(3, 3);
  // 颜色空间设置（让纹理颜色正确显示）
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

/**
 * 根据 SPK 物模型编码推断设备类型。
 * <p>SPK 是 HDL 设备的标准物模型编码，比设备名关键词更精准。</p>
 * <p>匹配规则（按 spk 包含的关键词）：</p>
 * <ul>
 *   <li>light.rgb / light.rgbw / light.rgbcw / light.rgbwaf / light.ledstrip → rgb_strip</li>
 *   <li>light.cct → cct_light</li>
 *   <li>light.dimming → dimming_light</li>
 *   <li>light.switch → switch</li>
 *   <li>hvac.fan → fan</li>
 *   <li>hvac.ac → ac</li>
 *   <li>curtain.* → curtain</li>
 * </ul>
 * @param spk SPK 物模型编码
 * @returns 推断出的设备类型；无法识别时返回 null
 */
function inferDeviceTypeBySpk(spk: string): DeviceType | null {
  if (!spk) return null;
  const s = spk.toLowerCase();
  // 灯具类（light.*）
  if (s.startsWith('light.')) {
    // RGB 系列（rgb/rgbw/rgbcw/rgbwaf/ledstrip）统一归为 rgb_strip
    if (s.includes('rgb') || s.includes('ledstrip')) return 'rgb_strip';
    // 色温灯
    if (s.includes('cct')) return 'cct_light';
    // 调光灯
    if (s.includes('dimm')) return 'dimming_light';
    // 开关灯
    if (s.includes('switch')) return 'switch';
    // 其他灯具默认归为调光灯
    return 'dimming_light';
  }
  // 暖通类（hvac.*）
  if (s.startsWith('hvac.')) {
    if (s.includes('fan')) return 'fan';
    if (s.includes('ac') || s.includes('air')) return 'ac';
    return null;
  }
  // 窗帘类（curtain.*）
  if (s.startsWith('curtain.')) return 'curtain';
  return null;
}

/**
 * 判断设备是否与控制事件匹配。
 * <p>匹配优先级：</p>
 * <ol>
 *   <li>SPK 精准匹配：事件携带 spk 时，与设备 spk 比较类型（同类型即匹配）</li>
 *   <li>房间关键词匹配：根据设备名中的"客厅/卧室/厨房"过滤房间</li>
 *   <li>设备类型关键词匹配：根据设备名中的"rgb/调温/调光/空调/风扇/窗帘/开关"过滤类型</li>
 *   <li>精确名称匹配：落地/吊灯/吸顶/筒灯等子类型关键词</li>
 * </ol>
 * @param device 设备配置
 * @param event 设备控制事件（含 deviceName + spk）
 * @returns 是否匹配
 */
function matchDevice(device: DeviceConfig, event: DeviceControlEvent): boolean {
  const deviceName = event.deviceName || '';
  const eventSpk = event.spk || '';
  console.log('[HomePlan3D] matchDevice: 尝试匹配', { deviceName, eventSpk }, '→', device.name, `(${device.spk})`);

  // ===== 1. SPK 精准匹配（优先级最高，spk 存在时直接用 spk 判断类型）=====
  if (eventSpk) {
    const eventType = inferDeviceTypeBySpk(eventSpk);
    const deviceType = inferDeviceTypeBySpk(device.spk);
    console.log('[HomePlan3D]   SPK 类型推断: 事件=', eventType, '设备=', deviceType);
    // spk 类型必须一致才匹配（同类型设备即可，不要求 spk 完全相同）
    if (eventType && deviceType && eventType === deviceType) {
      // 进一步用房间关键词过滤（如果设备名包含房间信息）
      const name = deviceName.toLowerCase();
      let roomOk = true;
      if (name.includes('客厅') || name.includes('living')) {
        roomOk = device.room === 'living';
      } else if (name.includes('卧室') || name.includes('bedroom')) {
        roomOk = device.room === 'bedroom';
      } else if (name.includes('厨房') || name.includes('kitchen')) {
        roomOk = device.room === 'kitchen';
      }
      if (!roomOk) {
        console.log('[HomePlan3D]   SPK 类型匹配但房间不匹配');
        return false;
      }
      console.log('[HomePlan3D]   ✓ SPK 匹配成功!');
      return true;
    }
    // spk 类型不匹配，直接返回 false（spk 是权威类型标识，不回退到名称匹配）
    if (eventType && deviceType && eventType !== deviceType) {
      console.log('[HomePlan3D]   SPK 类型不匹配');
      return false;
    }
  }

  // ===== 2. 名称关键词匹配（spk 不存在时的回退方案）=====
  const name = deviceName.toLowerCase();

  // 2.1 房间关键词匹配
  let roomMatch = true;
  if (name.includes('客厅') || name.includes('living')) {
    roomMatch = device.room === 'living';
  } else if (name.includes('卧室') || name.includes('bedroom')) {
    roomMatch = device.room === 'bedroom';
  } else if (name.includes('厨房') || name.includes('kitchen')) {
    roomMatch = device.room === 'kitchen';
  }
  if (!roomMatch) {
    console.log('[HomePlan3D]   房间不匹配');
    return false;
  }

  // 2.2 设备类型关键词匹配
  let typeMatch = true;
  if (name.includes('空调') || name.includes('aircon') || /\bac\b/.test(name)) {
    typeMatch = device.type === 'ac';
  } else if (name.includes('扇') || name.includes('fan')) {
    typeMatch = device.type === 'fan';
  } else if (name.includes('帘') || name.includes('curtain')) {
    typeMatch = device.type === 'curtain';
  } else if (name.includes('开关') || name.includes('switch')) {
    typeMatch = device.type === 'switch';
  } else if (name.includes('rgb') || name.includes('灯带')) {
    typeMatch = device.type === 'rgb_strip';
  } else if (name.includes('色温') || name.includes('调温') || name.includes('cct')) {
    typeMatch = device.type === 'cct_light';
  } else if (name.includes('调光') || name.includes('灯') || name.includes('light')) {
    typeMatch = device.type === 'dimming_light';
  }
  if (!typeMatch) {
    console.log('[HomePlan3D]   类型不匹配');
    return false;
  }

  // 2.3 精确名称匹配（区分同房间同类型多个设备）
  if (name.includes('落地') && !device.name.includes('落地')) return false;
  if (name.includes('吊灯') && !device.name.includes('吊灯')) return false;
  if (name.includes('吸顶') && !device.name.includes('吸顶')) return false;
  if (name.includes('筒灯') && !device.name.includes('筒灯')) return false;
  if (name.includes('台灯') && !device.name.includes('台灯')) return false;

  console.log('[HomePlan3D]   ✓ 名称匹配成功!');
  return true;
}

/**
 * 判断 on_off 是否为开启状态（宽松比较，兼容多种类型）。
 * @param onOff 开关状态值
 * @returns 是否开启
 */
function isOnValue(onOff: DeviceAttributes['on_off']): boolean {
  return onOff === 1 || onOff === '1' || onOff === 'on' || onOff === true;
}

// ==================== 通用动画 Hook ====================

/**
 * 设备脉冲缩放 Hook。
 * <p>当 deviceEvent 引用变化时，目标缩放设为 multiplier，150ms 后恢复为 1。
 * 在 useFrame 中通过 lerp 平滑过渡，实现弹性恢复效果。</p>
 * @param deviceEvent 设备控制事件（null 时不触发）
 * @param multiplier 触发时的目标缩放倍率（如 1.3 或 1.5）
 * @returns 目标缩放值的 ref（在 useFrame 中读取并 lerp）
 */
function usePulseScale(deviceEvent: DeviceControlEvent | null, multiplier: number) {
  // 目标缩放倍率（动画终点）
  const targetScale = useRef(1);
  // 记录上次处理的事件引用，避免重复触发
  const lastEvent = useRef<DeviceControlEvent | null>(null);

  useEffect(() => {
    // 仅当事件存在且与上次不同时触发
    if (deviceEvent && deviceEvent !== lastEvent.current) {
      lastEvent.current = deviceEvent;
      // 立即设为放大倍率
      targetScale.current = multiplier;
      // 150ms 后恢复为 1（useFrame 会平滑过渡）
      const timer = setTimeout(() => {
        targetScale.current = 1;
      }, 150);
      return () => clearTimeout(timer);
    }
  }, [deviceEvent, multiplier]);

  return targetScale;
}

// ==================== 脉冲光环组件 ====================

/** PulseRing Props */
interface PulseRingProps {
  /** 触发事件（引用变化时播放光环动画） */
  trigger: DeviceControlEvent | null;
  /** 光环中心在地面上的 X 坐标 */
  x: number;
  /** 光环中心在地面上的 Z 坐标 */
  z: number;
}

/**
 * 脉冲光环组件。
 * <p>当 trigger 变化时，从设备正下方地面生成一个向外扩散并淡出的紫色光环，
 * 用于高亮被控制的设备。</p>
 */
function PulseRing({ trigger, x, z }: PulseRingProps) {
  // 光环 mesh 引用
  const meshRef = useRef<THREE.Mesh>(null);
  // 动画状态：透明度、缩放、是否激活
  const animState = useRef({ opacity: 0, scale: 0.5, active: false });
  // 记录上次触发事件，避免重复
  const lastTrigger = useRef<DeviceControlEvent | null>(null);

  // 事件变化时启动光环动画
  useEffect(() => {
    if (trigger && trigger !== lastTrigger.current) {
      lastTrigger.current = trigger;
      // 初始状态：半透明、小尺寸、激活
      animState.current = { opacity: 0.8, scale: 0.5, active: true };
    }
  }, [trigger]);

  // 每帧更新光环扩散和淡出
  useFrame(() => {
    if (!meshRef.current) return;
    const s = animState.current;
    if (!s.active) {
      // 未激活时隐藏
      meshRef.current.visible = false;
      return;
    }
    // 激活时显示并更新缩放和透明度
    meshRef.current.visible = true;
    meshRef.current.scale.setScalar(s.scale);
    const mat = meshRef.current.material as THREE.MeshBasicMaterial;
    mat.opacity = s.opacity;
    // 每帧扩散并淡出
    s.scale += 0.06;
    s.opacity -= 0.02;
    // 完全淡出后停止
    if (s.opacity <= 0) {
      s.active = false;
    }
  });

  return (
    // 水平放置的圆环（旋转 -PI/2 使其平铺在地面）
    <mesh ref={meshRef} rotation={[-Math.PI / 2, 0, 0]} position={[x, 0.03, z]} visible={false}>
      <ringGeometry args={[0.4, 0.55, 32]} />
      <meshBasicMaterial color={NEON_PURPLE} transparent opacity={0} side={THREE.DoubleSide} />
    </mesh>
  );
}

// ==================== 设备名称标签组件 ====================

/** DeviceLabel Props */
interface DeviceLabelProps {
  /** 设备配置 */
  device: DeviceConfig;
  /** 设备控制事件（非 null 时表示该设备被控制） */
  deviceEvent: DeviceControlEvent | null;
}

/**
 * 设备名称浮标组件。
 * <p>当设备被控制时，在设备上方显示名称标签，2 秒后自动隐藏。</p>
 */
function DeviceLabel({ device, deviceEvent }: DeviceLabelProps) {
  // 是否显示标签
  const [show, setShow] = useState(false);

  useEffect(() => {
    if (deviceEvent) {
      // 收到事件时显示
      setShow(true);
      // 2 秒后隐藏
      const timer = setTimeout(() => setShow(false), 2000);
      return () => clearTimeout(timer);
    }
  }, [deviceEvent]);

  // 未显示时不渲染
  if (!show) return null;

  return (
    <Html position={device.position} center distanceFactor={10}>
      <div style={{
        background: 'rgba(15, 12, 30, 0.9)',
        border: `1px solid ${NEON_PURPLE}`,
        borderRadius: '4px',
        padding: '2px 8px',
        fontSize: '11px',
        color: NEON_CYAN,
        fontFamily: 'monospace',
        whiteSpace: 'nowrap',
        pointerEvents: 'none',
      }}>
        {device.name}
      </div>
    </Html>
  );
}

// ==================== 调光灯设备（light.dimming） ====================

/** DimmingLightDevice Props */
interface DimmingLightDeviceProps {
  device: DeviceConfig;
  deviceEvent: DeviceControlEvent | null;
}

/**
 * 调光灯设备组件（light.dimming）。
 * <p>参考设备列表 DeviceImage.tsx 的 DimmingLightDevice 样式：
 * 半球形吸顶灯 + 8 道光线辐射 + 亮度条。</p>
 * <p>支持 on_off + brightness 控制，灯亮时照亮整个房间。</p>
 * <p>调光灯默认暖金色光（无 rgb 属性，只有亮度控制）。</p>
 */
function DimmingLightDevice({ device, deviceEvent }: DimmingLightDeviceProps) {
  // 半球灯罩引用（发光部分）
  const domeRef = useRef<THREE.Mesh>(null);
  // 8 道光线引用（点亮时辐射状光线）
  const rayRefs = useRef<Array<THREE.Mesh | null>>([]);
  // 主点光源引用（强光照亮房间）
  const lightRef = useRef<THREE.PointLight>(null);
  // 辅助点光源引用（增加光照范围）
  const light2Ref = useRef<THREE.PointLight>(null);
  // 当前设备属性（跨帧持久化）
  const attrs = useRef<DeviceAttributes>({});
  // 脉冲缩放（灯光被控制时放大 1.5 倍）
  const targetScale = usePulseScale(deviceEvent, 1.5);

  // 事件到达时更新属性（合并而非覆盖，保持之前设置的状态）
  useEffect(() => {
    if (deviceEvent) {
      attrs.current = { ...attrs.current, ...deviceEvent.attributes };
      console.log('[HomePlan3D] DimmingLightDevice 属性更新:', device.name,
        '合并后:', attrs.current);
    }
  }, [deviceEvent]);

  // 每帧更新发光状态和缩放动画
  useFrame(() => {
    const a = attrs.current;
    // 是否点亮（宽松比较，兼容 string "1"/"on" 和 number 1/NaN）
    const isOn = isOnValue(a.on_off);
    // 调光灯默认暖金色光（如果有 rgb 也支持，但通常只有 brightness）
    const color = a.rgb ? parseRgbColor(a.rgb) : new THREE.Color('#faad14');
    // 光照强度 = 亮度 / 100（brightness 为 0/空/NaN 时用默认值 50）
    const brightNum = Number(a.brightness);
    const brightness = Number.isFinite(brightNum) && brightNum > 0 ? brightNum : 50;
    const intensity = isOn ? brightness / 100 : 0;

    // 更新半球灯罩发光材质
    if (domeRef.current) {
      const mat = domeRef.current.material as THREE.MeshStandardMaterial;
      if (isOn) {
        // 点亮：emissive 颜色 = 暖金色，强度 = brightness/100
        mat.emissive.copy(color);
        mat.emissiveIntensity = intensity * 3;
      } else {
        mat.emissive.setHex(0x000000);
        mat.emissiveIntensity = 0;
      }
      // 脉冲缩放动画（lerp 平滑过渡）
      const next = THREE.MathUtils.lerp(domeRef.current.scale.x, targetScale.current, 0.15);
      domeRef.current.scale.setScalar(next);
    }

    // 更新 8 道光线（点亮时显示辐射状光线，长度随亮度变化）
    const rayLength = 0.15 + intensity * 0.35;
    for (let i = 0; i < 8; i++) {
      const ray = rayRefs.current[i];
      if (!ray) continue;
      const mat = ray.material as THREE.MeshBasicMaterial;
      if (isOn) {
        mat.color.copy(color);
        mat.opacity = 0.5 * intensity;
        ray.visible = true;
        ray.scale.y = rayLength / 0.15; // 基础长度 0.15，按亮度缩放
      } else {
        ray.visible = false;
      }
    }

    // 更新 2 个点光源（强光照亮整个房间，distance=14 覆盖 7m 半径范围）
    // 光源强度 = 亮度 × 15（增强光照，让房间明显变亮，家具清晰可见）
    const lightIntensity = intensity * 15;
    if (lightRef.current) {
      lightRef.current.color.copy(color);
      lightRef.current.intensity = lightIntensity;
    }
    if (light2Ref.current) {
      light2Ref.current.color.copy(color);
      light2Ref.current.intensity = lightIntensity * 0.6;
    }
  });

  return (
    <>
      <group position={device.position}>
        {/* 顶部吊线（连接天花板，细圆柱） */}
        <mesh position={[0, 0.4, 0]}>
          <cylinderGeometry args={[0.015, 0.015, 0.3, 8]} />
          <meshStandardMaterial color="#333344" metalness={0.8} roughness={0.3} />
        </mesh>
        {/* 金属灯座（圆盘，吸顶式） */}
        <mesh position={[0, 0.22, 0]}>
          <cylinderGeometry args={[0.45, 0.45, 0.06, 24]} />
          <meshStandardMaterial color="#666677" metalness={0.9} roughness={0.2} />
        </mesh>
        {/* 半球灯罩（朝下发光，像碗倒扣） */}
        <mesh ref={domeRef} position={[0, 0.12, 0]} rotation={[Math.PI, 0, 0]}>
          <sphereGeometry args={[0.4, 32, 16, 0, Math.PI * 2, 0, Math.PI / 2]} />
          <meshStandardMaterial
            color="#2a2a3e"
            emissive="#000000"
            emissiveIntensity={0}
            metalness={0.2}
            roughness={0.3}
            transparent
            opacity={0.8}
            side={THREE.DoubleSide}
          />
        </mesh>
        {/* 8 道光线（点亮时从灯罩底部辐射状射出，模拟光照散射） */}
        {Array.from({ length: 8 }).map((_, i) => {
          const angle = (i * Math.PI) / 4; // 8 等分圆周
          const radius = 0.45;
          return (
            <mesh
              key={i}
              ref={el => { rayRefs.current[i] = el; }}
              position={[Math.cos(angle) * radius, -0.05, Math.sin(angle) * radius]}
              rotation={[0, -angle, Math.PI / 2]}
              visible={false}
            >
              <boxGeometry args={[0.02, 0.15, 0.02]} />
              <meshBasicMaterial color="#faad14" transparent opacity={0} />
            </mesh>
          );
        })}
        {/* 主点光源（强光照亮整个房间） */}
        <pointLight ref={lightRef} position={[0, 0, 0]} intensity={0} distance={14} decay={1.2} castShadow />
        {/* 辅助点光源（增加光照覆盖范围） */}
        <pointLight ref={light2Ref} position={[0, -0.3, 0]} intensity={0} distance={11} decay={1.4} />
      </group>
      {/* 地面脉冲光环 */}
      <PulseRing trigger={deviceEvent} x={device.position[0]} z={device.position[2]} />
    </>
  );
}

// ==================== 色温灯设备（light.cct） ====================

/** CctLightDevice Props */
interface CctLightDeviceProps {
  device: DeviceConfig;
  deviceEvent: DeviceControlEvent | null;
}

/**
 * 色温灯设备组件（light.cct）。
 * <p>参考设备列表 DeviceImage.tsx 的 CCTLightDevice 样式：
 * 灯泡造型 + 色温颜色 + 底部色温渐变条。</p>
 * <p>支持 on_off + brightness + cct 控制（暖白→冷白）。</p>
 * <p>cct 色温映射：< 4000K 暖黄, 4000-5000K 中性白, > 5000K 冷白。</p>
 */
function CctLightDevice({ device, deviceEvent }: CctLightDeviceProps) {
  // 灯泡球体引用（发光部分）
  const bulbRef = useRef<THREE.Mesh>(null);
  // 灯丝引用（内部细线）
  const filamentRef = useRef<THREE.Mesh>(null);
  // 色温渐变条引用（底部色温指示条）
  const cctBarRef = useRef<THREE.Mesh>(null);
  // 主点光源引用
  const lightRef = useRef<THREE.PointLight>(null);
  // 辅助点光源引用
  const light2Ref = useRef<THREE.PointLight>(null);
  // 当前设备属性（跨帧持久化）
  const attrs = useRef<DeviceAttributes>({});
  // 脉冲缩放（灯光被控制时放大 1.5 倍）
  const targetScale = usePulseScale(deviceEvent, 1.5);

  // 事件到达时更新属性（合并而非覆盖，保持之前设置的状态）
  useEffect(() => {
    if (deviceEvent) {
      attrs.current = { ...attrs.current, ...deviceEvent.attributes };
      console.log('[HomePlan3D] CctLightDevice 属性更新:', device.name,
        '合并后:', attrs.current);
    }
  }, [deviceEvent]);

  // 每帧更新发光状态和缩放动画
  useFrame(() => {
    const a = attrs.current;
    // 是否点亮
    const isOn = isOnValue(a.on_off);
    // cct 色温映射：< 4000K 暖黄, 4000-5000K 中性白, > 5000K 冷白
    let color: THREE.Color;
    const cctNum = Number(a.cct);
    if (Number.isFinite(cctNum) && cctNum > 0) {
      if (cctNum < 4000) {
        color = new THREE.Color('#ff9944'); // 暖黄
      } else if (cctNum < 5000) {
        color = new THREE.Color('#ffeecc'); // 中性白
      } else {
        color = new THREE.Color('#ccddff'); // 冷白
      }
    } else {
      color = new THREE.Color('#faad14'); // 默认暖金色
    }
    // 光照强度 = 亮度 / 100（brightness 为 0/空/NaN 时用默认值 50）
    const brightNum = Number(a.brightness);
    const brightness = Number.isFinite(brightNum) && brightNum > 0 ? brightNum : 50;
    const intensity = isOn ? brightness / 100 : 0;

    // 更新灯泡发光材质（玻璃灯罩）
    if (bulbRef.current) {
      const mat = bulbRef.current.material as THREE.MeshStandardMaterial;
      if (isOn) {
        mat.emissive.copy(color);
        mat.emissiveIntensity = intensity * 3;
      } else {
        mat.emissive.setHex(0x000000);
        mat.emissiveIntensity = 0;
      }
      // 脉冲缩放动画
      const next = THREE.MathUtils.lerp(bulbRef.current.scale.x, targetScale.current, 0.15);
      bulbRef.current.scale.setScalar(next);
    }

    // 更新灯丝发光（内部细线，点亮时高亮）
    if (filamentRef.current) {
      const mat = filamentRef.current.material as THREE.MeshStandardMaterial;
      if (isOn) {
        mat.emissive.copy(color);
        mat.emissiveIntensity = intensity * 4;
      } else {
        mat.emissive.setHex(0x222222);
        mat.emissiveIntensity = 0;
      }
    }

    // 更新色温渐变条（点亮时显示色温对应的颜色）
    if (cctBarRef.current) {
      const mat = cctBarRef.current.material as THREE.MeshStandardMaterial;
      if (isOn) {
        mat.emissive.copy(color);
        mat.emissiveIntensity = intensity * 2;
      } else {
        mat.emissive.setHex(0x000000);
        mat.emissiveIntensity = 0;
      }
    }

    // 更新 2 个点光源（强光照亮整个房间，distance=14 覆盖 7m 半径范围）
    // 光源强度 = 亮度 × 15（增强光照，让房间明显变亮）
    const lightIntensity = intensity * 15;
    if (lightRef.current) {
      lightRef.current.color.copy(color);
      lightRef.current.intensity = lightIntensity;
    }
    if (light2Ref.current) {
      light2Ref.current.color.copy(color);
      light2Ref.current.intensity = lightIntensity * 0.6;
    }
  });

  return (
    <>
      <group position={device.position}>
        {/* 顶部吊线（连接天花板，细圆柱） */}
        <mesh position={[0, 0.5, 0]}>
          <cylinderGeometry args={[0.015, 0.015, 0.4, 8]} />
          <meshStandardMaterial color="#333344" metalness={0.8} roughness={0.3} />
        </mesh>
        {/* 金属灯座（圆台，上窄下宽） */}
        <mesh position={[0, 0.25, 0]}>
          <cylinderGeometry args={[0.15, 0.22, 0.2, 16]} />
          <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.2} />
        </mesh>
        {/* 灯罩外环（圆环，金属装饰） */}
        <mesh position={[0, 0.08, 0]}>
          <torusGeometry args={[0.3, 0.04, 8, 24]} />
          <meshStandardMaterial color="#666677" metalness={0.9} roughness={0.15} />
        </mesh>
        {/* 灯泡玻璃壳（球体，半透明） */}
        <mesh ref={bulbRef} position={[0, 0, 0]}>
          <sphereGeometry args={[0.28, 32, 32]} />
          <meshStandardMaterial
            color="#222234"
            emissive="#000000"
            emissiveIntensity={0}
            metalness={0.1}
            roughness={0.1}
            transparent
            opacity={0.6}
          />
        </mesh>
        {/* 内部灯丝（螺旋线，点亮时高亮发光） */}
        <mesh ref={filamentRef} position={[0, -0.02, 0]}>
          <torusKnotGeometry args={[0.06, 0.015, 32, 8, 2, 3]} />
          <meshStandardMaterial
            color="#444444"
            emissive="#222222"
            emissiveIntensity={0}
            metalness={0.6}
            roughness={0.4}
          />
        </mesh>
        {/* 色温渐变条（底部环状，点亮时显示色温对应颜色） */}
        <mesh ref={cctBarRef} position={[0, -0.32, 0]}>
          <torusGeometry args={[0.25, 0.025, 8, 24]} />
          <meshStandardMaterial
            color="#333344"
            emissive="#000000"
            emissiveIntensity={0}
            metalness={0.5}
            roughness={0.3}
          />
        </mesh>
        {/* 主点光源（强光照亮整个房间） */}
        <pointLight ref={lightRef} position={[0, 0, 0]} intensity={0} distance={14} decay={1.2} castShadow />
        {/* 辅助点光源（增加光照覆盖范围） */}
        <pointLight ref={light2Ref} position={[0, -0.3, 0]} intensity={0} distance={11} decay={1.4} />
      </group>
      {/* 地面脉冲光环 */}
      <PulseRing trigger={deviceEvent} x={device.position[0]} z={device.position[2]} />
    </>
  );
}

// ==================== RGB 灯带设备 ====================

/** RgbStripDevice Props */
interface RgbStripDeviceProps {
  device: DeviceConfig;
  deviceEvent: DeviceControlEvent | null;
}

/** RGB 灯带 LED 灯珠数量（沿灯带均匀分布，降低以节省内存） */
const RGB_STRIP_BEAD_COUNT = 8;

/**
 * RGB 灯带设备组件（light.rgb）。
 * <p>沿天花板边缘铺设的逼真灯带造型：铝合金外壳 + 柔光罩 + LED 灯珠阵列 + 多点光源。</p>
 * <p>参考设备列表 DeviceImage.tsx 的 LedStripDevice 样式：
 * 横向灯带 + 15 颗 LED 灯珠 + 流光动效 + 彩色光晕。</p>
 * <p>支持 on_off + rgb + brightness 控制，灯亮时照亮整个房间并显示对应颜色。</p>
 */
function RgbStripDevice({ device, deviceEvent }: RgbStripDeviceProps) {
  // 灯带柔光罩引用（整体发光条）
  const stripRef = useRef<THREE.Mesh>(null);
  // LED 灯珠组引用（15 颗小球，逐颗发光）
  const beadRefs = useRef<Array<THREE.Mesh | null>>([]);
  // 3 个点光源引用（沿灯带均匀分布，强光照亮整个客厅，减少光源数量节省内存）
  const light1Ref = useRef<THREE.PointLight>(null);
  const light2Ref = useRef<THREE.PointLight>(null);
  const light3Ref = useRef<THREE.PointLight>(null);
  // 当前设备属性（跨帧持久化）
  const attrs = useRef<DeviceAttributes>({});
  // 脉冲缩放（灯带被控制时放大 1.3 倍）
  const targetScale = usePulseScale(deviceEvent, 1.3);

  // 事件到达时更新属性（合并而非覆盖，保持之前设置的状态）
  useEffect(() => {
    if (deviceEvent) {
      attrs.current = { ...attrs.current, ...deviceEvent.attributes };
      console.log('[HomePlan3D] RgbStripDevice 属性更新:', device.name,
        '合并后:', attrs.current);
    }
  }, [deviceEvent]);

  // 每帧更新发光状态和缩放动画
  useFrame((state) => {
    const a = attrs.current;
    const isOn = isOnValue(a.on_off);
    let color: THREE.Color;
    if (a.rgb) {
      color = parseRgbColor(a.rgb);
    } else {
      color = parseRgbColor(undefined);
    }
    const brightNum = Number(a.brightness);
    const brightness = Number.isFinite(brightNum) && brightNum > 0 ? brightNum : 50;
    const intensity = isOn ? brightness / 100 : 0;

    // 更新灯带柔光罩发光材质
    if (stripRef.current) {
      const mat = stripRef.current.material as THREE.MeshStandardMaterial;
      if (isOn) {
        mat.emissive.copy(color);
        mat.emissiveIntensity = intensity * 3;
      } else {
        mat.emissive.setHex(0x000000);
        mat.emissiveIntensity = 0;
      }
      const next = THREE.MathUtils.lerp(stripRef.current.scale.y, targetScale.current, 0.15);
      stripRef.current.scale.set(1, next, 1);
    }

    // 更新 15 颗 LED 灯珠（逐颗脉冲发光，形成流光动效）
    const elapsed = state.clock.elapsedTime;
    for (let i = 0; i < RGB_STRIP_BEAD_COUNT; i++) {
      const bead = beadRefs.current[i];
      if (!bead) continue;
      const mat = bead.material as THREE.MeshStandardMaterial;
      if (isOn) {
        // 灯珠颜色 = rgb 颜色，强度按正弦波脉冲（每颗灯珠有相位偏移，形成流动效果）
        mat.emissive.copy(color);
        const pulse = 0.6 + 0.4 * Math.sin(elapsed * 3 + i * 0.5);
        mat.emissiveIntensity = intensity * pulse * 4;
        mat.color.copy(color);
      } else {
        mat.emissive.setHex(0x000000);
        mat.emissiveIntensity = 0;
        mat.color.setHex(0x222234);
      }
    }

    // 更新 3 个点光源（强光照亮整个客厅，distance=16 覆盖 8m 半径范围）
    // 光源强度 = 亮度 × 18（增强光照，确保 RGB 灯带亮红时整个房间被染色）
    const lightIntensity = intensity * 18;
    [light1Ref, light2Ref, light3Ref].forEach(ref => {
      if (ref.current) {
        ref.current.color.copy(color);
        ref.current.intensity = lightIntensity;
      }
    });
  });

  // 计算 15 颗 LED 灯珠的 X 坐标（沿灯带均匀分布，间距 = 4.0 / 15）
  const beadSpacing = 4.0 / RGB_STRIP_BEAD_COUNT;
  const beadStartX = -2.0 + beadSpacing / 2;

  return (
    <>
      <group position={device.position}>
        {/* 铝合金外壳（U 型槽，深灰色金属质感） */}
        <mesh position={[0, 0.04, 0]}>
          <boxGeometry args={[4.2, 0.08, 0.18]} />
          <meshStandardMaterial color="#333344" metalness={0.9} roughness={0.2} />
        </mesh>
        {/* 柔光罩（半透明发光条，乳白色） */}
        <mesh ref={stripRef} position={[0, 0.02, 0]}>
          <boxGeometry args={[4.0, 0.04, 0.12]} />
          <meshStandardMaterial
            color="#1a1a2e"
            emissive="#000000"
            emissiveIntensity={0}
            metalness={0.1}
            roughness={0.2}
            transparent
            opacity={0.7}
          />
        </mesh>
        {/* 15 颗 LED 灯珠（沿灯带均匀分布，点亮时彩色脉冲发光） */}
        {Array.from({ length: RGB_STRIP_BEAD_COUNT }).map((_, i) => (
          <mesh
            key={i}
            ref={el => { beadRefs.current[i] = el; }}
            position={[beadStartX + i * beadSpacing, 0.02, 0]}
          >
            <sphereGeometry args={[0.035, 12, 12]} />
            <meshStandardMaterial
              color="#222234"
              emissive="#000000"
              emissiveIntensity={0}
              metalness={0.3}
              roughness={0.3}
            />
          </mesh>
        ))}
        {/* 3 个点光源（沿灯带均匀分布，强光照亮整个客厅） */}
        <pointLight ref={light1Ref} position={[-1.6, -0.5, 0]} intensity={0} distance={16} decay={1.1} />
        <pointLight ref={light2Ref} position={[0, -0.5, 0]} intensity={0} distance={16} decay={1.1} />
        <pointLight ref={light3Ref} position={[1.6, -0.5, 0]} intensity={0} distance={16} decay={1.1} />
      </group>
      <PulseRing trigger={deviceEvent} x={device.position[0]} z={device.position[2]} />
    </>
  );
}

// ==================== 风扇设备 ====================

/** FanDevice Props */
interface FanDeviceProps {
  device: DeviceConfig;
  deviceEvent: DeviceControlEvent | null;
}

/**
 * 风扇设备组件。
 * <p>圆柱底座 + 4 片扇叶，支持开关/速度/摆头控制。</p>
 * <p>动效：on_off=1 时扇叶旋转（速度 = speed*2 rad/s），swing>0 时摆头。</p>
 */
function FanDevice({ device, deviceEvent }: FanDeviceProps) {
  // 底座引用（脉冲缩放目标）
  const baseRef = useRef<THREE.Mesh>(null);
  // 风扇头引用（摆头旋转目标）
  const headRef = useRef<THREE.Group>(null);
  // 扇叶组引用（自旋目标）
  const bladeRef = useRef<THREE.Group>(null);
  // 当前设备属性
  const attrs = useRef<DeviceAttributes>({});
  // 脉冲缩放（被控制时底座放大 1.3 倍）
  const targetScale = usePulseScale(deviceEvent, 1.3);

  // 事件到达时更新属性
  useEffect(() => {
    if (deviceEvent) {
      attrs.current = { ...attrs.current, ...deviceEvent.attributes };
    }
  }, [deviceEvent]);

  // 每帧更新扇叶旋转和摆头
  useFrame((_, delta) => {
    const a = attrs.current;

    // 底座脉冲缩放动画
    if (baseRef.current) {
      const next = THREE.MathUtils.lerp(baseRef.current.scale.x, targetScale.current, 0.15);
      baseRef.current.scale.setScalar(next);
    }

    // 扇叶旋转（设备开启时，角速度 = speed * 2 rad/s）
    const fanOn = isOnValue(a.on_off);
    if (bladeRef.current && fanOn) {
      const speed = (a.speed ?? 1) * 2;
      bladeRef.current.rotation.y += speed * delta;
    }

    // 摆头（设备开启且 swing>0 时，整个风扇头左右摆动）
    if (headRef.current && fanOn && (a.swing ?? 0) > 0) {
      const swingDeg = a.swing ?? 0;
      // sin 波形摆动
      headRef.current.rotation.y = Math.sin(performance.now() * 0.0008) * (swingDeg * Math.PI / 180);
    }
  });

  return (
    <>
      <group position={device.position}>
        {/* 底座（连接天花板） */}
        <mesh ref={baseRef}>
          <cylinderGeometry args={[0.15, 0.2, 0.1, 16]} />
          <meshStandardMaterial color="#1a1a2e" metalness={0.7} roughness={0.3} />
        </mesh>
        {/* 连接杆 */}
        <mesh position={[0, -0.2, 0]}>
          <cylinderGeometry args={[0.03, 0.03, 0.3, 8]} />
          <meshStandardMaterial color="#333344" metalness={0.8} roughness={0.2} />
        </mesh>
        {/* 风扇头（摆头组） */}
        <group ref={headRef} position={[0, -0.4, 0]}>
          {/* 电机外壳 */}
          <mesh>
            <cylinderGeometry args={[0.12, 0.12, 0.15, 16]} />
            <meshStandardMaterial color="#2a2a3e" metalness={0.7} roughness={0.3} />
          </mesh>
          {/* 扇叶组（绕 Y 轴旋转） */}
          <group ref={bladeRef} position={[0, -0.08, 0]}>
            {[0, 1, 2, 3].map(i => (
              <mesh key={i} rotation={[0, (i * Math.PI) / 2, 0]} position={[0.35, 0, 0]}>
                <boxGeometry args={[0.6, 0.015, 0.08]} />
                <meshStandardMaterial color="#3a3a5e" metalness={0.5} roughness={0.4} transparent opacity={0.8} />
              </mesh>
            ))}
          </group>
        </group>
      </group>
      {/* 地面脉冲光环 */}
      <PulseRing trigger={deviceEvent} x={device.position[0]} z={device.position[2]} />
    </>
  );
}

// ==================== 空调设备 ====================

/** AcDevice Props */
interface AcDeviceProps {
  device: DeviceConfig;
  deviceEvent: DeviceControlEvent | null;
}

/** 空调冷气粒子数量（降低以节省内存） */
const AC_PARTICLE_COUNT = 10;

/**
 * 空调冷气粒子组件。
 * <p>白色小点从出风口飘出并向下飘落，循环往复。</p>
 * @param active 是否激活（on_off=1 时为 true）
 */
function AcParticles({ active }: { active: boolean }) {
  // 粒子点云引用
  const pointsRef = useRef<THREE.Points>(null);

  // 初始化粒子位置数组和速度数组（仅计算一次）
  const { positions, velocities } = useMemo(() => {
    const pos = new Float32Array(AC_PARTICLE_COUNT * 3);
    const vel = new Float32Array(AC_PARTICLE_COUNT * 3);
    for (let i = 0; i < AC_PARTICLE_COUNT; i++) {
      pos[i * 3] = (Math.random() - 0.5) * 1.6;      // x: 出风口宽度范围内随机
      pos[i * 3 + 1] = 0;                               // y: 从出风口开始
      pos[i * 3 + 2] = (Math.random() - 0.5) * 0.2;    // z: 小范围随机
      vel[i * 3] = (Math.random() - 0.5) * 0.005;      // x 方向微飘
      vel[i * 3 + 1] = -0.015 - Math.random() * 0.015;  // y 方向下落
      vel[i * 3 + 2] = (Math.random() - 0.5) * 0.005;  // z 方向微飘
    }
    return { positions: pos, velocities: vel };
  }, []);

  // 每帧更新粒子位置
  useFrame(() => {
    if (!pointsRef.current || !active) return;
    const geom = pointsRef.current.geometry;
    const pos = geom.attributes.position.array as Float32Array;
    // 逐帧更新每个粒子位置
    for (let i = 0; i < AC_PARTICLE_COUNT; i++) {
      pos[i * 3] += velocities[i * 3];
      pos[i * 3 + 1] += velocities[i * 3 + 1];
      pos[i * 3 + 2] += velocities[i * 3 + 2];
      // 粒子落到底部后重置到出风口
      if (pos[i * 3 + 1] < -2.5) {
        pos[i * 3] = (Math.random() - 0.5) * 1.6;
        pos[i * 3 + 1] = 0;
        pos[i * 3 + 2] = (Math.random() - 0.5) * 0.2;
      }
    }
    geom.attributes.position.needsUpdate = true;
  });

  return (
    <points ref={pointsRef} position={[0, -0.35, 0.2]} visible={active}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
      </bufferGeometry>
      <pointsMaterial size={0.08} color="#ffffff" transparent opacity={0.5} sizeAttenuation />
    </points>
  );
}

/**
 * 空调设备组件。
 * <p>长方体主体 + 可摆动导风板 + 冷气粒子，支持开关/摆头/色温控制。</p>
 * <p>动效：on_off=1 时导风板摆动 + 冷气粒子 + 主体发光（制冷蓝/制热红）。</p>
 */
function AcDevice({ device, deviceEvent }: AcDeviceProps) {
  // 主体引用
  const bodyRef = useRef<THREE.Mesh>(null);
  // 导风板引用
  const flapRef = useRef<THREE.Mesh>(null);
  // 当前设备属性
  const attrs = useRef<DeviceAttributes>({});
  // 脉冲缩放
  const targetScale = usePulseScale(deviceEvent, 1.3);
  // 开关状态（控制粒子显隐）
  const [isOn, setIsOn] = useState(false);

  // 事件到达时更新属性
  useEffect(() => {
    if (deviceEvent) {
      attrs.current = { ...attrs.current, ...deviceEvent.attributes };
      if (deviceEvent.attributes.on_off !== undefined) {
        setIsOn(isOnValue(deviceEvent.attributes.on_off));
      }
    }
  }, [deviceEvent]);

  // 每帧更新导风板摆动和主体发光
  useFrame((state) => {
    const a = attrs.current;
    const acOn = isOnValue(a.on_off);

    // 主体脉冲缩放
    if (bodyRef.current) {
      const next = THREE.MathUtils.lerp(bodyRef.current.scale.x, targetScale.current, 0.15);
      bodyRef.current.scale.setScalar(next);

      // 主体发光颜色（制冷淡蓝 / 制热淡红，cct>5000 为制热）
      const mat = bodyRef.current.material as THREE.MeshStandardMaterial;
      if (acOn) {
        if ((a.cct ?? 0) > 5000) {
          mat.emissive.setHex(0x331111); // 淡红色（制热）
        } else {
          mat.emissive.setHex(0x112244); // 淡蓝色（制冷）
        }
        mat.emissiveIntensity = 0.6;
      } else {
        mat.emissive.setHex(0x000000);
        mat.emissiveIntensity = 0;
      }
    }

    // 导风板上下摆动（设备开启时，sin 波形，幅度 = swing 度）
    if (flapRef.current && acOn) {
      const swingDeg = a.swing ?? 30;
      flapRef.current.rotation.x = Math.sin(state.clock.elapsedTime * 1.5) * (swingDeg * Math.PI / 180);
    }
  });

  return (
    <>
      <group position={device.position}>
        {/* 空调主体 */}
        <mesh ref={bodyRef}>
          <boxGeometry args={[1.8, 0.5, 0.25]} />
          <meshStandardMaterial color="#1a1a2e" emissive="#000000" emissiveIntensity={0} metalness={0.8} roughness={0.2} />
        </mesh>
        {/* 导风板 */}
        <mesh ref={flapRef} position={[0, -0.3, 0.12]}>
          <boxGeometry args={[1.6, 0.04, 0.08]} />
          <meshStandardMaterial color="#333344" metalness={0.6} roughness={0.4} />
        </mesh>
        {/* 冷气粒子 */}
        <AcParticles active={isOn} />
      </group>
      {/* 地面脉冲光环 */}
      <PulseRing trigger={deviceEvent} x={device.position[0]} z={device.position[2]} />
    </>
  );
}

// ==================== 窗帘设备 ====================

/** CurtainDevice Props */
interface CurtainDeviceProps {
  device: DeviceConfig;
  deviceEvent: DeviceControlEvent | null;
}

/**
 * 窗帘设备组件。
 * <p>窗框 + 两片滑动帘布，position=0 合拢在中央，position=100 展开到两侧。</p>
 * <p>动效：通过 lerp 平滑插值过渡。</p>
 */
function CurtainDevice({ device, deviceEvent }: CurtainDeviceProps) {
  // 左帘布引用
  const leftRef = useRef<THREE.Mesh>(null);
  // 右帘布引用
  const rightRef = useRef<THREE.Mesh>(null);
  // 目标位置（0=全关，100=全开）
  const targetPos = useRef(0);
  // 当前位置（平滑插值后的值）
  const currentPos = useRef(0);
  // 脉冲缩放
  const targetScale = usePulseScale(deviceEvent, 1.3);
  // 窗帘总宽度
  const curtainWidth = 2.4;

  // 事件到达时更新目标位置
  useEffect(() => {
    if (deviceEvent?.attributes.position !== undefined) {
      targetPos.current = deviceEvent.attributes.position;
    }
  }, [deviceEvent]);

  // 每帧平滑插值帘布位置
  useFrame(() => {
    // 平滑插值当前位置到目标位置
    currentPos.current = THREE.MathUtils.lerp(currentPos.current, targetPos.current, 0.08);
    // 根据位置计算两片帘布的偏移量（0=合拢，最大=展开到两侧）
    const offset = (currentPos.current / 100) * (curtainWidth / 2);
    if (leftRef.current) {
      // 左帘布向左滑
      leftRef.current.position.x = -curtainWidth / 4 - offset / 2;
      // 脉冲缩放
      const next = THREE.MathUtils.lerp(leftRef.current.scale.x, targetScale.current, 0.15);
      leftRef.current.scale.setScalar(next);
    }
    if (rightRef.current) {
      // 右帘布向右滑
      rightRef.current.position.x = curtainWidth / 4 + offset / 2;
      const next = THREE.MathUtils.lerp(rightRef.current.scale.x, targetScale.current, 0.15);
      rightRef.current.scale.setScalar(next);
    }
  });

  return (
    <>
      <group position={device.position}>
        {/* 窗框 */}
        <mesh>
          <boxGeometry args={[curtainWidth + 0.2, 2.2, 0.08]} />
          <meshStandardMaterial color="#1a1a2e" metalness={0.8} roughness={0.2} transparent opacity={0.4} />
        </mesh>
        {/* 左帘布 */}
        <mesh ref={leftRef} position={[-curtainWidth / 4, 0, 0.06]}>
          <planeGeometry args={[curtainWidth / 2, 2]} />
          <meshStandardMaterial color="#2a2a4e" transparent opacity={0.75} side={THREE.DoubleSide} metalness={0.3} roughness={0.7} />
        </mesh>
        {/* 右帘布 */}
        <mesh ref={rightRef} position={[curtainWidth / 4, 0, 0.06]}>
          <planeGeometry args={[curtainWidth / 2, 2]} />
          <meshStandardMaterial color="#2a2a4e" transparent opacity={0.75} side={THREE.DoubleSide} metalness={0.3} roughness={0.7} />
        </mesh>
      </group>
      {/* 地面脉冲光环 */}
      <PulseRing trigger={deviceEvent} x={device.position[0]} z={device.position[2]} />
    </>
  );
}

// ==================== 开关设备 ====================

/** SwitchDevice Props */
interface SwitchDeviceProps {
  device: DeviceConfig;
  deviceEvent: DeviceControlEvent | null;
}

/**
 * 开关设备组件。
 * <p>墙面面板 + 指示灯小球，支持开关/闪烁控制。</p>
 * <p>动效：on_off=1 时指示灯亮（绿色），被控制时面板闪烁一次。</p>
 */
function SwitchDevice({ device, deviceEvent }: SwitchDeviceProps) {
  // 面板引用
  const panelRef = useRef<THREE.Mesh>(null);
  // 指示灯引用
  const ledRef = useRef<THREE.Mesh>(null);
  // 当前设备属性
  const attrs = useRef<DeviceAttributes>({});
  // 闪烁强度（0-1，被控制时设为 1，逐帧淡出）
  const flashRef = useRef(0);

  // 事件到达时更新属性并触发闪烁
  useEffect(() => {
    if (deviceEvent) {
      attrs.current = { ...attrs.current, ...deviceEvent.attributes };
      // 被控制时触发面板闪烁
      flashRef.current = 1;
    }
  }, [deviceEvent]);

  // 每帧更新指示灯和面板闪烁
  useFrame(() => {
    const a = attrs.current;
    const isOn = isOnValue(a.on_off);

    // 指示灯颜色（on_off=1 时绿色发光）
    if (ledRef.current) {
      const mat = ledRef.current.material as THREE.MeshStandardMaterial;
      if (isOn) {
        mat.emissive.setHex(0x00ff44); // 绿色发光
        mat.emissiveIntensity = 1;
      } else {
        mat.emissive.setHex(0x000000);
        mat.emissiveIntensity = 0;
      }
    }

    // 面板闪烁动画（被控制时闪烁一次后淡出）
    if (panelRef.current) {
      const mat = panelRef.current.material as THREE.MeshStandardMaterial;
      if (flashRef.current > 0) {
        mat.emissive.setHex(0x665577);
        mat.emissiveIntensity = flashRef.current;
        // 逐帧淡出
        flashRef.current = Math.max(0, flashRef.current - 0.04);
      } else {
        mat.emissive.setHex(0x000000);
        mat.emissiveIntensity = 0;
      }
    }
  });

  return (
    <>
      <group position={device.position}>
        {/* 开关面板 */}
        <mesh ref={panelRef}>
          <boxGeometry args={[0.08, 0.3, 0.3]} />
          <meshStandardMaterial color="#1a1a2e" emissive="#000000" emissiveIntensity={0} metalness={0.6} roughness={0.4} />
        </mesh>
        {/* 指示灯小球 */}
        <mesh ref={ledRef} position={[0.06, 0.08, 0]}>
          <sphereGeometry args={[0.03, 12, 12]} />
          <meshStandardMaterial color="#222234" emissive="#000000" emissiveIntensity={0} />
        </mesh>
      </group>
      {/* 地面脉冲光环 */}
      <PulseRing trigger={deviceEvent} x={device.position[0]} z={device.position[2]} />
    </>
  );
}

// ==================== 房间结构 ====================

/** Wall Props */
interface WallProps {
  /** 墙壁中心位置 [x, y, z] */
  position: [number, number, number];
  /** 墙壁尺寸 [宽, 高, 厚] */
  size: [number, number, number];
}

/**
 * 单面墙壁组件（深色半透明 + 紫色霓虹边缘）。
 * <p>使用 EdgesGeometry + LineBasicMaterial 绘制发光边缘。</p>
 * <p>墙壁带有真实厚度感（通过 size.z 控制厚度，外围墙 0.2，内墙 0.15）。</p>
 */
function Wall({ position, size }: WallProps) {
  // 预计算盒体几何体（用于 EdgesGeometry，避免每帧重建）
  const boxGeom = useMemo(() => new THREE.BoxGeometry(size[0], size[1], size[2]), [size]);

  return (
    <group position={position}>
      {/* 墙体（深色半透明材质） */}
      <mesh>
        <boxGeometry args={size} />
        <meshStandardMaterial color={DARK_BG} transparent opacity={0.25} metalness={0.6} roughness={0.3} />
      </mesh>
      {/* 霓虹边缘（紫色发光线框） */}
      <lineSegments>
        <edgesGeometry args={[boxGeom]} />
        <lineBasicMaterial color={NEON_PURPLE} />
      </lineSegments>
    </group>
  );
}

// ==================== 家具组件（增强版） ====================

/**
 * 家具组件（增强版几何体组合，提供空间真实感）。
 * <p>每个家具由多个 mesh 组合而成，使用深色金属材质响应光照变化。</p>
 * <p>家具材质调整 metalness/roughness 让灯光颜色变化更明显：
 * 金属表面会反射灯光颜色，木质表面会吸收灯光显暖色。</p>
 */

/** 客厅沙发（L 形带靠垫） */
function Sofa({ position }: { position: [number, number, number] }) {
  // 沙发主体材质（深色布艺，低金属度，高粗糙度）
  const sofaMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#2a2a3e', roughness: 0.75, metalness: 0.15 }), []);
  // 靠垫材质（稍亮色，柔软质感）
  const cushionMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#3a3a5e', roughness: 0.9, metalness: 0.05 }), []);

  return (
    <group position={position}>
      {/* 主沙发底座（横向部分） */}
      <mesh position={[0, 0.3, 0]} castShadow receiveShadow material={sofaMat}>
        <boxGeometry args={[2.5, 0.5, 1.0]} />
      </mesh>
      {/* 主沙发靠背 */}
      <mesh position={[0, 0.85, -0.4]} castShadow material={sofaMat}>
        <boxGeometry args={[2.5, 0.7, 0.2]} />
      </mesh>
      {/* L 形侧座（右侧延伸，构成 L 形） */}
      <mesh position={[1.5, 0.3, 0.5]} castShadow receiveShadow material={sofaMat}>
        <boxGeometry args={[1.0, 0.5, 1.5]} />
      </mesh>
      {/* L 形侧靠背 */}
      <mesh position={[1.85, 0.85, 0.5]} castShadow material={sofaMat}>
        <boxGeometry args={[0.2, 0.7, 1.5]} />
      </mesh>
      {/* 靠垫 1（左侧） */}
      <mesh position={[-0.6, 0.6, 0]} castShadow material={cushionMat}>
        <boxGeometry args={[0.5, 0.2, 0.5]} />
      </mesh>
      {/* 靠垫 2（中间） */}
      <mesh position={[0.1, 0.6, 0]} castShadow material={cushionMat}>
        <boxGeometry args={[0.5, 0.2, 0.5]} />
      </mesh>
      {/* 靠垫 3（L 形转角） */}
      <mesh position={[0.9, 0.6, 0.3]} castShadow material={cushionMat}>
        <boxGeometry args={[0.5, 0.2, 0.5]} />
      </mesh>
      {/* 左扶手 */}
      <mesh position={[-1.15, 0.6, 0]} castShadow material={sofaMat}>
        <boxGeometry args={[0.2, 0.7, 1.0]} />
      </mesh>
    </group>
  );
}

/** 客厅茶几（带下层置物板） */
function CoffeeTable({ position }: { position: [number, number, number] }) {
  // 桌面材质（深色木纹，中金属度）
  const topMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#1a1a2e', roughness: 0.3, metalness: 0.6 }), []);
  // 桌腿材质（金属质感）
  const legMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#1a1a2e', roughness: 0.3, metalness: 0.6 }), []);

  return (
    <group position={position}>
      {/* 上层桌面 */}
      <mesh position={[0, 0.45, 0]} castShadow receiveShadow material={topMat}>
        <boxGeometry args={[1.2, 0.06, 0.6]} />
      </mesh>
      {/* 下层置物板 */}
      <mesh position={[0, 0.15, 0]} castShadow receiveShadow material={topMat}>
        <boxGeometry args={[1.1, 0.04, 0.55]} />
      </mesh>
      {/* 4 根桌腿 */}
      <mesh position={[-0.55, 0.22, -0.27]} castShadow material={legMat}>
        <cylinderGeometry args={[0.04, 0.04, 0.45, 8]} />
      </mesh>
      <mesh position={[0.55, 0.22, -0.27]} castShadow material={legMat}>
        <cylinderGeometry args={[0.04, 0.04, 0.45, 8]} />
      </mesh>
      <mesh position={[-0.55, 0.22, 0.27]} castShadow material={legMat}>
        <cylinderGeometry args={[0.04, 0.04, 0.45, 8]} />
      </mesh>
      <mesh position={[0.55, 0.22, 0.27]} castShadow material={legMat}>
        <cylinderGeometry args={[0.04, 0.04, 0.45, 8]} />
      </mesh>
    </group>
  );
}

/** 客厅电视柜 + 电视 */
function TvStand({ position }: { position: [number, number, number] }) {
  return (
    <group position={position}>
      {/* 电视柜主体 */}
      <mesh position={[0, 0.4, 0]} castShadow receiveShadow>
        <boxGeometry args={[2.0, 0.8, 0.4]} />
        <meshStandardMaterial color="#1a1a2e" roughness={0.5} metalness={0.5} />
      </mesh>
      {/* 电视柜抽屉分隔线 */}
      <mesh position={[0, 0.4, 0.21]}>
        <boxGeometry args={[0.02, 0.78, 0.01]} />
        <meshStandardMaterial color="#0a0a1e" />
      </mesh>
      {/* 电视底座 */}
      <mesh position={[0, 0.85, 0.1]} castShadow>
        <boxGeometry args={[0.4, 0.05, 0.2]} />
        <meshStandardMaterial color="#1a1a2e" metalness={0.8} roughness={0.3} />
      </mesh>
      {/* 电视边框（深色金属） */}
      <mesh position={[0, 1.8, 0]} castShadow>
        <boxGeometry args={[1.7, 1.0, 0.06]} />
        <meshStandardMaterial color="#0a0a1e" metalness={0.9} roughness={0.15} />
      </mesh>
      {/* 电视屏幕（自发光，模拟开机状态） */}
      <mesh position={[0, 1.8, 0.035]}>
        <boxGeometry args={[1.6, 0.9, 0.02]} />
        <meshStandardMaterial
          color="#000510"
          emissive="#0a1a2a"
          emissiveIntensity={0.4}
          roughness={0.2}
          metalness={0.7}
        />
      </mesh>
    </group>
  );
}

/** 客厅地毯（薄平面，铺在茶几下方） */
function Carpet({ position }: { position: [number, number, number] }) {
  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[position[0], 0.02, position[2]]} receiveShadow>
      <planeGeometry args={[3.2, 2.2]} />
      <meshStandardMaterial color="#2a1a3a" roughness={0.95} metalness={0.05} transparent opacity={0.75} />
    </mesh>
  );
}

/** 卧室双人床（带床头板+被子+枕头） */
function Bed({ position }: { position: [number, number, number] }) {
  // 床架材质（深色木质）
  const frameMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#1a1a2e', roughness: 0.5, metalness: 0.3 }), []);
  // 床垫材质（米色，柔软质感）
  const mattressMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#4a4a6e', roughness: 0.85, metalness: 0.05 }), []);
  // 被子材质（稍深色）
  const quiltMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#3a3a5e', roughness: 0.9, metalness: 0.05 }), []);
  // 枕头材质（亮色，柔软）
  const pillowMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#5a5a7e', roughness: 0.95, metalness: 0.05 }), []);

  return (
    <group position={position}>
      {/* 床架 */}
      <mesh position={[0, 0.25, 0]} castShadow receiveShadow material={frameMat}>
        <boxGeometry args={[2.0, 0.3, 1.8]} />
      </mesh>
      {/* 床垫 */}
      <mesh position={[0, 0.5, 0]} castShadow material={mattressMat}>
        <boxGeometry args={[1.9, 0.2, 1.7]} />
      </mesh>
      {/* 被子（覆盖床尾 2/3 区域） */}
      <mesh position={[0, 0.58, 0.3]} castShadow material={quiltMat}>
        <boxGeometry args={[1.85, 0.1, 1.0]} />
      </mesh>
      {/* 左枕头 */}
      <mesh position={[-0.5, 0.65, -0.6]} castShadow material={pillowMat}>
        <boxGeometry args={[0.6, 0.15, 0.35]} />
      </mesh>
      {/* 右枕头 */}
      <mesh position={[0.5, 0.65, -0.6]} castShadow material={pillowMat}>
        <boxGeometry args={[0.6, 0.15, 0.35]} />
      </mesh>
      {/* 床头板（高一些） */}
      <mesh position={[0, 0.7, -0.9]} castShadow material={frameMat}>
        <boxGeometry args={[2.0, 0.9, 0.1]} />
      </mesh>
    </group>
  );
}

/** 卧室床头柜 + 台灯 */
function NightStand({ position }: { position: [number, number, number] }) {
  return (
    <group position={position}>
      {/* 床头柜台面 */}
      <mesh position={[0, 0.5, 0]} castShadow receiveShadow>
        <boxGeometry args={[0.5, 0.05, 0.5]} />
        <meshStandardMaterial color="#1a1a2e" roughness={0.5} metalness={0.5} />
      </mesh>
      {/* 床头柜柜体 */}
      <mesh position={[0, 0.25, 0]} castShadow>
        <boxGeometry args={[0.45, 0.45, 0.45]} />
        <meshStandardMaterial color="#1a1a2e" roughness={0.5} metalness={0.5} />
      </mesh>
      {/* 抽屉把手 */}
      <mesh position={[0, 0.3, 0.23]}>
        <boxGeometry args={[0.1, 0.02, 0.02]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.15} />
      </mesh>
      {/* 台灯灯杆 */}
      <mesh position={[0, 0.75, 0]}>
        <cylinderGeometry args={[0.02, 0.02, 0.4, 8]} />
        <meshStandardMaterial color="#333344" metalness={0.8} roughness={0.2} />
      </mesh>
      {/* 台灯灯罩（圆锥形，半透明发光） */}
      <mesh position={[0, 1.0, 0]}>
        <coneGeometry args={[0.18, 0.22, 16, 1, true]} />
        <meshStandardMaterial
          color="#faad14"
          emissive="#faad14"
          emissiveIntensity={0.5}
          side={THREE.DoubleSide}
          transparent
          opacity={0.85}
        />
      </mesh>
      {/* 台灯点光源（局部暖光照明） */}
      <pointLight position={[0, 0.95, 0]} intensity={0.8} distance={4} color="#faad14" decay={1.5} />
    </group>
  );
}

/** 卧室衣柜 */
function Wardrobe({ position }: { position: [number, number, number] }) {
  return (
    <group position={position}>
      {/* 衣柜主体 */}
      <mesh position={[0, 1.0, 0]} castShadow receiveShadow>
        <boxGeometry args={[1.2, 2.0, 0.6]} />
        <meshStandardMaterial color="#1a1a2e" roughness={0.5} metalness={0.4} />
      </mesh>
      {/* 左门把手 */}
      <mesh position={[-0.3, 1.0, 0.32]}>
        <cylinderGeometry args={[0.02, 0.02, 0.18, 8]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.2} />
      </mesh>
      {/* 右门把手 */}
      <mesh position={[0.3, 1.0, 0.32]}>
        <cylinderGeometry args={[0.02, 0.02, 0.18, 8]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.2} />
      </mesh>
      {/* 门缝（中间分隔线） */}
      <mesh position={[0, 1.0, 0.31]}>
        <boxGeometry args={[0.02, 1.95, 0.01]} />
        <meshStandardMaterial color="#0a0a1e" />
      </mesh>
    </group>
  );
}

/** 厨房灶台（带水槽+水龙头） */
function KitchenCounter({ position }: { position: [number, number, number] }) {
  // 台面材质（深色石材，高金属度反光）
  const topMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#252535', roughness: 0.2, metalness: 0.8 }), []);
  // 柜体材质
  const bodyMat = useMemo(() => new THREE.MeshStandardMaterial({ color: '#1a1a2e', roughness: 0.4, metalness: 0.4 }), []);

  return (
    <group position={position}>
      {/* 台面 */}
      <mesh position={[0, 0.9, 0]} castShadow receiveShadow material={topMat}>
        <boxGeometry args={[3.0, 0.08, 0.7]} />
      </mesh>
      {/* 柜体 */}
      <mesh position={[0, 0.45, 0]} castShadow material={bodyMat}>
        <boxGeometry args={[3.0, 0.9, 0.7]} />
      </mesh>
      {/* 灶台圆环 1 */}
      <mesh position={[-0.7, 0.96, 0]} castShadow>
        <cylinderGeometry args={[0.2, 0.2, 0.04, 16]} />
        <meshStandardMaterial color="#0a0a0a" roughness={0.3} metalness={0.7} />
      </mesh>
      {/* 灶台圆环 2 */}
      <mesh position={[0.7, 0.96, 0]} castShadow>
        <cylinderGeometry args={[0.2, 0.2, 0.04, 16]} />
        <meshStandardMaterial color="#0a0a0a" roughness={0.3} metalness={0.7} />
      </mesh>
      {/* 水槽（凹陷矩形） */}
      <mesh position={[0, 0.94, 0]} castShadow>
        <boxGeometry args={[0.6, 0.04, 0.4]} />
        <meshStandardMaterial color="#15151a" roughness={0.1} metalness={0.9} />
      </mesh>
      {/* 水龙头立柱 */}
      <mesh position={[0, 1.15, -0.25]}>
        <cylinderGeometry args={[0.025, 0.025, 0.4, 8]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.1} />
      </mesh>
      {/* 水龙头出水口 */}
      <mesh position={[0, 1.25, -0.1]} rotation={[Math.PI / 2.5, 0, 0]}>
        <cylinderGeometry args={[0.025, 0.025, 0.25, 8]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.1} />
      </mesh>
    </group>
  );
}

/** 厨房冰箱 */
function Refrigerator({ position }: { position: [number, number, number] }) {
  return (
    <group position={position}>
      {/* 主体 */}
      <mesh position={[0, 1.0, 0]} castShadow receiveShadow>
        <boxGeometry args={[0.8, 2.0, 0.7]} />
        <meshStandardMaterial color="#2a2a3e" roughness={0.3} metalness={0.7} />
      </mesh>
      {/* 冷藏室门把手（上方） */}
      <mesh position={[0.35, 1.3, 0]}>
        <boxGeometry args={[0.04, 0.3, 0.04]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.1} />
      </mesh>
      {/* 冷冻室门把手（下方） */}
      <mesh position={[0.35, 0.4, 0]}>
        <boxGeometry args={[0.04, 0.2, 0.04]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.1} />
      </mesh>
      {/* 冷冻/冷藏分隔线 */}
      <mesh position={[0, 0.8, 0.36]}>
        <boxGeometry args={[0.8, 0.02, 0.01]} />
        <meshStandardMaterial color="#0a0a1e" />
      </mesh>
    </group>
  );
}

/** 厨房悬挂橱柜（墙上） */
function Cabinet({ position }: { position: [number, number, number] }) {
  return (
    <group position={position}>
      {/* 悬挂橱柜主体 */}
      <mesh position={[0, 2.5, -0.3]} castShadow>
        <boxGeometry args={[2.0, 0.8, 0.4]} />
        <meshStandardMaterial color="#1a1a2e" roughness={0.5} metalness={0.4} />
      </mesh>
      {/* 门缝 */}
      <mesh position={[0, 2.5, -0.1]}>
        <boxGeometry args={[0.02, 0.78, 0.01]} />
        <meshStandardMaterial color="#0a0a1e" />
      </mesh>
      {/* 左把手 */}
      <mesh position={[-0.4, 2.5, -0.1]}>
        <boxGeometry args={[0.04, 0.04, 0.04]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.1} />
      </mesh>
      {/* 右把手 */}
      <mesh position={[0.4, 2.5, -0.1]}>
        <boxGeometry args={[0.04, 0.04, 0.04]} />
        <meshStandardMaterial color="#888899" metalness={0.9} roughness={0.1} />
      </mesh>
    </group>
  );
}

/**
 * 房间结构组件（增强版）。
 * <p>包含：三色纹理地面 + 半透明天花板 + 带门洞的墙壁 + 丰富家具 + 房间标签。</p>
 *
 * <p>户型布局（20x12）：</p>
 * <ul>
 *   <li>客厅：x:-10~2, z:-6~6（暖灰色木地板）</li>
 *   <li>卧室：x:2~10, z:-6~0（浅木色木地板）</li>
 *   <li>厨房：x:2~10, z:0~6（浅蓝灰瓷砖）</li>
 * </ul>
 *
 * <p>门洞位置：</p>
 * <ul>
 *   <li>客厅↔卧室：x=2, z=-3 处（宽 1.5m，高 2.5m）</li>
 *   <li>客厅↔厨房：x=2, z=3 处（宽 1.5m，高 2.5m）</li>
 *   <li>卧室↔厨房：z=0, x=6 处（宽 1.5m，高 2.5m）</li>
 * </ul>
 *
 * <p>地面材质 metalness=0.4, roughness=0.35，能清晰反射设备灯光颜色，
 * 让 RGB 灯带亮红时整个房间地面变红，调光灯亮时地面变暖金色。</p>
 */
function RoomStructure() {
  // 半宽和半深（用于墙壁定位）
  const halfW = FLOOR_WIDTH / 2;  // 10
  const halfD = FLOOR_DEPTH / 2;  // 6

  // 程序生成三个房间的地面纹理（仅计算一次）
  const livingFloorTex = useMemo(() => makeFloorTexture(LIVING_FLOOR_COLOR, 'wood'), []);
  const bedroomFloorTex = useMemo(() => makeFloorTexture(BEDROOM_FLOOR_COLOR, 'wood'), []);
  const kitchenFloorTex = useMemo(() => makeFloorTexture(KITCHEN_FLOOR_COLOR, 'tile'), []);

  return (
    <>
      {/* === 三色纹理地面（拆分三个房间，每房间独立材质）=== */}
      {/* 客厅地面 x:-10~2 (宽12), z:-6~6 (深12)，中心 (-4, 0, 0) */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[-4, 0, 0]} receiveShadow>
        <planeGeometry args={[12, 12]} />
        <meshStandardMaterial
          map={livingFloorTex}
          color="#ffffff"
          roughness={0.35}
          metalness={0.4}
        />
      </mesh>
      {/* 卧室地面 x:2~10 (宽8), z:-6~0 (深6)，中心 (6, 0, -3) */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[6, 0, -3]} receiveShadow>
        <planeGeometry args={[8, 6]} />
        <meshStandardMaterial
          map={bedroomFloorTex}
          color="#ffffff"
          roughness={0.35}
          metalness={0.4}
        />
      </mesh>
      {/* 厨房地面 x:2~10 (宽8), z:0~6 (深6)，中心 (6, 0, 3) */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[6, 0, 3]} receiveShadow>
        <planeGeometry args={[8, 6]} />
        <meshStandardMaterial
          map={kitchenFloorTex}
          color="#ffffff"
          roughness={0.3}
          metalness={0.5}
        />
      </mesh>

      {/* 科幻网格线（紫色主线 + 暗色副线，铺满整个户型） */}
      <gridHelper args={[FLOOR_WIDTH, 20, NEON_PURPLE, '#222244']} position={[0, 0.01, 0]} />

      {/* === 半透明天花板（让相机从上方能看到内部）=== */}
      <mesh rotation={[Math.PI / 2, 0, 0]} position={[0, WALL_HEIGHT, 0]}>
        <planeGeometry args={[FLOOR_WIDTH, FLOOR_DEPTH]} />
        <meshStandardMaterial
          color={DARK_BG}
          transparent
          opacity={0.15}
          side={THREE.DoubleSide}
          metalness={0.6}
          roughness={0.3}
        />
      </mesh>

      {/* === 外围墙壁（带厚度，深色半透明 + 紫色霓虹边缘）=== */}
      {/* 西墙（左） */}
      <Wall position={[-halfW, WALL_HEIGHT / 2, 0]} size={[0.2, WALL_HEIGHT, FLOOR_DEPTH]} />
      {/* 东墙（右） */}
      <Wall position={[halfW, WALL_HEIGHT / 2, 0]} size={[0.2, WALL_HEIGHT, FLOOR_DEPTH]} />
      {/* 北墙（后） */}
      <Wall position={[0, WALL_HEIGHT / 2, -halfD]} size={[FLOOR_WIDTH, WALL_HEIGHT, 0.2]} />
      {/* 南墙（前） */}
      <Wall position={[0, WALL_HEIGHT / 2, halfD]} size={[FLOOR_WIDTH, WALL_HEIGHT, 0.2]} />

      {/* === 内部隔墙（带门洞，分段实现）=== */}
      {/* 客厅↔卧室隔墙：x=2, z=-6~0，在 z=-3 处留门洞（宽 1.5） */}
      {/* 下段左：z=-6 ~ -3.75（长 2.25），完整高度 */}
      <Wall position={[2, WALL_HEIGHT / 2, -4.875]} size={[0.15, WALL_HEIGHT, 2.25]} />
      {/* 下段右：z=-2.25 ~ 0（长 2.25），完整高度 */}
      <Wall position={[2, WALL_HEIGHT / 2, -1.125]} size={[0.15, WALL_HEIGHT, 2.25]} />
      {/* 上段（门洞上方）：z=-3.75 ~ -2.25（长 1.5），y=2.5~4（高 1.5） */}
      <Wall position={[2, 3.25, -3]} size={[0.15, 1.5, 1.5]} />

      {/* 客厅↔厨房隔墙：x=2, z=0~6，在 z=3 处留门洞（宽 1.5） */}
      <Wall position={[2, WALL_HEIGHT / 2, 1.125]} size={[0.15, WALL_HEIGHT, 2.25]} />
      <Wall position={[2, WALL_HEIGHT / 2, 4.875]} size={[0.15, WALL_HEIGHT, 2.25]} />
      <Wall position={[2, 3.25, 3]} size={[0.15, 1.5, 1.5]} />

      {/* 卧室↔厨房隔墙：z=0, x=2~10，在 x=6 处留门洞（宽 1.5） */}
      <Wall position={[4.125, WALL_HEIGHT / 2, 0]} size={[2.25, WALL_HEIGHT, 0.15]} />
      <Wall position={[7.875, WALL_HEIGHT / 2, 0]} size={[2.25, WALL_HEIGHT, 0.15]} />
      <Wall position={[6, 3.25, 0]} size={[1.5, 1.5, 0.15]} />

      {/* === 客厅家具 === */}
      <Sofa position={[-7, 0, -3]} />
      <CoffeeTable position={[-5, 0, -2]} />
      <TvStand position={[-9.5, 0, -3]} />
      <Carpet position={[-6, 0, -2]} />

      {/* === 卧室家具 === */}
      <Bed position={[6, 0, -2]} />
      <NightStand position={[3.7, 0, -4.5]} />
      <Wardrobe position={[9.5, 0, -2]} />

      {/* === 厨房家具 === */}
      <KitchenCounter position={[9, 0, 3]} />
      <Refrigerator position={[3, 0, 1]} />
      <Cabinet position={[9, 0, 5]} />

      {/* === 房间标签（使用 Html 替代 Text，避免加载字体节省内存） === */}
      <Html position={[-4, WALL_HEIGHT + 0.5, 0]} center distanceFactor={12} style={{ pointerEvents: 'none' }}>
        <div style={{
          fontSize: '14px',
          color: NEON_CYAN,
          fontWeight: 'bold',
          textShadow: `0 0 4px ${NEON_PURPLE}, 0 0 8px ${NEON_PURPLE}`,
          whiteSpace: 'nowrap',
        }}>
          客厅
        </div>
      </Html>
      <Html position={[6, WALL_HEIGHT + 0.5, -3]} center distanceFactor={12} style={{ pointerEvents: 'none' }}>
        <div style={{
          fontSize: '12px',
          color: NEON_CYAN,
          fontWeight: 'bold',
          textShadow: `0 0 4px ${NEON_PURPLE}, 0 0 8px ${NEON_PURPLE}`,
          whiteSpace: 'nowrap',
        }}>
          卧室
        </div>
      </Html>
      <Html position={[6, WALL_HEIGHT + 0.5, 3]} center distanceFactor={12} style={{ pointerEvents: 'none' }}>
        <div style={{
          fontSize: '12px',
          color: NEON_CYAN,
          fontWeight: 'bold',
          textShadow: `0 0 4px ${NEON_PURPLE}, 0 0 8px ${NEON_PURPLE}`,
          whiteSpace: 'nowrap',
        }}>
          厨房
        </div>
      </Html>
    </>
  );
}

// ==================== 设备渲染分发 ====================

/** DeviceRenderer Props */
interface DeviceRendererProps {
  device: DeviceConfig;
  deviceEvent: DeviceControlEvent | null;
}

/**
 * 设备渲染分发组件（根据 type 渲染对应设备组件 + 名称标签）。
 */
function DeviceRenderer({ device, deviceEvent }: DeviceRendererProps) {
  // 根据设备类型渲染对应组件
  let model: ReactNode = null;
  switch (device.type) {
    case 'rgb_strip':
      model = <RgbStripDevice device={device} deviceEvent={deviceEvent} />;
      break;
    case 'dimming_light':
      // 调光灯：半球吸顶灯 + 8 道光线 + 亮度控制
      model = <DimmingLightDevice device={device} deviceEvent={deviceEvent} />;
      break;
    case 'cct_light':
      // 色温灯：灯泡造型 + 色温颜色 + 色温渐变条
      model = <CctLightDevice device={device} deviceEvent={deviceEvent} />;
      break;
    case 'fan':
      model = <FanDevice device={device} deviceEvent={deviceEvent} />;
      break;
    case 'ac':
      model = <AcDevice device={device} deviceEvent={deviceEvent} />;
      break;
    case 'curtain':
      model = <CurtainDevice device={device} deviceEvent={deviceEvent} />;
      break;
    case 'switch':
      model = <SwitchDevice device={device} deviceEvent={deviceEvent} />;
      break;
  }
  return (
    <Fragment>
      {model}
      {/* 设备名称浮标（被控制时显示） */}
      <DeviceLabel device={device} deviceEvent={deviceEvent} />
    </Fragment>
  );
}

// ==================== 设备提取展示组件 ====================

/** DeviceShowcase Props */
interface DeviceShowcaseProps {
  /** 被提取展示的设备配置 */
  device: DeviceConfig;
  /** 提取时刻的设备事件（用于显示属性信息） */
  showcaseEvent: DeviceControlEvent;
}

/**
 * 信息卡片样式（霓虹科幻风格）。
 */
const INFO_CARD_STYLE: React.CSSProperties = {
  background: 'rgba(15, 12, 30, 0.92)',
  border: `1px solid ${NEON_PURPLE}`,
  borderRadius: '6px',
  padding: '8px 14px',
  fontFamily: 'monospace',
  whiteSpace: 'nowrap',
  pointerEvents: 'none',
  boxShadow: `0 0 24px ${NEON_PURPLE}88, inset 0 0 12px ${NEON_PURPLE}33`,
};

/** 信息卡片标题样式 */
const INFO_TITLE_STYLE: React.CSSProperties = {
  fontSize: '15px',
  color: NEON_CYAN,
  fontWeight: 'bold',
  marginBottom: '6px',
  borderBottom: `1px solid ${NEON_PURPLE}88`,
  paddingBottom: '4px',
  textShadow: `0 0 8px ${NEON_CYAN}aa`,
};

/** 信息卡片行样式 */
const INFO_LINE_STYLE: React.CSSProperties = {
  fontSize: '12px',
  color: '#e0e0e0',
  marginTop: '3px',
  lineHeight: '1.5',
};

/**
 * 生成设备信息卡片的文本行列表。
 * @param device 设备配置
 * @param attrs 设备属性
 * @returns 信息行数组（首行为设备名称）
 */
function generateInfoLines(device: DeviceConfig, attrs: DeviceAttributes): string[] {
  const lines: string[] = [device.name];
  const isOn = isOnValue(attrs.on_off);
  lines.push(`状态: ${isOn ? '开启' : '关闭'}`);
  if (attrs.brightness !== undefined) lines.push(`亮度: ${attrs.brightness}%`);
  if (attrs.rgb !== undefined) lines.push(`颜色: ${rgbToHex(attrs.rgb)}`);
  if (attrs.cct !== undefined) lines.push(`色温: ${attrs.cct}K`);
  if (attrs.speed !== undefined) lines.push(`速度: ${attrs.speed} 档`);
  if (attrs.swing !== undefined) lines.push(`摆头: ${attrs.swing}°`);
  if (attrs.position !== undefined) lines.push(`位置: ${attrs.position}%`);
  return lines;
}

/**
 * 设备提取展示组件（无形的手拎出效果）。
 * <p>当设备被控制后 1 秒触发：设备被"无形的手"从户型图中拎出，沿弧线轨迹
 * 移动到户型图左侧外部空间，不放大，单独展示设备信息。</p>
 *
 * <p>动画效果：</p>
 * <ul>
 *   <li>弧线轨迹（二次贝塞尔曲线）：从设备原位置 → 向上弧形 → 落到左侧外部</li>
 *   <li>不缩放（保持原始大小），仅位置变化</li>
 *   <li>设备被拎出后，旁边显示信息卡片</li>
 *   <li>底部有光柱连接户型图，表示"被拎出"的来源</li>
 * </ul>
 */
function DeviceShowcase({ device, showcaseEvent }: DeviceShowcaseProps) {
  // 整体 group 引用（用于位置动画）
  const groupRef = useRef<THREE.Group>(null);
  // 动画进度（0 → 1）
  const progress = useRef(0);

  // 起始位置（设备原位置）
  const startPos = useMemo(
    () => new THREE.Vector3(device.position[0], device.position[1], device.position[2]),
    [device.position]
  );
  // 结束位置：户型图左侧外部（x=-10 在户型图墙外，y=2 中等高度，z=0）
  const endPos = useMemo(() => new THREE.Vector3(-10, 2, 0), []);
  // 弧线控制点：起点和终点中间偏上方，形成"拎起"的弧形轨迹
  const controlPos = useMemo(() => {
    const mid = new THREE.Vector3().lerpVectors(startPos, endPos, 0.5);
    mid.y += 5; // 向上抬高 5 个单位，形成弧形
    return mid;
  }, [startPos, endPos]);

  // 创建展示用设备副本（position 归零，由 group 包装到展示位置）
  const showcaseDevice = useMemo<DeviceConfig>(
    () => ({ ...device, position: [0, 0, 0] }),
    [device]
  );

  /**
   * 二次贝塞尔曲线插值。
   * @param t 进度 0→1
   * @param p0 起点
   * @param p1 控制点
   * @param p2 终点
   * @returns 插值后的位置
   */
  const bezierPoint = useCallback((t: number, p0: THREE.Vector3, p1: THREE.Vector3, p2: THREE.Vector3) => {
    const u = 1 - t;
    return new THREE.Vector3(
      u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
      u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y,
      u * u * p0.z + 2 * u * t * p1.z + t * t * p2.z,
    );
  }, []);

  // 每帧更新入场动画（弧线轨迹，不缩放）
  useFrame(() => {
    if (!groupRef.current) return;
    if (progress.current < 1) {
      // 动画进度递增（约 2 秒完成，比之前更慢更有"拎起"感）
      progress.current = Math.min(1, progress.current + 0.01);
      // easeInOutQuad 缓动函数
      const t = progress.current;
      const eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
      // 沿贝塞尔弧线移动（不缩放）
      const pos = bezierPoint(eased, startPos, controlPos, endPos);
      groupRef.current.position.copy(pos);
    }
  });

  // 生成信息卡片内容
  const infoLines = useMemo(
    () => generateInfoLines(device, showcaseEvent.attributes),
    [device, showcaseEvent]
  );

  return (
    <group ref={groupRef} position={device.position}>
      {/* 设备 3D 模型副本（位置归零，由 group 包装） */}
      <DeviceRenderer device={showcaseDevice} deviceEvent={showcaseEvent} />

      {/* 信息卡片（Html 组件，悬浮在设备右侧，更醒目） */}
      <Html position={[2.5, 0.8, 0]} center distanceFactor={10} style={{ pointerEvents: 'none' }}>
        <div style={INFO_CARD_STYLE}>
          {infoLines.map((line, i) => (
            <div key={i} style={i === 0 ? INFO_TITLE_STYLE : INFO_LINE_STYLE}>
              {line}
            </div>
          ))}
        </div>
      </Html>

      {/* 底部光晕（表示设备被"拎出"后的悬浮基座） */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -1.2, 0]}>
        <ringGeometry args={[0.8, 1.2, 32]} />
        <meshBasicMaterial color={NEON_CYAN} transparent opacity={0.4} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}

// ==================== 场景组件 ====================

/** Scene Props */
interface SceneProps {
  deviceEvent: DeviceControlEvent | null;
}

/**
 * OrbitControls 实例最小类型约束。
 * <p>避免引入 three-stdlib 类型依赖，仅声明需要使用的方法。</p>
 */
interface OrbitControlsLike {
  /** 控制目标点（相机看向的位置） */
  target: THREE.Vector3;
  /** 更新控制器状态（每帧调用以同步） */
  update: () => void;
}

/**
 * 默认相机位置（俯视斜角，看向原点）。
 */
const DEFAULT_CAM_POS: [number, number, number] = [15, 12, 15];
/** 默认相机目标点（户型图中心） */
const DEFAULT_TARGET: [number, number, number] = [0, 0, 0];
/** 设备聚焦时相机距离设备的偏移量（斜上方俯视，距离约 6.4 单位） */
const FOCUS_CAM_OFFSET: [number, number, number] = [3, 4, 4];
/** 设备提取展示的延迟（毫秒） */
const SHOWCASE_DELAY_MS = 1000;
/** 无操作后回归的延迟（毫秒） */
const RESET_DELAY_MS = 10000;

/**
 * 3D 场景组件（增强版：光照 + 房间 + 设备 + 相机智能聚焦 + 设备提取展示 + 阴影）。
 *
 * <p>相机控制逻辑：</p>
 * <ol>
 *   <li>deviceEvent 到达时，立即设置 focusedDeviceId，相机平滑移动到设备附近</li>
 *   <li>1 秒后，设置 showcaseDevice，触发设备提取展示</li>
 *   <li>10 秒后无新操作，清除 focusedDeviceId 和 showcaseDevice，相机回归默认位置</li>
 * </ol>
 *
 * <p>相机平滑过渡使用 useFrame + Vector3.lerp 实现，每帧 lerp factor 0.05，
 * 约 1 秒内完成过渡。</p>
 */
function Scene({ deviceEvent }: SceneProps) {
  // 获取相机（用于位置控制）
  const { camera } = useThree();
  // OrbitControls 引用（用于控制 target 和同步状态）
  const controlsRef = useRef<OrbitControlsLike | null>(null);

  // 聚焦的设备 ID（用于相机移动）
  const [focusedDeviceId, setFocusedDeviceId] = useState<string | null>(null);
  // 提取展示的设备
  const [showcaseDevice, setShowcaseDevice] = useState<DeviceConfig | null>(null);
  // 提取展示的事件（包含提取时刻的属性）
  const [showcaseEvent, setShowcaseEvent] = useState<DeviceControlEvent | null>(null);

  // 相机目标位置和 lookAt 目标（useFrame 中 lerp 用）
  const targetCamPos = useRef(new THREE.Vector3(...DEFAULT_CAM_POS));
  const targetLookAt = useRef(new THREE.Vector3(...DEFAULT_TARGET));

  // 默认相机位置和目标（备忘，回归时使用）
  const defaultCamPos = useMemo(() => new THREE.Vector3(...DEFAULT_CAM_POS), []);
  const defaultTarget = useMemo(() => new THREE.Vector3(...DEFAULT_TARGET), []);

  // 匹配当前被控制的设备 ID（deviceEvent 变化时重新计算）
  const matchedDeviceId = useMemo(() => {
    if (!deviceEvent) return null;
    console.log('[HomePlan3D] Scene 收到 deviceEvent:', deviceEvent);
    // 遍历设备列表找到匹配的设备（spk 优先匹配，名称关键词作为回退）
    const matched = DEVICES.find(d => matchDevice(d, deviceEvent));
    console.log('[HomePlan3D] 匹配结果:', matched?.id ?? '未匹配');
    return matched?.id ?? null;
  }, [deviceEvent]);

  // deviceEvent 变化时：设置聚焦 → 延时提取展示 → 延时回归
  useEffect(() => {
    if (!matchedDeviceId) return;

    // 立即聚焦到被控制设备
    setFocusedDeviceId(matchedDeviceId);
    // 清除上一次的展示（避免重叠）
    setShowcaseDevice(null);
    setShowcaseEvent(null);

    console.log('[HomePlan3D] 聚焦设备:', matchedDeviceId);

    // 1 秒后提取设备到上方展示
    const showcaseTimer = setTimeout(() => {
      const dev = DEVICES.find(d => d.id === matchedDeviceId);
      if (dev && deviceEvent) {
        console.log('[HomePlan3D] 提取展示设备:', dev.name);
        setShowcaseDevice(dev);
        setShowcaseEvent(deviceEvent);
      }
    }, SHOWCASE_DELAY_MS);

    // 10 秒后无新操作，回归默认状态
    const resetTimer = setTimeout(() => {
      console.log('[HomePlan3D] 无操作超时，回归默认');
      setFocusedDeviceId(null);
      setShowcaseDevice(null);
      setShowcaseEvent(null);
    }, RESET_DELAY_MS);

    // 清理函数：deviceEvent 变化或组件卸载时清除定时器
    return () => {
      clearTimeout(showcaseTimer);
      clearTimeout(resetTimer);
    };
  }, [matchedDeviceId, deviceEvent]);

  // 每帧更新相机位置（lerp 平滑过渡）
  useFrame(() => {
    if (showcaseDevice) {
      // 展示模式：设备被拎到左侧外部 [-10, 2, 0]，相机看向展示位置
      // 相机偏移到展示设备的前方偏上，让展示设备在屏幕左侧可见
      targetCamPos.current.set(-5, 5, 10);
      targetLookAt.current.set(-10, 2, 0);
    } else if (focusedDeviceId) {
      // 聚焦模式：相机移动到设备附近，斜上方俯视
      const dev = DEVICES.find(d => d.id === focusedDeviceId);
      if (dev) {
        const [x, y, z] = dev.position;
        // 相机位置 = 设备位置 + 偏移（斜上方）
        targetCamPos.current.set(
          x + FOCUS_CAM_OFFSET[0],
          y + FOCUS_CAM_OFFSET[1],
          z + FOCUS_CAM_OFFSET[2]
        );
        // 目标点 = 设备位置
        targetLookAt.current.set(x, y, z);
      }
    } else {
      // 默认模式：相机回到默认俯视位置
      targetCamPos.current.copy(defaultCamPos);
      targetLookAt.current.copy(defaultTarget);
    }

    // 相机位置平滑过渡（lerp factor 0.05，约 1 秒完成过渡）
    camera.position.lerp(targetCamPos.current, 0.05);

    // OrbitControls 目标点同步（让 lookAt 也平滑过渡）
    if (controlsRef.current) {
      controlsRef.current.target.lerp(targetLookAt.current, 0.05);
      controlsRef.current.update();
    }
  });

  return (
    <>
      {/* 环境光（极低强度基础照明，让设备灯光效果更突出明显） */}
      <ambientLight intensity={0.1} />
      {/* 方向光（弱主光源，仅提供基础轮廓可见性） */}
      <directionalLight position={[10, 15, 8]} intensity={0.2} castShadow />
      {/* 半球光（天空-地面渐变，极弱） */}
      <hemisphereLight args={['#1a2a5a', '#0a0a1e', 0.12]} />

      {/* 房间结构 */}
      <RoomStructure />

      {/* 全部设备（仅匹配的设备接收 deviceEvent） */}
      {DEVICES.map(d => (
        <DeviceRenderer
          key={d.id}
          device={d}
          deviceEvent={matchedDeviceId === d.id ? deviceEvent : null}
        />
      ))}

      {/* 设备提取展示（deviceEvent 后 1 秒触发，10 秒后消失） */}
      {showcaseDevice && showcaseEvent && (
        <DeviceShowcase device={showcaseDevice} showcaseEvent={showcaseEvent} />
      )}

      {/* 地面接触阴影（降低分辨率到 128 节省内存） */}
      <ContactShadows
        position={[0, 0.02, 0]}
        opacity={0.4}
        scale={FLOOR_WIDTH}
        blur={2}
        far={WALL_HEIGHT}
        color={NEON_PURPLE}
        resolution={128}
      />

      {/* 轨道控制器（支持无死角旋转和缩放，不限制极角） */}
      <OrbitControls
        ref={controlsRef as never}
        target={[0, 0, 0]}
        minDistance={5}
        maxDistance={40}
        enablePan={true}
      />
    </>
  );
}

// ==================== ErrorBoundary ====================

/**
 * 局部 ErrorBoundary（3D 加载失败时显示降级 UI，不影响外层功能）。
 */
class HomePlan3DErrorBoundary extends Component<
  { children: ReactNode },
  { hasError: boolean; errorMsg: string }
> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false, errorMsg: '' };
  }

  // 捕获错误后更新状态
  static getDerivedStateFromError(error: Error) {
    return { hasError: true, errorMsg: error.message || '3D 户型图加载失败' };
  }

  // 错误日志
  componentDidCatch(error: Error) {
    console.error('[HomePlan3D] 错误被局部 ErrorBoundary 捕获：', error.message);
  }

  render() {
    // 出错时显示降级 UI
    if (this.state.hasError) {
      return (
        <div className="flex items-center justify-center w-full h-full">
          <div className="text-center">
            <div className="text-3xl mb-3 opacity-60">🏠</div>
            <p className="text-xs text-slate-400 mb-1">3D 户型图暂时不可用</p>
            <p className="text-[10px] text-slate-600 max-w-xs">其他功能正常使用</p>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

// ==================== 主组件 ====================

/**
 * 3D 户型图内部组件（不含 ErrorBoundary，包含 Canvas）。
 * @param props.deviceEvent 设备控制事件
 * @param props.size 尺寸（px）
 * @param props.onClose 关闭回调（X 按钮触发）
 */
function HomePlan3DInner({ deviceEvent, size, onClose }: HomePlan3DProps) {
  return (
    <div style={{ width: '100%', height: size ? `${size}px` : '100%', position: 'relative' }}>
      <Canvas
        camera={{ position: DEFAULT_CAM_POS, fov: 45, near: 0.1, far: 200 }}
        dpr={[1, 1.2]}
        gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
        style={{ background: 'transparent' }}
      >
        <Suspense fallback={null}>
          <Scene deviceEvent={deviceEvent ?? null} />
        </Suspense>
      </Canvas>

      {/* 右上角 X 退出按钮：点击关闭户型图，返回主 3D 模型 */}
      {onClose && (
        <button
          onClick={onClose}
          title="退出户型图"
          style={{
            position: 'absolute',
            top: '16px',
            right: '16px',
            zIndex: 200,
            width: '40px',
            height: '40px',
            borderRadius: '50%',
            border: `2px solid ${NEON_PURPLE}`,
            background: 'rgba(15, 12, 30, 0.85)',
            color: NEON_CYAN,
            fontSize: '20px',
            fontWeight: 'bold',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backdropFilter: 'blur(8px)',
            boxShadow: `0 0 16px ${NEON_PURPLE}88`,
            transition: 'all 0.2s',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(168, 85, 247, 0.3)';
            e.currentTarget.style.transform = 'scale(1.1)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'rgba(15, 12, 30, 0.85)';
            e.currentTarget.style.transform = 'scale(1)';
          }}
        >
          ✕
        </button>
      )}
    </div>
  );
}

/**
 * 3D 户型图主组件（包裹局部 ErrorBoundary）。
 *
 * <p>3D 加载失败时仅在此处显示降级提示，不向上抛出，
 * 确保外层功能不受影响。</p>
 *
 * @param props.deviceEvent 设备控制事件
 * @param props.size 自定义尺寸（px）
 */
export default function HomePlan3D(props: HomePlan3DProps) {
  return (
    <HomePlan3DErrorBoundary>
      <HomePlan3DInner {...props} />
    </HomePlan3DErrorBoundary>
  );
}
