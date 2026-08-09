/**
 * @file 设备工具函数
 *
 * 提供 HDL 设备相关的纯函数工具：
 *  - getSpkLabel: 根据 SPK 物模型编码返回中文类型标签
 *  - getStatusValue: 从 status 数组中按 key 取值
 *  - rgbToHex: RGB 字符串 "r,g,b" 转 hex "#rrggbb"
 *
 * 来源：从 DeviceListPage.tsx 和 DeviceStatusPage.tsx 中提取的重复函数实现。
 */

/**
 * 根据 SPK 物模型编码返回中文类型标签。
 *
 * 匹配规则（按 SPK 前缀 + 关键词）：
 *  - light.*：RGBWAF灯 / RGBCW灯 / RGBW灯 / RGB灯 / 色温灯 / 调光灯 / 开关灯 / 灯具
 *  - hvac.*：风扇 / 地暖 / 空调
 *  - curtain.*：窗帘
 *  - 包含 tv / pjt：电视
 *  - 包含 music：音乐
 *  - security.*：安防
 *  - sensor.*：传感器
 *  - 包含 socket：插座
 *  - 其他：原样返回 spk
 *
 * @param spk SPK 物模型编码（如 'light.rgb' / 'hvac.ac'）
 * @returns 中文类型标签
 */
export function getSpkLabel(spk?: string): string {
  if (!spk) return '未知';
  const s = spk.toLowerCase();
  if (s.startsWith('light.')) {
    if (s.includes('rgbwaf')) return 'RGBWAF灯';
    if (s.includes('rgbcw')) return 'RGBCW灯';
    if (s.includes('rgbw')) return 'RGBW灯';
    if (s.includes('rgb')) return 'RGB灯';
    if (s.includes('cct')) return '色温灯';
    if (s.includes('dimm')) return '调光灯';
    if (s.includes('switch')) return '开关灯';
    return '灯具';
  }
  if (s.startsWith('hvac.')) {
    if (s.includes('fan')) return '风扇';
    if (s.includes('heat')) return '地暖';
    return '空调';
  }
  if (s.startsWith('curtain.')) return '窗帘';
  if (s.includes('tv') || s.includes('pjt')) return '电视';
  if (s.includes('music')) return '音乐';
  if (s.startsWith('security.')) return '安防';
  if (s.startsWith('sensor.')) return '传感器';
  if (s.includes('socket')) return '插座';
  return spk;
}

/**
 * 状态项数据结构（HDL /device/info 返回的 status 数组元素）。
 */
export interface StatusItem {
  /** 属性 key（如 'on_off' / 'brightness'） */
  key: string;
  /** 属性值（标量，如 'on' / '99'） */
  value: string;
}

/**
 * 从 status 数组中按 key 取值。
 *
 * HDL 设备的 status 字段是 [{key, value}] 数组，本函数做线性查找并返回 value 的字符串形式。
 *
 * @param status 状态数组
 * @param key 属性 key
 * @returns 属性值字符串；未找到或 status 为空时返回 undefined
 */
export function getStatusValue(
  status: Array<StatusItem> | undefined,
  key: string,
): string | undefined {
  if (!status || !Array.isArray(status)) return undefined;
  const found = status.find(s => s && s.key === key);
  return found?.value != null ? String(found.value) : undefined;
}

/**
 * 将 RGB 字符串（如 '255,128,0'）转换为 hex 颜色字符串（如 '#ff8000'）。
 *
 * 解析规则：
 *  - 按 ',' 分割后逐段 parseInt
 *  - 过滤 NaN，至少需要 3 个有效数字
 *  - 每个通道值钳制到 [0, 255] 范围
 *  - 转为 2 位 hex 拼接为 #rrggbb
 *
 * @param rgbStr RGB 字符串（如 '255,128,0' / '255, 128, 0, 80'）
 * @returns hex 颜色字符串；输入为空或解析失败时返回 undefined
 */
export function rgbToHex(rgbStr: string): string | undefined {
  if (!rgbStr) return undefined;
  const parts = rgbStr.split(',').map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n));
  if (parts.length < 3) return undefined;
  const toHex = (n: number) => Math.max(0, Math.min(255, n)).toString(16).padStart(2, '0');
  return `#${toHex(parts[0])}${toHex(parts[1])}${toHex(parts[2])}`;
}
