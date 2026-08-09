/**
 * 可观测性 SSE Hook
 *
 * 封装 /api/observe/events SSE 订阅：
 *  - 自动连接 / 断开重连
 *  - 维护实时事件列表（最近 N 条）
 *  - 暴露连接状态（connected）
 *  - 组件卸载时自动关闭连接
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  subscribeObserveEvents,
  type ObserveEvent,
  type ObserveSseCallbacks,
} from '../api/observe';

/** 实时事件缓冲区大小（仅保留最近 N 条用于展示） */
const MAX_EVENT_BUFFER_SIZE = 100;

/** 重连间隔（毫秒），断开后 3 秒自动重连 */
const RECONNECT_INTERVAL_MS = 3000;

/**
 * 可观测性 SSE Hook
 *
 * @returns
 *   - events: 最近收到的事件列表（最新在前）
 *   - connected: SSE 连接状态
 *   - clearEvents: 清空本地事件缓冲
 */
export function useObserveSSE(autoConnect: boolean = true) {
  /** 实时事件列表（最新在前） */
  const [events, setEvents] = useState<ObserveEvent[]>([]);
  /** 连接状态 */
  const [connected, setConnected] = useState(false);

  /** EventSource 实例引用 */
  const esRef = useRef<EventSource | null>(null);
  /** 重连定时器引用 */
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** 是否已主动断开（用于阻止自动重连） */
  const manualCloseRef = useRef(false);

  /** 添加新事件到缓冲区头部 */
  const appendEvent = useCallback((event: ObserveEvent) => {
    setEvents((prev) => {
      // 新事件加到头部，超过容量时丢弃尾部
      const next = [event, ...prev];
      if (next.length > MAX_EVENT_BUFFER_SIZE) {
        next.length = MAX_EVENT_BUFFER_SIZE;
      }
      return next;
    });
  }, []);

  /** 批量添加初始快照事件 */
  const appendSnapshot = useCallback((snapshot: ObserveEvent[]) => {
    // 快照按时间倒序（最新在前）直接设置
    setEvents(snapshot.slice(0, MAX_EVENT_BUFFER_SIZE));
  }, []);

  /** 建立 SSE 连接 */
  const connect = useCallback(() => {
    // 已连接则跳过
    if (esRef.current) {
      return;
    }
    manualCloseRef.current = false;

    const callbacks: ObserveSseCallbacks = {
      onOpen: () => {
        setConnected(true);
      },
      onSnapshot: (events) => {
        appendSnapshot(events);
      },
      onEvent: (event) => {
        appendEvent(event);
      },
      onError: () => {
        setConnected(false);
        // 关闭旧连接
        esRef.current?.close();
        esRef.current = null;
        // 自动重连（除非已主动断开）
        if (!manualCloseRef.current) {
          if (reconnectTimerRef.current) {
            clearTimeout(reconnectTimerRef.current);
          }
          reconnectTimerRef.current = setTimeout(() => {
            connect();
          }, RECONNECT_INTERVAL_MS);
        }
      },
    };

    esRef.current = subscribeObserveEvents(callbacks);
  }, [appendEvent, appendSnapshot]);

  /** 主动断开 SSE 连接 */
  const disconnect = useCallback(() => {
    manualCloseRef.current = true;
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    esRef.current?.close();
    esRef.current = null;
    setConnected(false);
  }, []);

  /** 清空本地事件缓冲 */
  const clearEvents = useCallback(() => {
    setEvents([]);
  }, []);

  // 自动连接（默认开启）
  useEffect(() => {
    if (autoConnect) {
      connect();
    }
    // 卸载时断开
    return () => {
      disconnect();
    };
  }, [autoConnect, connect, disconnect]);

  return {
    events,
    connected,
    clearEvents,
    connect,
    disconnect,
  };
}
