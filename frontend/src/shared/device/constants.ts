/**
 * @file 设备相关常量
 *
 * 集中定义 HDL 设备属性 key 与属性值的统一常量，
 * 供 DeviceListPage / DeviceStatusPage / useDeviceControl 等模块复用，
 * 消除各页面散落的魔法字符串。
 *
 * 来源：从 DeviceStatusPage.tsx / DeviceListPage.tsx 中提取的重复常量。
 */

/** 开关属性 key（HDL 设备统一使用 on_off） */
export const ATTR_ON_OFF = 'on_off';

/** 开关属性值：开 */
export const VALUE_ON = 'on';

/** 开关属性值：关 */
export const VALUE_OFF = 'off';

/** 色彩模式属性 key（部分灯具使用 colorful 切换色彩/白光模式） */
export const ATTR_COLORFUL = 'colorful';

/** RGB 颜色属性 key（light.rgb） */
export const ATTR_RGB = 'rgb';
