/**
 * @file 错误处理工具
 *
 * 提供错误消息脱敏函数 sanitizeErrorMessage，
 * 用于过滤掉程序内部信息（JSON 出参、堆栈、异常类名、HTTP 调试日志等），避免向用户暴露实现细节。
 *
 * 来源：从 App.tsx 和 ErrorResultPage.tsx 中提取的重复函数实现（两处实现完全一致）。
 */

/**
 * 错误消息脱敏：过滤掉程序内部信息，避免向用户暴露实现细节。
 *
 * 脱敏规则（命中任一即返回兜底文案「服务处理异常，请稍后重试」）：
 *  1. JSON 出参特征：以 { 或 [ 开头，或包含 "code": / "data": / "success": / "errorMessage": 等接口字段
 *  2. Java 异常类名：匹配 com.xxx.Exception / java.lang.NullPointerException 等
 *  3. 堆栈特征：匹配 `at com.xxx.method(` 行
 *  4. HTTP 调试日志：包含 >>> / <<< / POST ` / GET ` / resp= / requestBody= 等关键字
 *  5. JSONPath 提取失败：包含 JSONPath / extractByJsonPath / PathNotFoundException
 *
 * @param raw 原始错误消息
 * @returns 脱敏后的错误消息；输入为空时返回「处理失败」
 */
export function sanitizeErrorMessage(raw: string): string {
  if (!raw) return '处理失败';
  // 检测 JSON 出参特征（以 { 或 [ 开头，或包含 "code":、"data": 等接口字段）
  if (/^\s*[{[]/.test(raw) || /"code"\s*:|"data"\s*:|"success"\s*:|"errorMessage"\s*:/.test(raw)) {
    return '服务处理异常，请稍后重试';
  }
  // 检测 Java 异常类名（如 com.xxx.Exception、java.lang.NullPointerException）
  if (/[a-z]+\.[a-z]+\.[A-Z]\w*Exception/.test(raw) || /at\s+[\w.]+\(/.test(raw)) {
    return '服务处理异常，请稍后重试';
  }
  // 检测 HTTP 接口出参（如 POST `https://...`、resp= 等）
  if (/>>>|<<<|POST\s+`|GET\s+`|resp\s*=|requestBody\s*=/.test(raw)) {
    return '服务处理异常，请稍后重试';
  }
  // 检测 JSONPath 提取失败等内部日志
  if (/JSONPath|extractByJsonPath|PathNotFoundException/i.test(raw)) {
    return '服务处理异常，请稍后重试';
  }
  return raw;
}
