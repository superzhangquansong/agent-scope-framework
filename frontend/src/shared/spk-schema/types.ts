/**
 * @file SPK 物模型 Schema 类型定义
 *
 * 定义 HDL 设备物模型（SPK Schema）相关 TypeScript 类型，
 * 供设备控制、场景创建等多个业务页面复用，消除各页面重复定义。
 *
 * 来源：从 DeviceStatusPage.tsx / SceneCreateResultPage.tsx 中提取，
 * 两侧定义保持完全一致，统一收敛至本文件。
 */

/**
 * SPK 属性枚举值
 *
 * 描述 enum 类型属性的可选值与中文说明，
 * 例如 on_off 属性的 enumerations 为 [{ value: 'on', desc: '开' }, { value: 'off', desc: '关' }]。
 */
export interface SpkEnumeration {
  /** 枚举值（如 'on' / 'off' / 'cool'） */
  value: string;
  /** 枚举中文描述（如 '开' / '关' / '制冷'） */
  desc: string;
}

/**
 * SPK 属性定义
 *
 * 描述单个设备属性（如 on_off 开关、brightness 亮度、rgb 颜色等），
 * 包括类型、访问权限、取值范围、枚举值、单位等元信息。
 */
export interface SpkAttribute {
  /** 属性 key（如 'on_off' / 'brightness' / 'rgb'） */
  key: string;
  /** 属性中文描述（如 '电源' / '亮度' / '颜色'） */
  desc: string;
  /** 属性类型：enum 枚举 / number 数值 / bool 布尔 / color 颜色 / string 字符串 */
  type: 'enum' | 'number' | 'bool' | 'color' | 'string';
  /** 访问权限：R 只读 / W 只写 / WR 读写 */
  access: 'R' | 'W' | 'WR';
  /** 单位（如 '%' / '℃' / 'K'） */
  unit?: string;
  /** 数值下限（number 类型适用） */
  min?: number;
  /** 数值上限（number 类型适用） */
  max?: number;
  /** 数值步长（number 类型适用） */
  step?: number;
  /** 枚举值列表（enum 类型适用） */
  enumerations?: SpkEnumeration[];
  /** 颜色通道格式（color 类型适用，如 'r,g,b' / 'r,g,b,w' / 'r,g,b,c,w'） */
  format?: string;
  /** 别名列表（属性 key 的可替代名称） */
  aliases?: string[];
}

/**
 * SPK 设备物模型
 *
 * 描述一个 SPK 设备类型（如 light.rgb / hvac.ac）的全部属性集合，
 * 对应 spk-schemas.json 中 schemas[spk] 字段。
 */
export interface SpkSchema {
  /** 设备类型名称（与 spk key 相同，如 'light.rgb'） */
  name: string;
  /** 别名列表 */
  aliases: string[];
  /** 属性定义列表 */
  attributes: SpkAttribute[];
}

/**
 * Schema 文件整体结构
 *
 * 对应 public/spk-schemas.json 的根结构，
 * schemas 是设备类型 -> 物模型的映射，_fallback 是兜底属性集合。
 */
export interface SpkSchemaFile {
  /** 设备类型 -> 物模型映射（key 如 'light.rgb'） */
  schemas: Record<string, SpkSchema>;
  /** 兜底物模型（spk 未命中时使用，仅含 attributes） */
  _fallback: { attributes: SpkAttribute[] };
}
