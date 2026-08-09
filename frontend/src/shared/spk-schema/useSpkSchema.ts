/**
 * @file SPK Schema 加载 Hook
 *
 * 提供 spk-schemas.json 的全局缓存加载与按 spk 查询 schema 的 React Hook。
 *
 * 设计要点：
 *  - schemaCache / schemaPromise 为模块级单例，整个应用只 fetch 一次，
 *    多个组件同时调用 useSpkSchema 共享同一份缓存，避免重复网络请求。
 *  - loadSpkSchemas 失败时返回 null，调用方降级为不展示物模型控制面板。
 *  - useSpkSchema 在 spk 未命中时回退到 _fallback.attributes，保证未知设备也有兜底控件。
 *
 * 来源：从 DeviceStatusPage.tsx / SceneCreateResultPage.tsx 中提取（两处实现完全一致）。
 */

import { useState, useEffect } from 'react';
import type { SpkSchema, SpkSchemaFile } from './types';

/**
 * 模块级 Schema 缓存：整个应用生命周期只 fetch 一次。
 * 首次 fetch 成功后写入，后续调用直接命中缓存。
 */
let schemaCache: SpkSchemaFile | null = null;

/**
 * 模块级进行中的 fetch Promise：避免并发场景下重复发起请求。
 * 多个组件同时 mount 时复用同一个 Promise，请求完成后所有组件同时拿到结果。
 */
let schemaPromise: Promise<SpkSchemaFile | null> | null = null;

/**
 * 异步加载 SPK Schema（带模块级缓存）。
 *
 * 加载流程：
 *  1. 命中 schemaCache：直接返回 resolved Promise
 *  2. 命中 schemaPromise：返回进行中的 Promise（避免重复请求）
 *  3. 否则发起 fetch，成功后写入 schemaCache，失败返回 null
 *
 * @returns 加载完成的 Schema 文件；失败时返回 null 以便调用方降级
 */
export function loadSpkSchemas(): Promise<SpkSchemaFile | null> {
  if (schemaCache) return Promise.resolve(schemaCache);
  if (schemaPromise) return schemaPromise;
  const base = import.meta.env.BASE_URL || '/';
  schemaPromise = fetch(`${base}spk-schemas.json`)
    .then(r => r.json() as Promise<SpkSchemaFile>)
    .then(data => {
      schemaCache = data;
      return data;
    })
    .catch(() => null);
  return schemaPromise;
}

/**
 * Hook：根据 spk 加载对应 Schema，未命中时回退到 _fallback。
 *
 * 使用方式：
 * ```ts
 * const schema = useSpkSchema(device.spk);
 * if (!schema) return <Loading />;
 * ```
 *
 * 行为说明：
 *  - spk 命中 schemas 时返回对应 SpkSchema
 *  - spk 未命中但存在 _fallback 时，返回 { name: spk, aliases: [], attributes: _fallback.attributes }
 *  - 加载中或加载失败时返回 null，由调用方决定降级 UI
 *
 * @param spk 设备类型编码（如 'light.rgb' / 'hvac.ac'）
 * @returns 匹配到的 Schema；加载中或失败时返回 null
 */
export function useSpkSchema(spk: string): SpkSchema | null {
  const [schema, setSchema] = useState<SpkSchema | null>(null);

  useEffect(() => {
    let cancelled = false;
    loadSpkSchemas().then(data => {
      if (cancelled || !data) return;
      const s = data.schemas?.[spk];
      if (s) {
        setSchema(s);
      } else if (data._fallback) {
        setSchema({ name: spk, aliases: [], attributes: data._fallback.attributes || [] });
      }
    });
    return () => { cancelled = true; };
  }, [spk]);

  return schema;
}
