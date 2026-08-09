/**
 * @file 通用工具函数统一导出
 *
 * 汇总 utils 子模块的对外 API：
 *  - error: sanitizeErrorMessage 错误消息脱敏
 *  - product: pickPrice 产品价格解析
 *
 * 调用方应从本文件统一引入。
 */

export * from './error';
export * from './product';
