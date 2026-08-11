import { useCallback, useEffect, useRef, useState } from 'react';
import { sendChat, type SseCallbacks, type ImageInfo } from '../api/client';

/**
 * SSE 连接管理 Hook
 *
 * 封装 sendChat 的取消控制与 sending 状态：
 *  - send(message, history)：发起一轮 SSE 对话（自动取消上一轮未完成请求）
 *  - abort()：手动中断当前请求
 *  - sending：是否正在流式接收中
 *
 * @param callbacks SSE 事件回调（用 ref 保存最新值，避免闭包过期）
 */
export function useSSE(callbacks: SseCallbacks) {
  const [sending, setSending] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  /** 调用计数器：防止旧 send 的 finally 覆盖新 send 的状态 */
  const callIdRef = useRef(0);

  // 用 ref 保存最新回调，避免每次渲染重建 send 闭包
  const callbacksRef = useRef(callbacks);
  useEffect(() => {
    callbacksRef.current = callbacks;
  }, [callbacks]);

  /** 发送消息（支持多模态图片 + 房屋 ID） */
  const send = useCallback(
    async (message: string, history: Array<{ role: string; content: string }>, images?: ImageInfo[], houseId?: string) => {
      // 取消上一个未完成的 SSE 请求
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      // 递增调用 ID，标记当前是最新调用
      const callId = ++callIdRef.current;

      setSending(true);
      try {
        await sendChat(message, history, callbacksRef.current, controller.signal, images, houseId);
      } finally {
        // 只有当前调用仍是最新时，才重置状态（防止旧 finally 覆盖新调用）
        if (callIdRef.current === callId) {
          setSending(false);
          abortRef.current = null;
        }
      }
    },
    [],
  );

  /** 中断当前请求 */
  const abort = useCallback(() => {
    // 递增 callId，使旧 send 的 finally 不会覆盖 abort 的重置
    callIdRef.current++;
    abortRef.current?.abort();
    abortRef.current = null;
    setSending(false);
  }, []);

  // 卸载时清理未完成的请求
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  return { send, abort, sending, callbacksRef };
}
