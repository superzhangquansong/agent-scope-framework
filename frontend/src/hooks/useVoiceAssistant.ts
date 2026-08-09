/**
 * 企业级语音助手 Hook（v3 - 持续监听 + 高识别率 + 自动重连 + 空闲休眠）
 *
 * 核心改进（v3）：
 *  1. onend 重启延迟降至 150ms（减少漏听窗口），重试 200ms，连续 5 次失败通知 UI
 *  2. maxAlternatives=5，遍历所有候选做唤醒词检测，显著提升召回率
 *  3. 唤醒词变体扩展（涵盖"你好小东东"、"嘿小东"、"小东小东"等口语化变体及同音字）
 *  4. normalizeText 增强：NFC 规范化 + 全角转半角 + Unicode 组合字符合并
 *  5. confidence 阈值过滤：唤醒词低置信度（<0.3）需多候选共识才触发
 *  6. 网络断开自动重连：监听 online/offline 事件，网络恢复后延迟 1s 恢复监听
 *  7. 动态静默超时：根据命令文本长度自适应（1500~4000ms）
 *  8. 空闲 5 分钟自动休眠（低功耗模式），降低浏览器功耗
 *  9. DEBUG 通过 options 传入，默认关闭，避免生产环境日志噪音
 * 10. 长时间未唤醒自动进入低功耗模式，用户点击麦克风按钮可重新激活
 *
 * @author HDL AI Assistant Team
 * @since v3.0
 */

import { useCallback, useEffect, useRef, useState } from 'react';

// ============================================================================
// Web Speech API TypeScript 类型声明
// ============================================================================

interface SpeechRecognitionAlternative {
  transcript: string;
  confidence: number;
}

interface SpeechRecognitionResult {
  readonly length: number;
  item(index: number): SpeechRecognitionAlternative;
  [index: number]: SpeechRecognitionAlternative;
  isFinal: boolean;
}

interface SpeechRecognitionEvent extends Event {
  readonly resultIndex: number;
  readonly results: {
    readonly length: number;
    item(index: number): SpeechRecognitionResult;
    [index: number]: SpeechRecognitionResult;
  };
}

interface SpeechRecognitionErrorEvent extends Event {
  readonly error: string;
  readonly message: string;
}

interface SpeechRecognitionInstance extends EventTarget {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  onstart: (() => void) | null;
}

interface SpeechRecognitionConstructor {
  new (): SpeechRecognitionInstance;
}

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  }
}

// ============================================================================
// 类型定义
// ============================================================================

export type VoiceState =
  | 'idle'
  | 'listening'
  | 'awake'
  | 'commanding'
  | 'speaking';

export interface UseVoiceAssistantOptions {
  wakeWord?: string;
  wakeWordVariants?: string[];
  onCommand: (text: string) => void;
  commandSilenceMs?: number;
  commandTimeoutMs?: number;
  ttsRate?: number;
  ttsPitch?: number;
  ttsVolume?: number;
  /** 调试模式（默认 false）。开启后控制台输出详细日志，便于排查问题 */
  debug?: boolean;
}

export interface UseVoiceAssistantReturn {
  state: VoiceState;
  enabled: boolean;
  supported: boolean;
  transcript: string;
  /** 错误信息（含解决指引） */
  error: string | null;
  /** 错误类型（not-allowed / network / unsupported / unknown） */
  errorType: 'not-allowed' | 'network' | 'unsupported' | 'unknown' | null;
  enable: () => void;
  disable: () => void;
  toggle: () => void;
  startCommand: () => void;
  cancelCommand: () => void;
  speak: (text: string) => void;
  stopSpeaking: () => void;
  skipCurrent: () => void;
  /** 恢复唤醒词监听（SSE 流程结束后调用，确保持续监听） */
  resumeListening: () => void;
}

// ============================================================================
// 常量
// ============================================================================

const DEFAULT_WAKE_WORD = '你好小东';

/**
 * 唤醒词变体（涵盖常见发音识别错误 + 口语化变体 + 同音字）。
 *
 * <p>包含以下类别：</p>
 * <ul>
 *   <li>标准变体（带/不带空格、逗号分隔）</li>
 *   <li>同音字误识别（小冬/晓东/肖东/小栋/小董/晓冬）</li>
 *   <li>口语化变体（你好小东东/嘿小东/小东小东/小东在吗/小东你好）</li>
 *   <li>简短唤醒词（仅"小东"等单字词）</li>
 * </ul>
 */
const DEFAULT_WAKE_VARIANTS = [
  // ===== 标准变体 =====
  '你好小东', '你好 小东', '你好，小东',
  // ===== 同音字误识别 =====
  '你好小冬', '你好 小冬', '你好，小冬',
  '你好晓东', '你好 晓东', '你好，晓东',
  '你好肖东', '你好 肖东',
  '你好小栋', '你好 小栋', '你好，小栋',
  '你好小董', '你好 小董', '你好，小董',
  '你好晓冬', '你好 晓冬', '你好，晓冬',
  // ===== 口语化变体（叠词 / 呼叫语） =====
  '你好小东东', '你好 小东东', '你好，小东东',
  '你好晓东东', '你好肖冬',
  '嘿小东', '嘿 小东', '嘿，小东',
  '小东小东', '小东 在吗', '小东在吗',
  '小东你好', '小东 你好',
  // ===== 简短唤醒（仅"小东"等单字词，用于短指令场景） =====
  '小东', '小冬', '晓东', '肖东', '小栋', '小董', '晓冬',
];

/** 唤醒后 TTS 朗读的欢迎语 */
const WAKE_REPLY = '在呢，有什么可以帮您';

/**
 * onend 后重启延迟（毫秒）。
 * <p>降低至 150ms（v2 为 300ms），减少漏听窗口。避免 Chrome continuous=true 死循环。</p>
 */
const RESTART_DELAY_MS = 150;

/**
 * 重启失败后的快速重试延迟（毫秒）。
 * <p>降低至 200ms（v2 为 500ms），加快恢复速度。</p>
 */
const RESTART_RETRY_DELAY_MS = 200;

/** 连续重启失败次数上限，超过后通知 UI 并停止重试 */
const MAX_RESTART_FAILURES = 5;

/** 命令模式静默超时（基础值，实际使用动态计算 getDynamicSilenceTimeout） */
const DEFAULT_COMMAND_SILENCE_MS = 1500;

/** 命令模式总超时 */
const DEFAULT_COMMAND_TIMEOUT_MS = 10000;

/**
 * 空闲休眠超时（5 分钟）。
 * <p>启用语音后如果 5 分钟内没有唤醒，自动进入低功耗模式（state='idle'），
 * 停止重启 SpeechRecognition 以降低浏览器功耗。用户点击麦克风按钮可重新激活。</p>
 */
const IDLE_TIMEOUT_MS = 5 * 60 * 1000;

/** 空闲休眠检查间隔（毫秒） */
const IDLE_CHECK_INTERVAL_MS = 60 * 1000;

/** 网络恢复后延迟启动监听的时间（毫秒） */
const ONLINE_RESUME_DELAY_MS = 1000;

/**
 * 唤醒词置信度阈值。
 * <p>低于此值的候选需要 ≥2 个候选同时命中唤醒词才触发（多候选共识机制），
 * 降低误唤醒率。</p>
 */
const WAKE_CONFIDENCE_THRESHOLD = 0.3;

/** SpeechRecognition maxAlternatives 数量 —— 提升唤醒词召回率 */
const MAX_ALTERNATIVES = 5;

/** 唤醒提示音 */
const BEEP_FREQUENCY_AWAKE = 880;
const BEEP_DURATION_AWAKE = 200;

/** TTS 句子切分正则 */
const SENTENCE_SPLIT_REGEX = /[^。！？.!?；;\n]+[。！？.!?；;\n]+/g;

/** TTS 单段最大字符数 */
const TTS_MAX_CHUNK_LENGTH = 200;

/**
 * 错别字纠正表（语音识别常见错误）。
 * <p>key = 错误识别，value = 正确文本。</p>
 * <p>覆盖：颜色、亮度、设备名、动作词、场景、英文/数字混合误识别。</p>
 */
const TYPO_CORRECTIONS: Record<string, string> = {
  // ===== 颜色相关 =====
  '红涩': '红色', '红社': '红色', '红设': '红色',
  '绿涩': '绿色', '绿社': '绿色',
  '蓝涩': '蓝色', '蓝社': '蓝色',
  '黄涩': '黄色', '黄社': '黄色',
  '白涩': '白色', '白社': '白色',
  '暖涩': '暖色', '暖社': '暖色',
  '冷涩': '冷色', '冷社': '冷色',
  // ===== 亮度相关 =====
  '凉度': '亮度', '量度': '亮度',
  // ===== 唤醒词相关 =====
  '小冬': '小东', '晓东': '小东', '肖东': '小东',
  // ===== 动作词相关 =====
  '看灯': '开灯', '关爱': '关灯',
  // ===== 场景相关 =====
  '回家建': '回家键', '离家见': '离家键',
  '回家晋': '回家景', '回家景': '回家场景',
  // ===== 设备名 Lite 误识别（中文谐音） =====
  '耐着': 'Lite ', '赖特': 'Lite ', '莱特': 'Lite ',
  '来特': 'Lite ', '雷特': 'Lite ', '拉特': 'Lite ',
  // ===== 设备名 RGB 误识别 =====
  '二GB': 'RGB', '二gb': 'RGB', '2GB': 'RGB', '2gb': 'RGB',
  '阿GB': 'RGB', '阿gb': 'RGB', 'R G B': 'RGB',
  '阿基': 'RGB', '阿吉': 'RGB',
  // ===== 设备类型名误识别 =====
  '色温等': '色温灯', '色温等灯': '色温灯',
  '调温等': '调温灯', '调光等': '调光灯',
  '继电等': '继电器',
  // ===== 英文小写转首字母大写（设备名规范） =====
  'light rgb': 'Lite RGB',
  'light调温': 'Lite 调温', 'light 调温': 'Lite 调温',
  'light色温': 'Lite 色温', 'light 色温': 'Lite 色温',
  'light调光': 'Lite 调光', 'light 调光': 'Lite 调光',
  // ===== 其他常见误识别 =====
  '创建一个': '创建一个', // 占位，确保短语完整性
  '暖涩灯': '暖色灯',
};

/**
 * 设备名智能归一化（正则匹配 + 替换）。
 *
 * <p>处理语音识别中常见的英文/数字/中文混合误识别，这些模式无法用简单的
 * 字符串替换覆盖所有变体，需要用正则表达式智能匹配：</p>
 * <ul>
 *   <li>"light" / "Light" / "LIGHT" → "Lite"（设备品牌名）</li>
 *   <li>"二GB" / "2GB" / "阿GB" → "RGB"（颜色通道）</li>
 *   <li>"耐着色温灯" → "Lite 色温灯"（设备全名归一化）</li>
 * </ul>
 *
 * @param text 语音识别原始文本
 * @return 归一化后的文本
 */
function normalizeDeviceNames(text: string): string {
  if (!text) return text;

  let result = text;

  // 1. "light" / "Light" / "LIGHT" → "Lite"（独立词匹配，不匹配包含 light 的其他词）
  //    但 "light rgb" → "Lite RGB" 需要先处理
  result = result.replace(/\blight\b/gi, 'Lite');
  // 处理 "lightRGB" / "lightrgb"（无空格连写）
  result = result.replace(/lightrgb/gi, 'Lite RGB');
  // 处理 "light二GB" / "light2GB"（中英混合）
  result = result.replace(/light\s*[二2两]\s*[Gg][Bb]/g, 'Lite RGB');
  result = result.replace(/light\s*[阿阿]\s*[Gg][Bb]/g, 'Lite RGB');

  // 2. RGB 相关纠正（独立出现的 "二GB" / "2GB" / "阿GB"）
  result = result.replace(/[二2两]\s*[Gg][Bb]/g, 'RGB');
  result = result.replace(/阿\s*[Gg][Bb]/g, 'RGB');
  result = result.replace(/[Rr]\s*[Gg]\s*[Bb]/g, 'RGB'); // "R G B" → "RGB"

  // 3. "耐着" / "赖特" / "莱特" → "Lite "（已在 TYPO_CORRECTIONS 中处理，这里做兜底）
  //    如果 "耐着" 后面直接跟 "色温" / "调温" 等，加空格
  result = result.replace(/耐着(色温|调温|调光|RGB|rgb)/g, 'Lite $1');
  result = result.replace(/(赖特|莱特|来特|雷特)(色温|调温|调光|RGB|rgb)/g, 'Lite $2');

  // 4. 处理 "LiteRGB"（无空格连写）
  result = result.replace(/LiteRGB/g, 'Lite RGB');

  // 5. 色温灯 / 调光灯 / 调温灯 误识别兜底
  result = result.replace(/色温等/g, '色温灯');
  result = result.replace(/调温等/g, '调温灯');
  result = result.replace(/调光等/g, '调光灯');

  return result;
}

/**
 * 综合错别字纠正：先做设备名智能归一化（正则），再做错别字表替换（精确匹配）。
 *
 * @param text 语音识别原始文本
 * @return 纠正后的文本
 */
function correctTypos(text: string): string {
  if (!text) return text;
  // 1. 先做设备名智能归一化（正则匹配，处理模式化错误）
  let result = normalizeDeviceNames(text);
  // 2. 再做错别字表精确替换（处理固定词汇错误）
  for (const [wrong, right] of Object.entries(TYPO_CORRECTIONS)) {
    result = result.split(wrong).join(right);
  }
  return result;
}

// ============================================================================
// 辅助函数
// ============================================================================

function getSpeechRecognition(): SpeechRecognitionConstructor | null {
  if (typeof window === 'undefined') return null;
  return window.SpeechRecognition ?? window.webkitSpeechRecognition ?? null;
}

/**
 * 归一化文本（增强版）。
 *
 * <p>处理步骤：</p>
 * <ol>
 *   <li>NFC 规范化：合并 Unicode 组合字符（如 é = e + ´ 合并为单字符）</li>
 *   <li>全角字符转半角（！→!, Ａ→A, ０→0 等）</li>
 *   <li>全角空格（U+3000）转普通空格</li>
 *   <li>去除标点符号和空格（中英文标点均处理）</li>
 *   <li>转小写</li>
 * </ol>
 *
 * @param s 原始文本
 * @return 归一化后的文本（无标点、无空格、小写、NFC 规范化）
 */
function normalizeText(s: string): string {
  return s
    // NFC 规范化（合并组合字符，如 e + ´ → é）
    .normalize('NFC')
    // 全角字符转半角（U+FF01~U+FF5E → U+0021~U+007E）
    .replace(/[\uFF01-\uFF5E]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0xFEE0))
    // 全角空格（U+3000）转普通空格
    .replace(/\u3000/g, ' ')
    // 去除标点符号和空格（中英文标点）
    .replace(/[\s,，。.！？!?；;、]/g, '')
    .toLowerCase();
}

/**
 * 检测文本是否包含唤醒词（支持多变体）。
 *
 * @param text 待检测文本
 * @param wakeWord 主唤醒词
 * @param variants 唤醒词变体列表
 * @return true 表示命中唤醒词
 */
function containsWakeWord(text: string, wakeWord: string, variants: string[]): boolean {
  if (!text) return false;
  const normalized = normalizeText(text);
  const allVariants = [wakeWord, ...variants];
  return allVariants.some((v) => {
    const nv = normalizeText(v);
    return nv.length > 0 && normalized.includes(nv);
  });
}

/**
 * 从文本中移除唤醒词，提取命令部分。
 *
 * @param text 原始文本（可能包含唤醒词 + 命令）
 * @param wakeWord 主唤醒词
 * @param variants 唤醒词变体列表
 * @return 移除唤醒词后的命令文本（已 trim）
 */
function stripWakeWord(text: string, wakeWord: string, variants: string[]): string {
  if (!text) return '';
  const allVariants = [wakeWord, ...variants].map(normalizeText).filter((s) => s.length > 0);
  allVariants.sort((a, b) => b.length - a.length);
  let result = text;
  for (const variant of allVariants) {
    const idx = result.indexOf(variant);
    if (idx >= 0) {
      result = result.substring(0, idx) + result.substring(idx + variant.length);
      break;
    }
  }
  return result.trim();
}

/**
 * 在单个识别结果（包含多个候选）中检测唤醒词。
 *
 * <p>置信度策略：</p>
 * <ul>
 *   <li>高置信度（≥ {@link WAKE_CONFIDENCE_THRESHOLD}）：单候选命中即触发</li>
 *   <li>低置信度（< {@link WAKE_CONFIDENCE_THRESHOLD}）：需要 ≥2 个候选同时命中才触发（多候选共识）</li>
 * </ul>
 *
 * @param result SpeechRecognition 单个结果（含多候选）
 * @param wakeWord 主唤醒词
 * @param variants 唤醒词变体列表
 * @return 检测结果：hit=是否命中，transcript=最佳候选文本，confidence=最佳候选置信度
 */
function detectWakeInResult(
  result: SpeechRecognitionResult,
  wakeWord: string,
  variants: string[],
): { hit: boolean; transcript: string; confidence: number } {
  const limit = Math.min(result.length, MAX_ALTERNATIVES);
  let hits = 0;
  let bestTranscript = '';
  let bestConfidence = 0;
  for (let j = 0; j < limit; j++) {
    const alt = result[j];
    const transcript = alt?.transcript ?? '';
    const confidence = alt?.confidence ?? 0;
    if (!transcript) continue;
    if (containsWakeWord(transcript, wakeWord, variants)) {
      hits++;
      if (confidence > bestConfidence) {
        bestConfidence = confidence;
        bestTranscript = transcript;
      }
    }
  }
  // 高置信度单候选命中，或低置信度多候选共识
  const hit = hits > 0 && (bestConfidence >= WAKE_CONFIDENCE_THRESHOLD || hits >= 2);
  return { hit, transcript: bestTranscript, confidence: bestConfidence };
}

/**
 * 根据命令文本长度计算动态静默超时。
 *
 * <p>短命令用较短超时（快速响应），长命令用较长超时（给用户更多时间说完）。</p>
 * <ul>
 *   <li>基础 1500ms</li>
 *   <li>每 10 个字符增加 500ms，最多增加 2500ms</li>
 *   <li>总超时上限 4000ms</li>
 * </ul>
 *
 * @param text 当前命令文本
 * @return 动态静默超时（毫秒）
 */
function getDynamicSilenceTimeout(text: string): number {
  const baseMs = DEFAULT_COMMAND_SILENCE_MS;
  const extraMs = Math.min((text.length / 10) * 500, 2500);
  return Math.min(baseMs + extraMs, 4000);
}

/**
 * 将长文本切分为 TTS 句子片段。
 *
 * @param text 待切分文本
 * @return 句子片段数组（每段不超过 TTS_MAX_CHUNK_LENGTH 字符）
 */
export function splitTextForTTS(text: string): string[] {
  if (!text) return [];
  const chunks: string[] = [];
  const sentences: string[] = text.match(SENTENCE_SPLIT_REGEX) ?? [];
  const remaining = text.replace(SENTENCE_SPLIT_REGEX, '');
  if (sentences.length === 0 && remaining.trim()) {
    sentences.push(remaining);
  } else if (remaining.trim()) {
    sentences.push(remaining.trim());
  }
  for (const sentence of sentences) {
    const trimmed = sentence.trim();
    if (!trimmed) continue;
    if (trimmed.length <= TTS_MAX_CHUNK_LENGTH) {
      chunks.push(trimmed);
    } else {
      let rest = trimmed;
      while (rest.length > TTS_MAX_CHUNK_LENGTH) {
        let cut = TTS_MAX_CHUNK_LENGTH;
        const commaIdx = rest.lastIndexOf('，', TTS_MAX_CHUNK_LENGTH);
        const spaceIdx = rest.lastIndexOf(' ', TTS_MAX_CHUNK_LENGTH);
        if (commaIdx > TTS_MAX_CHUNK_LENGTH / 2) cut = commaIdx + 1;
        else if (spaceIdx > TTS_MAX_CHUNK_LENGTH / 2) cut = spaceIdx + 1;
        chunks.push(rest.substring(0, cut));
        rest = rest.substring(cut);
      }
      if (rest.trim()) chunks.push(rest.trim());
    }
  }
  // 过滤标点符号和特殊字符（TTS 不需要朗读标点）
  return chunks.map(stripPunctuationForTTS).filter(c => c.length > 0);
}

/**
 * 移除文本中的标点符号和特殊字符，仅保留汉字、字母、数字和空格。
 * TTS 朗读时不需要朗读标点符号（句号、逗号、问号、感叹号等）。
 *
 * @param text 原始文本
 * @return 过滤后的纯文本
 */
function stripPunctuationForTTS(text: string): string {
  // 中英文标点符号 + 特殊符号（emoji、markdown、控制字符等）
  // 保留：汉字 \u4e00-\u9fff、字母 a-zA-Z、数字 0-9、空格、以及常见英文缩写中的点
  return text
    // 移除中英文标点
    .replace(/[，。！？；：、""''（）【】《》…—~·]/g, '')
    .replace(/[,!?;:"'()\[\]<>.\-]/g, ' ')
    // 移除 emoji 和特殊符号
    .replace(/[\u{1F000}-\u{1FFFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}]/gu, '')
    // 移除 markdown 符号
    .replace(/[#*_`~|\\>/]/g, '')
    // 压缩多余空格
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * 播放提示音（Web Audio API）。
 *
 * @param frequency 频率（Hz）
 * @param duration 持续时间（毫秒）
 */
function playBeep(frequency: number, duration: number): void {
  try {
    const AudioContextClass = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.connect(gain);
    gain.connect(ctx.destination);
    oscillator.frequency.value = frequency;
    oscillator.type = 'sine';
    gain.gain.setValueAtTime(0.15, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration / 1000);
    oscillator.start();
    oscillator.stop(ctx.currentTime + duration / 1000);
    oscillator.onended = () => {
      ctx.close().catch(() => { /* ignore */ });
    };
  } catch {
    // 提示音失败不影响主流程
  }
}

// ============================================================================
// Hook 实现
// ============================================================================

/**
 * 企业级语音助手 Hook。
 *
 * <p>提供语音唤醒、命令识别、TTS 朗读等完整功能，支持持续监听、
 * 网络断线重连、空闲自动休眠等企业级特性。</p>
 *
 * @param options 配置选项
 * @returns 语音助手控制接口
 */
export function useVoiceAssistant(options: UseVoiceAssistantOptions): UseVoiceAssistantReturn {
  const {
    wakeWord = DEFAULT_WAKE_WORD,
    wakeWordVariants = DEFAULT_WAKE_VARIANTS,
    onCommand,
    commandTimeoutMs = DEFAULT_COMMAND_TIMEOUT_MS,
    ttsRate = 1.0,
    ttsPitch = 1.0,
    ttsVolume = 1.0,
    debug = false,
  } = options;

  // ===== 状态 =====
  const [state, setState] = useState<VoiceState>('idle');
  const [enabled, setEnabled] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [errorType, setErrorType] = useState<'not-allowed' | 'network' | 'unsupported' | 'unknown' | null>(null);

  const supported = typeof window !== 'undefined' && (
    'SpeechRecognition' in window || 'webkitSpeechRecognition' in window
  ) && 'speechSynthesis' in window;

  // ===== 引用 =====
  const onCommandRef = useRef(onCommand);
  useEffect(() => {
    onCommandRef.current = onCommand;
  }, [onCommand]);

  const wakeRecognitionRef = useRef<SpeechRecognitionInstance | null>(null);
  const commandRecognitionRef = useRef<SpeechRecognitionInstance | null>(null);
  const ttsQueueRef = useRef<string[]>([]);
  const ttsSpeakingRef = useRef(false);
  const silenceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const commandTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const commandBufferRef = useRef('');
  const manualAbortRef = useRef(false);
  const enabledRef = useRef(false);
  /** 重启定时器（onend 后延迟重启） */
  const restartTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** 连续重启失败计数器（成功后归零） */
  const restartFailuresRef = useRef(0);
  /** 最后一次唤醒成功的时间戳（用于空闲休眠判断） */
  const lastWakeTimeRef = useRef(0);
  /** 网络是否处于离线状态（用于 onerror 中判断 network 错误是否等待恢复） */
  const networkOfflineRef = useRef(false);
  /** 是否处于空闲休眠模式（低功耗模式） */
  const isIdleSleepRef = useRef(false);
  /** 调试模式 ref（避免 log 函数依赖变化导致 useCallback 重建） */
  const debugRef = useRef(debug);
  useEffect(() => {
    debugRef.current = debug;
  }, [debug]);

  /**
   * 调试日志函数（稳定引用，通过 debugRef 读取最新配置）。
   *
   * @param args 日志参数
   */
  const log = useCallback((...args: unknown[]) => {
    if (debugRef.current) console.log('[VoiceAssistant]', ...args);
  }, []);

  // ===== 清理定时器 =====
  const clearCommandTimers = useCallback(() => {
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
    if (commandTimeoutRef.current) {
      clearTimeout(commandTimeoutRef.current);
      commandTimeoutRef.current = null;
    }
  }, []);

  const clearRestartTimer = useCallback(() => {
    if (restartTimerRef.current) {
      clearTimeout(restartTimerRef.current);
      restartTimerRef.current = null;
    }
  }, []);

  // ===== 创建 SpeechRecognition 实例 =====
  // 【关键修复】统一使用 continuous=false，避免 Chrome 死循环
  // 【v3 改进】maxAlternatives=5，提升唤醒词召回率
  const createRecognition = useCallback((interimResults: boolean): SpeechRecognitionInstance | null => {
    const Ctor = getSpeechRecognition();
    if (!Ctor) return null;
    const recognition = new Ctor();
    recognition.lang = 'zh-CN';
    recognition.continuous = false;  // 【关键】不用 continuous=true，改用 onend 后延迟重启
    recognition.interimResults = interimResults;
    recognition.maxAlternatives = MAX_ALTERNATIVES; // 【v3】从 1 提升至 5
    return recognition;
  }, []);

  // ===== 停止唤醒词监听 =====
  const stopWakeRecognition = useCallback(() => {
    clearRestartTimer();
    if (wakeRecognitionRef.current) {
      manualAbortRef.current = true;
      try {
        wakeRecognitionRef.current.abort();
      } catch {
        // ignore
      }
      wakeRecognitionRef.current = null;
    }
  }, [clearRestartTimer]);

  // ===== 停止命令识别 =====
  const stopCommandRecognition = useCallback(() => {
    if (commandRecognitionRef.current) {
      manualAbortRef.current = true;
      try {
        commandRecognitionRef.current.abort();
      } catch {
        // ignore
      }
      commandRecognitionRef.current = null;
    }
    clearCommandTimers();
  }, [clearCommandTimers]);

  // ===== 前向声明：startCommandRecognition 和 startWakeListening 互相引用 =====
  const startCommandRecognitionRef = useRef<() => void>(() => {});
  const startWakeListeningRef = useRef<() => void>(() => {});
  /** 空闲休眠函数 ref（前向声明，避免循环依赖） */
  const enterIdleSleepRef = useRef<() => void>(() => {});

  // ===== 唤醒成功：播放提示音 + TTS 朗读"在呢，有什么可以帮您" + 进入命令模式 =====
  const handleWakeSuccess = useCallback((trailingCommand: string) => {
    log('✓ 唤醒成功！trailingCommand=', trailingCommand);
    // 更新最后唤醒时间（重置空闲休眠计时）
    lastWakeTimeRef.current = Date.now();
    // 退出空闲休眠模式（如果之前处于休眠）
    isIdleSleepRef.current = false;
    // 播放唤醒提示音
    playBeep(BEEP_FREQUENCY_AWAKE, BEEP_DURATION_AWAKE);
    setState('awake');

    if (trailingCommand && trailingCommand.length >= 2) {
      // 唤醒词后已带命令（如"你好小东开红色"），跳过欢迎语直接执行
      log('唤醒词后带命令，直接执行: ', trailingCommand);
      const corrected = correctTypos(trailingCommand);
      if (corrected !== trailingCommand) {
        log('错别字纠正: ', trailingCommand, '→', corrected);
      }
      onCommandRef.current(corrected);
    } else {
      // 仅唤醒：TTS 朗读"在呢，有什么可以帮您"，朗读完成后进入命令模式
      log('仅唤醒，TTS 朗读欢迎语: ', WAKE_REPLY);
      // 先入队欢迎语，朗读完成后再进入命令模式
      ttsQueueRef.current = [WAKE_REPLY];
      ttsSpeakingRef.current = false;
      // 手动触发 TTS 处理（不通过 speak 方法，因为需要在朗读完成后进入命令模式）
      const utterance = new SpeechSynthesisUtterance(WAKE_REPLY);
      utterance.lang = 'zh-CN';
      utterance.rate = ttsRate;
      utterance.pitch = ttsPitch;
      utterance.volume = ttsVolume;

      // 选择中文女声
      const voices = window.speechSynthesis.getVoices();
      const zhVoice = voices.find((v) => v.lang.startsWith('zh') && /female|女|xiaomei|xiaoyan|ting/i.test(v.name))
        ?? voices.find((v) => v.lang.startsWith('zh'));
      if (zhVoice) utterance.voice = zhVoice;

      setState('speaking');
      ttsSpeakingRef.current = true;
      utterance.onend = () => {
        log('欢迎语朗读完成，进入命令模式');
        ttsSpeakingRef.current = false;
        ttsQueueRef.current = [];
        startCommandRecognitionRef.current();
      };
      utterance.onerror = () => {
        log('欢迎语朗读出错，仍进入命令模式', 'warn');
        ttsSpeakingRef.current = false;
        ttsQueueRef.current = [];
        startCommandRecognitionRef.current();
      };
      window.speechSynthesis.speak(utterance);
    }
  }, [ttsRate, ttsPitch, ttsVolume, log]);

  // ===== 启动唤醒词监听（continuous=false + onend 延迟重启） =====
  const startWakeListening = useCallback(() => {
    if (!supported) {
      log('startWakeListening: 浏览器不支持');
      return;
    }
    // 如果处于空闲休眠模式，不启动监听
    if (isIdleSleepRef.current) {
      log('startWakeListening: 处于空闲休眠模式，跳过');
      return;
    }
    // 如果网络离线，不启动监听（等待 online 事件恢复）
    if (networkOfflineRef.current) {
      log('startWakeListening: 网络离线，跳过（等待网络恢复）');
      return;
    }
    // 先停止现有监听和重启定时器
    clearRestartTimer();
    if (wakeRecognitionRef.current) {
      manualAbortRef.current = true;
      try { wakeRecognitionRef.current.abort(); } catch { /* ignore */ }
      wakeRecognitionRef.current = null;
    }
    manualAbortRef.current = false;

    // 【关键修复】continuous=false，避免 Chrome 死循环
    const recognition = createRecognition(true);
    if (!recognition) {
      setError('SpeechRecognition 不可用');
      return;
    }

    log('startWakeListening: 创建 SpeechRecognition（continuous=false, interimResults=true, maxAlternatives=' + MAX_ALTERNATIVES + '）');

    recognition.onstart = () => {
      setError(null);
      // 重启成功，归零失败计数器
      restartFailuresRef.current = 0;
      if (enabledRef.current) {
        setState('listening');
      }
      log('onstart: 监听唤醒词"你好小东"中...');
    };

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      // 收集所有识别文本（取第一候选用于 UI 显示）
      let fullText = '';
      for (let i = 0; i < event.results.length; i++) {
        fullText += event.results[i][0].transcript;
      }
      log('onresult: fullText=', fullText);

      // 更新 UI 显示
      setTranscript(fullText);

      // 【v3 改进】遍历所有结果的候选做唤醒词检测（含 confidence 阈值过滤）
      for (let i = 0; i < event.results.length; i++) {
        const result = event.results[i];
        const detection = detectWakeInResult(result, wakeWord, wakeWordVariants);
        if (detection.hit) {
          log(`✓ 唤醒词命中 (confidence=${detection.confidence.toFixed(2)}, transcript="${detection.transcript}")`);
          // 提取唤醒词后的命令部分
          const trailingCommand = stripWakeWord(detection.transcript, wakeWord, wakeWordVariants);
          // 停止唤醒词监听
          stopWakeRecognition();
          // 处理唤醒（TTS 欢迎语 + 命令模式）
          handleWakeSuccess(trailingCommand);
          return;
        }
      }
    };

    recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
      log('onerror:', event.error, event.message);
      // not-allowed / aborted 是正常的，不当作错误
      if (event.error === 'no-speech' || event.error === 'aborted') {
        return;
      }
      // not-allowed / service-not-allowed：权限被拒绝（最常见问题）
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        setErrorType('not-allowed');
        setError('麦克风权限被拒绝！请按以下步骤修复：\n' +
          '1. 点击浏览器地址栏左侧的锁图标（🔒）或设置图标（⚙）\n' +
          '2. 找到"麦克风"选项，改为"允许"\n' +
          '3. 刷新页面后重新点击麦克风按钮\n' +
          '注意：必须使用 localhost 或 HTTPS 地址访问，HTTP 地址无法使用麦克风');
        enabledRef.current = false;
        setEnabled(false);
        setState('idle');
        log('麦克风权限被拒绝，已禁用语音助手', 'error');
        return;
      }
      // network 错误：设置离线标记，等待 online 事件自动恢复
      if (event.error === 'network') {
        networkOfflineRef.current = true;
        setErrorType('network');
        setError('网络连接异常，语音识别暂停。网络恢复后将自动重连');
        // 设置 manualAbort 阻止 onend 重启，等待 online 事件恢复
        manualAbortRef.current = true;
        log('网络错误，等待网络恢复后自动重连', 'warn');
        return;
      }
      // audio-capture 错误：音频设备问题
      if (event.error === 'audio-capture') {
        setErrorType('network');
        setError(`音频设备错误: ${event.error}。请检查麦克风是否正常连接`);
      } else {
        setErrorType('unknown');
        setError(`语音识别错误: ${event.error}`);
      }
    };

    recognition.onend = () => {
      log('onend: manualAbort=', manualAbortRef.current, 'enabledRef=', enabledRef.current, 'failures=', restartFailuresRef.current);
      // 【关键修复】onend 后延迟重启（避免 Chrome 立即重启导致死循环）

      // 空闲休眠检查：距离上次唤醒超过 IDLE_TIMEOUT_MS 则进入低功耗模式
      if (!manualAbortRef.current && enabledRef.current && !isIdleSleepRef.current) {
        const idleElapsed = Date.now() - lastWakeTimeRef.current;
        if (idleElapsed >= IDLE_TIMEOUT_MS) {
          log(`onend: 空闲超时（${Math.round(idleElapsed / 1000)}s 未唤醒），进入低功耗模式`, 'warn');
          enterIdleSleepRef.current();
          return;
        }
      }

      if (!manualAbortRef.current && enabledRef.current && !isIdleSleepRef.current && !networkOfflineRef.current) {
        log('onend: 延迟', RESTART_DELAY_MS, 'ms 后重启...');
        restartTimerRef.current = setTimeout(() => {
          if (!manualAbortRef.current && enabledRef.current && !isIdleSleepRef.current && !networkOfflineRef.current) {
            try {
              log('onend: 重启 SpeechRecognition');
              recognition.start();
            } catch (e) {
              restartFailuresRef.current++;
              const errMsg = e instanceof Error ? e.message : String(e);
              log(`onend: 重启失败 (${restartFailuresRef.current}/${MAX_RESTART_FAILURES}):`, errMsg, 'warn');
              // 连续失败超过上限，通知 UI 并停止重试
              if (restartFailuresRef.current >= MAX_RESTART_FAILURES) {
                setErrorType('unknown');
                setError(`语音监听连续重启失败 ${MAX_RESTART_FAILURES} 次，已停止。请检查浏览器麦克风权限或刷新页面重试`);
                setState('idle');
                enabledRef.current = false;
                setEnabled(false);
                log('onend: 连续重启失败达上限，已停止监听', 'error');
                return;
              }
              // 快速重试一次（200ms）
              restartTimerRef.current = setTimeout(() => {
                if (!manualAbortRef.current && enabledRef.current && !isIdleSleepRef.current && !networkOfflineRef.current) {
                  try {
                    recognition.start();
                  } catch (e2) {
                    restartFailuresRef.current++;
                    const errMsg2 = e2 instanceof Error ? e2.message : String(e2);
                    log(`onend: 重试仍失败 (${restartFailuresRef.current}/${MAX_RESTART_FAILURES}):`, errMsg2, 'error');
                    if (restartFailuresRef.current >= MAX_RESTART_FAILURES) {
                      setErrorType('unknown');
                      setError(`语音监听连续重启失败 ${MAX_RESTART_FAILURES} 次，已停止。请检查浏览器麦克风权限或刷新页面重试`);
                      setState('idle');
                      enabledRef.current = false;
                      setEnabled(false);
                    }
                  }
                }
              }, RESTART_RETRY_DELAY_MS);
            }
          }
        }, RESTART_DELAY_MS);
      } else {
        log('onend: 不重启（manualAbort / disabled / idleSleep / offline）');
      }
    };

    wakeRecognitionRef.current = recognition;
    try {
      recognition.start();
    } catch (e) {
      setError(`启动监听失败: ${e instanceof Error ? e.message : String(e)}`);
      log('startWakeListening: start() 失败:', e instanceof Error ? e.message : String(e), 'error');
    }
  }, [supported, wakeWord, wakeWordVariants, createRecognition, clearRestartTimer, stopWakeRecognition, handleWakeSuccess, log]);

  // 更新 ref
  useEffect(() => {
    startWakeListeningRef.current = startWakeListening;
  }, [startWakeListening]);

  // ===== 启动命令识别 =====
  const startCommandRecognition = useCallback(() => {
    if (!supported) return;
    // 停止唤醒词监听
    clearRestartTimer();
    if (wakeRecognitionRef.current) {
      manualAbortRef.current = true;
      try { wakeRecognitionRef.current.abort(); } catch { /* ignore */ }
      wakeRecognitionRef.current = null;
    }
    manualAbortRef.current = false;
    commandBufferRef.current = '';
    setTranscript('');
    setState('commanding');
    clearCommandTimers();

    // 命令模式也用 continuous=false，单次识别
    const recognition = createRecognition(true);
    if (!recognition) {
      setError('SpeechRecognition 不可用');
      startWakeListeningRef.current();
      return;
    }

    log('startCommandRecognition: 创建命令识别（continuous=false, interimResults=true, maxAlternatives=' + MAX_ALTERNATIVES + '）');

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      let interimText = '';
      let finalText = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) {
          finalText += result[0].transcript;
        } else {
          interimText += result[0].transcript;
        }
      }
      log('command onresult: interim=', interimText, 'final=', finalText);

      const currentText = (finalText + interimText).trim();
      if (currentText) {
        setTranscript(currentText);
        commandBufferRef.current = currentText;
      }
      // 收到 final 结果后启动动态静默超时
      if (finalText) {
        // 【v3 改进】使用动态静默超时（根据命令长度自适应 1500~4000ms）
        const dynamicSilence = getDynamicSilenceTimeout(currentText);
        log(`command 收到 final，启动动态静默超时 ${dynamicSilence}ms（text length=${currentText.length}）`);
        if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
        silenceTimerRef.current = setTimeout(() => {
          const command = commandBufferRef.current.trim();
          log('command 静默超时，提交命令:', command);
          // 提交前做错别字纠正
          const corrected = correctTypos(command);
          if (corrected !== command) {
            log('错别字纠正:', command, '→', corrected);
          }
          stopCommandRecognition();
          if (corrected) {
            onCommandRef.current(corrected);
          } else {
            startWakeListeningRef.current();
          }
        }, dynamicSilence);
      }
    };

    recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
      log('command onerror:', event.error, event.message);
      if (event.error !== 'no-speech' && event.error !== 'aborted') {
        setError(`命令识别错误: ${event.error}`);
      }
    };

    recognition.onend = () => {
      log('command onend: buffer=', commandBufferRef.current, 'manualAbort=', manualAbortRef.current);
      clearCommandTimers();
      const command = commandBufferRef.current.trim();
      if (command && !manualAbortRef.current) {
        // 错别字纠正
        const corrected = correctTypos(command);
        if (corrected !== command) {
          log('错别字纠正:', command, '→', corrected);
        }
        log('command onend: 提交命令:', corrected);
        onCommandRef.current(corrected);
      } else if (!command && !manualAbortRef.current) {
        // 无识别结果，回到监听状态
        log('command onend: 无结果，回到监听');
        startWakeListeningRef.current();
      }
    };

    commandRecognitionRef.current = recognition;
    try {
      recognition.start();
    } catch (e) {
      setError(`启动命令识别失败: ${e instanceof Error ? e.message : String(e)}`);
      startWakeListeningRef.current();
      return;
    }

    // 命令模式总超时
    commandTimeoutRef.current = setTimeout(() => {
      const cmd = commandBufferRef.current.trim();
      log('command 总超时，提交:', cmd, 'warn');
      stopCommandRecognition();
      if (cmd) {
        const corrected = correctTypos(cmd);
        onCommandRef.current(corrected);
      } else {
        startWakeListeningRef.current();
      }
    }, commandTimeoutMs);
  }, [supported, createRecognition, clearRestartTimer, clearCommandTimers, stopCommandRecognition, commandTimeoutMs, log]);

  // 更新 ref
  useEffect(() => {
    startCommandRecognitionRef.current = startCommandRecognition;
  }, [startCommandRecognition]);

  // ===== 进入空闲休眠模式（低功耗） =====
  const enterIdleSleep = useCallback(() => {
    log('进入空闲休眠模式（低功耗），停止 SpeechRecognition 重启', 'warn');
    isIdleSleepRef.current = true;
    setState('idle');
    stopWakeRecognition();
    stopCommandRecognition();
    clearRestartTimer();
    // 注意：保持 enabledRef.current = true，使得 toggle() 能识别"从休眠唤醒"场景
  }, [stopWakeRecognition, stopCommandRecognition, clearRestartTimer, log]);

  // 更新 enterIdleSleep ref
  useEffect(() => {
    enterIdleSleepRef.current = enterIdleSleep;
  }, [enterIdleSleep]);

  // ===== TTS 朗读（用于 onToken 流式朗读 AI 回复） =====
  const processTtsQueue = useCallback(() => {
    if (ttsSpeakingRef.current) return;
    const next = ttsQueueRef.current.shift();
    if (!next) {
      // 队列空：如果当前是 speaking 状态，恢复唤醒词监听
      // 【关键修复】TTS 朗读完成后必须恢复监听，否则第二次喊唤醒词没反应
      if (enabledRef.current) {
        log('TTS 队列空，恢复唤醒词监听');
        setState('listening');
        startWakeListeningRef.current();
      }
      return;
    }
    ttsSpeakingRef.current = true;
    setState('speaking');
    // 朗读时停止麦克风，避免朗读声被识别为命令
    if (wakeRecognitionRef.current) {
      manualAbortRef.current = true;
      try { wakeRecognitionRef.current.abort(); } catch { /* ignore */ }
    }

    const utterance = new SpeechSynthesisUtterance(next);
    utterance.lang = 'zh-CN';
    utterance.rate = ttsRate;
    utterance.pitch = ttsPitch;
    utterance.volume = ttsVolume;

    const voices = window.speechSynthesis.getVoices();
    const zhVoice = voices.find((v) => v.lang.startsWith('zh') && /female|女|xiaomei|xiaoyan|ting/i.test(v.name))
      ?? voices.find((v) => v.lang.startsWith('zh'));
    if (zhVoice) utterance.voice = zhVoice;

    utterance.onend = () => {
      ttsSpeakingRef.current = false;
      processTtsQueue();
    };
    utterance.onerror = () => {
      ttsSpeakingRef.current = false;
      processTtsQueue();
    };
    window.speechSynthesis.speak(utterance);
  }, [ttsRate, ttsPitch, ttsVolume, log]);

  // ===== 对外 API =====

  /**
   * 开启语音助手。
   * 【关键改进】先调用 getUserMedia 主动触发浏览器权限弹窗。
   * 浏览器记住了之前的"拒绝"决定后，SpeechRecognition 直接返回 not-allowed，
   * 但 getUserMedia 会重新触发权限弹窗，让用户重新选择。
   *
   * <p>v3 新增：重置空闲休眠状态和重启失败计数器。</p>
   */
  const enable = useCallback(async () => {
    if (!supported) {
      setErrorType('unsupported');
      setError('当前浏览器不支持语音识别（请使用 Chrome 或 Edge）');
      log('enable: 浏览器不支持');
      return;
    }

    // 【关键改进】先用 getUserMedia 请求麦克风权限，触发浏览器弹窗
    log('enable: 请求麦克风权限（getUserMedia）...');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      // 权限已授予：停止音轨（只需触发权限，不需要实际录音）
      stream.getTracks().forEach((track) => track.stop());
      log('enable: 麦克风权限已授予', 'success');
    } catch (e) {
      const errName = e instanceof DOMException ? e.name : 'UnknownError';
      log('enable: getUserMedia 失败:', errName, 'error');
      if (errName === 'NotAllowedError' || errName === 'PermissionDeniedError') {
        setErrorType('not-allowed');
        setError('麦克风权限被拒绝！请按以下步骤修复：\n' +
          '方法1（推荐）：在 Chrome 地址栏输入 chrome://settings/content/microphone\n' +
          '   → 在"不允许"列表中找到 localhost:5173\n' +
          '   → 点击右侧×按钮删除该条目\n' +
          '   → 刷新页面后重新点击麦克风按钮，会弹出权限对话框\n' +
          '方法2：点击地址栏左侧锁图标 🔒 → 麦克风 → 允许 → 刷新\n' +
          '方法3：使用无痕模式（Ctrl+Shift+N）打开 http://localhost:5173');
      } else if (errName === 'NotFoundError' || errName === 'DevicesNotFoundError') {
        setErrorType('network');
        setError('未检测到麦克风设备。请检查麦克风是否正常连接');
      } else {
        setErrorType('unknown');
        setError(`麦克风访问错误: ${errName}`);
      }
      return;
    }

    log('enable: 开启语音助手');
    enabledRef.current = true;
    isIdleSleepRef.current = false;
    networkOfflineRef.current = false;
    restartFailuresRef.current = 0;
    lastWakeTimeRef.current = Date.now();
    setEnabled(true);
    setState('listening');
    setTranscript('');
    setError(null);
    setErrorType(null);
    // 预加载 TTS 声音
    window.speechSynthesis.getVoices();
    startWakeListening();
  }, [supported, startWakeListening, log]);

  const disable = useCallback(() => {
    log('disable: 关闭语音助手');
    enabledRef.current = false;
    isIdleSleepRef.current = false;
    networkOfflineRef.current = false;
    restartFailuresRef.current = 0;
    setEnabled(false);
    setState('idle');
    stopWakeRecognition();
    stopCommandRecognition();
    window.speechSynthesis.cancel();
    ttsSpeakingRef.current = false;
    ttsQueueRef.current = [];
    setTranscript('');
    clearCommandTimers();
    clearRestartTimer();
  }, [stopWakeRecognition, stopCommandRecognition, clearCommandTimers, clearRestartTimer, log]);

  /**
   * 切换语音助手开关。
   *
   * <p>v3 新增：如果当前处于空闲休眠模式，点击麦克风按钮会重新激活监听，
   * 而不是关闭语音助手（提升用户体验）。</p>
   */
  const toggle = useCallback(() => {
    if (enabledRef.current) {
      if (isIdleSleepRef.current) {
        // 低功耗模式下点击麦克风按钮：重新激活监听
        log('toggle: 从空闲休眠中重新激活');
        isIdleSleepRef.current = false;
        lastWakeTimeRef.current = Date.now();
        restartFailuresRef.current = 0;
        setError(null);
        setErrorType(null);
        setState('listening');
        startWakeListeningRef.current();
      } else {
        disable();
      }
    } else {
      enable();
    }
  }, [enable, disable, log]);

  const startCommand = useCallback(() => {
    if (!supported || !enabledRef.current) return;
    // 如果处于空闲休眠模式，先重新激活
    if (isIdleSleepRef.current) {
      log('startCommand: 从空闲休眠中重新激活');
      isIdleSleepRef.current = false;
      lastWakeTimeRef.current = Date.now();
      restartFailuresRef.current = 0;
    }
    window.speechSynthesis.cancel();
    ttsSpeakingRef.current = false;
    ttsQueueRef.current = [];
    startCommandRecognition();
  }, [supported, startCommandRecognition, log]);

  const cancelCommand = useCallback(() => {
    stopCommandRecognition();
    setTranscript('');
    commandBufferRef.current = '';
    if (enabledRef.current) {
      startWakeListening();
    }
  }, [stopCommandRecognition, startWakeListening]);

  const speak = useCallback((text: string) => {
    if (!text) return;
    const chunks = splitTextForTTS(text);
    if (chunks.length === 0) return;
    ttsQueueRef.current.push(...chunks);
    processTtsQueue();
  }, [processTtsQueue]);

  const stopSpeaking = useCallback(() => {
    window.speechSynthesis.cancel();
    ttsSpeakingRef.current = false;
    ttsQueueRef.current = [];
    if (state === 'speaking') {
      setState('listening');
      startWakeListening();
    }
  }, [state, startWakeListening]);

  const skipCurrent = useCallback(() => {
    window.speechSynthesis.cancel();
  }, []);

  /**
   * 恢复唤醒词监听。
   * 在 SSE 流程结束后（onResult/onDone）调用，确保持续监听。
   * 如果 TTS 队列还有内容正在朗读，等朗读完成后再恢复（processTtsQueue 会自动恢复）。
   * 如果没有 TTS 朗读，立即恢复监听。
   */
  const resumeListening = useCallback(() => {
    if (!enabledRef.current) {
      log('resumeListening: 语音助手未启用，跳过');
      return;
    }
    // 如果处于空闲休眠模式，先重新激活
    if (isIdleSleepRef.current) {
      log('resumeListening: 从空闲休眠中重新激活');
      isIdleSleepRef.current = false;
      lastWakeTimeRef.current = Date.now();
      restartFailuresRef.current = 0;
    }
    // 如果 TTS 还在朗读，等朗读完成后 processTtsQueue 会自动恢复
    if (ttsSpeakingRef.current || ttsQueueRef.current.length > 0) {
      log('resumeListening: TTS 正在朗读，等朗读完成后自动恢复');
      return;
    }
    log('resumeListening: 恢复唤醒词监听');
    setState('listening');
    startWakeListeningRef.current();
  }, [log]);

  // ===== 组件卸载清理 =====
  useEffect(() => {
    return () => {
      enabledRef.current = false;
      if (wakeRecognitionRef.current) {
        try { wakeRecognitionRef.current.abort(); } catch { /* ignore */ }
      }
      if (commandRecognitionRef.current) {
        try { commandRecognitionRef.current.abort(); } catch { /* ignore */ }
      }
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
        window.speechSynthesis.cancel();
      }
      clearCommandTimers();
      clearRestartTimer();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ===== TTS 声音异步加载 =====
  useEffect(() => {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;
    const handleVoicesChanged = () => {
      window.speechSynthesis.getVoices();
    };
    window.speechSynthesis.addEventListener('voiceschanged', handleVoicesChanged);
    return () => {
      window.speechSynthesis.removeEventListener('voiceschanged', handleVoicesChanged);
    };
  }, []);

  // ===== 网络状态监听（v3 新增：网络断开自动重连） =====
  useEffect(() => {
    if (typeof window === 'undefined') return;

    /**
     * 网络恢复事件处理：延迟 1s 后恢复唤醒词监听。
     * 延迟是为了等待网络栈完全恢复，避免立即重连又失败。
     */
    const handleOnline = () => {
      log('网络已恢复');
      networkOfflineRef.current = false;
      if (enabledRef.current && !isIdleSleepRef.current) {
        setError(null);
        setErrorType(null);
        log(`网络恢复，延迟 ${ONLINE_RESUME_DELAY_MS}ms 后恢复监听`);
        clearRestartTimer();
        restartTimerRef.current = setTimeout(() => {
          if (enabledRef.current && !isIdleSleepRef.current && !networkOfflineRef.current) {
            setState('listening');
            startWakeListeningRef.current();
          }
        }, ONLINE_RESUME_DELAY_MS);
      }
    };

    /**
     * 网络断开事件处理：设置离线标记，停止当前识别，等待网络恢复。
     */
    const handleOffline = () => {
      log('网络已断开', 'warn');
      networkOfflineRef.current = true;
      setErrorType('network');
      setError('网络连接已断开，语音识别暂停。网络恢复后将自动重连');
      setState('idle');
      clearRestartTimer();
      // 停止当前识别（设置 manualAbort 阻止 onend 重启）
      if (wakeRecognitionRef.current) {
        manualAbortRef.current = true;
        try { wakeRecognitionRef.current.abort(); } catch { /* ignore */ }
      }
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, [clearRestartTimer, log]);

  // ===== 空闲休眠定时检查（v3 新增：5 分钟无唤醒自动休眠） =====
  useEffect(() => {
    if (!enabled) return;
    /**
     * 定时检查是否空闲超时。
     * 每分钟检查一次，如果距离上次唤醒超过 IDLE_TIMEOUT_MS 则进入低功耗模式。
     */
    const checkIdle = setInterval(() => {
      if (!enabledRef.current || isIdleSleepRef.current) return;
      const elapsed = Date.now() - lastWakeTimeRef.current;
      if (elapsed >= IDLE_TIMEOUT_MS) {
        log(`空闲超时（${Math.round(elapsed / 1000)}s 未唤醒），进入低功耗模式`, 'warn');
        enterIdleSleepRef.current();
      }
    }, IDLE_CHECK_INTERVAL_MS);
    return () => clearInterval(checkIdle);
  }, [enabled, log]);

  return {
    state,
    enabled,
    supported,
    transcript,
    error,
    errorType,
    enable,
    disable,
    toggle,
    startCommand,
    cancelCommand,
    speak,
    stopSpeaking,
    skipCurrent,
    resumeListening,
  };
}
