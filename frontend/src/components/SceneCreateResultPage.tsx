import { useState, useEffect, useRef } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  CheckCircle2,
  Plus,
  RefreshCw,
  Power,
  Info,
  ChevronDown,
  Bell,
  Star,
  Clock,
} from 'lucide-react';
import type { RoutePageProps } from './DynamicPage';
import { createScene, getSceneList, type CreateSceneRequest, type SceneItem } from '../api/client';
import SceneListPage from './SceneListPage';
import {
  useSpkSchema,
  AttributeControl,
  type SpkAttribute,
} from '../shared/spk-schema';

// ===== 设备属性表单（基于 SPK schema 动态渲染可写属性） =====

/** 场景创建表单的设备数据 */
interface SceneDevice {
  deviceId: string;
  deviceName: string;
  spk: string;
  gatewayId?: string;
  online: boolean;
  sid?: string;
}

/**
 * 场景创建 - 设备属性表单
 *
 * 复用 AttributeControl 组件，但 onControl/onColorPick 回调写入本地表单状态而非调用控制 API。
 * 属性值存储在父组件的 attrs（Record<attrKey, attrValue>）中，提交时统一打包为 functions[].status。
 */
function SceneDeviceAttrForm({
  spk, attrs, onAttrChange, onColorPick,
}: {
  spk: string;
  attrs: Record<string, string>;
  onAttrChange: (key: string, value: string) => void;
  onColorPick: (attr: SpkAttribute, rgb: [number, number, number]) => void;
}) {
  const schema = useSpkSchema(spk);
  if (!schema) {
    return (
      <div className="text-xs text-slate-400 flex items-center gap-1.5">
        <RefreshCw size={12} className="animate-spin text-neon-purple" />
        加载设备物模型...
      </div>
    );
  }
  const writableAttrs = schema.attributes.filter(a => a.access === 'WR' || a.access === 'W');
  if (writableAttrs.length === 0) {
    return <div className="text-xs text-slate-400">该设备无可配置属性</div>;
  }
  return (
    <div className="space-y-2.5">
      {writableAttrs.map(attr => (
        <AttributeControl
          key={attr.key}
          attr={attr}
          value={attrs[attr.key]}
          loading={false}
          onControl={onAttrChange}
          onColorPick={onColorPick}
        />
      ))}
    </div>
  );
}

// ===== 单个设备的属性配置行 =====

/**
 * 场景创建 - 单个设备的属性配置行
 *
 * 点击整行即可选中/取消选中（不需要精确点复选框），选中后展开属性配置控件 + 延迟秒数输入框。
 * 设备无 SID 时仍可选择，提交时用 deviceId 作为 sid 回退。
 */
function SceneDeviceForm({
  device, selected, onToggle, attrs, delay,
  onAttrChange, onColorPick, onDelayChange,
}: {
  device: SceneDevice;
  selected: boolean;
  onToggle: () => void;
  attrs: Record<string, string>;
  delay: number;
  onAttrChange: (key: string, value: string) => void;
  onColorPick: (attr: SpkAttribute, rgb: [number, number, number]) => void;
  onDelayChange: (sec: number) => void;
}) {
  return (
    <div
      className={`rounded-lg border p-2.5 transition-all cursor-pointer ${
        selected
          ? 'border-neon-purple/50 bg-neon-purple/5'
          : 'border-neon-purple/15 glass-panel hover:border-neon-purple/30'
      }`}
      onClick={onToggle}
    >
      {/* 设备选择行（点击整行切换选中） */}
      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={selected}
          onChange={onToggle}
          onClick={(e) => e.stopPropagation()}
          className="w-4 h-4 rounded accent-neon-purple pointer-events-none"
          tabIndex={-1}
        />
        {/* 设备名 + deviceId（小字灰色，参考 DeviceListPage 展示方式） */}
        <div className="flex-1 min-w-0">
          <span className={`text-sm block truncate ${selected ? 'text-slate-100 font-medium' : 'text-slate-300'}`}>
            {device.deviceName || '未命名设备'}
          </span>
          <span className="text-[10px] text-slate-500 font-mono truncate block">
            ID: {device.deviceId}
          </span>
        </div>
        {device.spk && (
          <span className="text-[10px] text-neon-purple font-mono px-1.5 py-0.5 rounded bg-neon-purple/10 border border-neon-purple/20 flex-shrink-0">
            {device.spk}
          </span>
        )}
        <span className={`flex-shrink-0 text-[10px] font-mono ${device.online ? 'text-neon-green' : 'text-slate-500'}`}>
          {device.online ? '在线' : '离线'}
        </span>
        <ChevronDown
          size={14}
          className={`text-slate-400 flex-shrink-0 transition-transform ${selected ? 'rotate-180' : ''}`}
        />
      </div>

      {/* 选中后展开：属性配置 + 延迟秒数 */}
      <AnimatePresence>
        {selected && (
          <motion.div
            key="expand"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            className="overflow-hidden"
          >
            <div className="mt-2.5 pt-2.5 border-t border-neon-purple/15 space-y-2.5" onClick={(e) => e.stopPropagation()}>
              <SceneDeviceAttrForm
                spk={device.spk}
                attrs={attrs}
                onAttrChange={onAttrChange}
                onColorPick={onColorPick}
              />
              {/* 延迟秒数输入 */}
              <div className="flex items-center gap-1.5">
                <label className="text-xs text-slate-400 flex-shrink-0">延迟秒数</label>
                <input
                  type="number"
                  min={0}
                  max={86400}
                  value={delay}
                  onChange={(e) => {
                    const v = parseInt(e.target.value, 10);
                    onDelayChange(isNaN(v) || v < 0 ? 0 : v);
                  }}
                  className="w-20 px-2 py-1 text-xs glass-panel border border-neon-purple/15 rounded-md focus:outline-none focus:border-neon-purple/50 text-slate-200 font-mono"
                />
                <span className="text-[10px] text-slate-500">秒（0=立即执行）</span>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ===== 主组件：场景创建表单页 =====

/**
 * 场景创建表单页
 *
 * 后端返回 routePath=scene-create + 表单数据（sceneName + 设备列表 + gatewayId）后，
 * 前端渲染表单供用户：
 *  1. 输入/确认场景名称
 *  2. 点击设备行选中/取消（可多选），选中后展开属性配置
 *  3. 为每个选中设备配置要设置的属性值（基于 SPK schema 动态渲染可写属性控件）
 *  4. 为每个设备动作设置延迟秒数（0=立即执行）
 *  5. 设置场景级延时、是否收藏、是否推送通知
 *  6. 提交调用 createScene REST API 创建场景
 *  7. 创建成功后调用 getSceneList() 获取最新场景列表，渲染 SceneListPage
 */
export default function SceneCreateResultPage({ data }: RoutePageProps) {
  const payload = data as unknown as {
    sceneName?: string;
    devices?: SceneDevice[];
    gatewayId?: string;
    /** 后端 LLM 预选的设备 ID 列表 */
    selectedDeviceIds?: string[];
    /** 后端 LLM 预填的设备属性：{ deviceId -> { attrKey: attrValue } } */
    prefillAttrs?: Record<string, Record<string, string>>;
    /** 后端 LLM 预设的场景级配置 */
    collect?: boolean;
    delaySeconds?: number;
    executePush?: boolean;
  };
  const devices = payload.devices ?? [];
  const initialSceneName = payload.sceneName ?? '';

  // ===== 表单状态（从后端预填数据初始化） =====
  /** 场景名称 */
  const [sceneName, setSceneName] = useState(initialSceneName);
  /** 已选中的设备 ID 集合（初始化为后端预选的设备） */
  const [selectedIds, setSelectedIds] = useState<Set<string>>(
    new Set(payload.selectedDeviceIds ?? [])
  );
  /** 每个设备的属性表单值：deviceId -> { attrKey: attrValue }（初始化为后端预填属性） */
  const [deviceAttrs, setDeviceAttrs] = useState<Record<string, Record<string, string>>>(
    payload.prefillAttrs ?? {}
  );
  /** 每个设备的延迟秒数：deviceId -> 秒 */
  const [deviceDelays, setDeviceDelays] = useState<Record<string, number>>({});
  /** 场景级延时秒数（0=不设置延时，初始化为后端预设值） */
  const [sceneDelay, setSceneDelay] = useState(payload.delaySeconds ?? 0);
  /** 是否展示在首页常用（collect，初始化为后端预设值） */
  const [collect, setCollect] = useState(payload.collect ?? false);
  /** 场景执行后是否推送结果通知（executePush，初始化为后端预设值） */
  const [executePush, setExecutePush] = useState(payload.executePush ?? false);

  // ===== 提交状态 =====
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | undefined>();
  /** 创建成功后获取的场景列表（用于渲染 SceneListPage） */
  const [sceneList, setSceneList] = useState<SceneItem[] | null>(null);
  /** 创建后加载场景列表的 loading 状态 */
  const [loadingScenes, setLoadingScenes] = useState(false);

  // ===== 倒计时自动保存状态 =====
  /** 倒计时剩余秒数，null 表示未启动倒计时 */
  const [countdown, setCountdown] = useState<number | null>(null);
  const COUNTDOWN_SECONDS = 20;

  // 保存 handleSubmit 的最新引用到 ref，让事件监听器能调用最新的表单状态
  // 原因：handleSubmit 闭包依赖 sceneName/selectedIds/deviceAttrs 等状态，直接在 useEffect
  //       中调用会闭包过期（拿到初始空值）。用 ref 中转保证事件触发时调用最新闭包。
  const handleSubmitRef = useRef<() => void>(() => {});

  // 监听 App.tsx 派发的 'hdl-scene-create-submit' 事件
  // 用户在场景创建表单展示时说"保存"/"提交"等关键词，App.tsx 拦截后派发此事件，
  // 这里调用 handleSubmitRef.current() 触发表单提交，直接调用 createScene REST API
  useEffect(() => {
    const onSubmit = () => handleSubmitRef.current();
    window.addEventListener('hdl-scene-create-submit', onSubmit);
    return () => window.removeEventListener('hdl-scene-create-submit', onSubmit);
  }, []);

  // 倒计时：每秒递减，到 0 时自动调用 handleSubmit 保存场景
  useEffect(() => {
    if (countdown === null) return;
    if (countdown <= 0) {
      setCountdown(null);
      handleSubmitRef.current();
      return;
    }
    const timer = setTimeout(() => {
      setCountdown(prev => (prev === null ? null : prev - 1));
    }, 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  /** 切换设备选中状态，选中时初始化该设备的属性表单和延迟默认值 */
  const toggleDevice = (deviceId: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(deviceId)) {
        next.delete(deviceId);
      } else {
        next.add(deviceId);
      }
      return next;
    });
    // 选中时初始化默认值（避免后续读取 undefined）
    if (!selectedIds.has(deviceId)) {
      setDeviceAttrs(prev => (prev[deviceId] ? prev : { ...prev, [deviceId]: {} }));
      setDeviceDelays(prev => (prev[deviceId] != null ? prev : { ...prev, [deviceId]: 0 }));
    }
  };

  /** 更新某个设备的某个属性值 */
  const handleAttrChange = (deviceId: string, key: string, value: string) => {
    setDeviceAttrs(prev => ({
      ...prev,
      [deviceId]: { ...(prev[deviceId] || {}), [key]: value },
    }));
  };

  /** 颜色选择回调：将 RGB 重组为 "r,g,b" / "r,g,b,w" / "r,g,b,c,w" 格式并存入表单 */
  const handleColorPick = (deviceId: string, attr: SpkAttribute, rgb: [number, number, number]) => {
    const [r, g, b] = rgb;
    const format = attr.format || 'r,g,b';
    const channelCount = format.split(',').length;
    // 解析当前值，保留 w/c 通道
    const currentVal = deviceAttrs[deviceId]?.[attr.key] || '';
    const parts = currentVal.split(',').map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n));
    const w = parts[3] ?? 0;
    const c = parts[3] ?? 0;
    const w2 = parts[4] ?? 0;
    let value: string;
    if (channelCount >= 5) {
      value = `${r},${g},${b},${c},${w2}`;
    } else if (channelCount === 4) {
      value = `${r},${g},${b},${w}`;
    } else {
      value = `${r},${g},${b}`;
    }
    handleAttrChange(deviceId, attr.key, value);
  };

  /** 提交创建场景 */
  const handleSubmit = async () => {
    if (submitting) return;
    // 表单校验
    if (!sceneName.trim()) {
      setSubmitError('请输入场景名称');
      return;
    }
    if (selectedIds.size === 0) {
      setSubmitError('请至少选择一个设备');
      return;
    }
    // 构建 functions 数组
    const selectedDevices = devices.filter(d => selectedIds.has(d.deviceId));
    const functions = selectedDevices.map(d => {
      const attrs = deviceAttrs[d.deviceId] || {};
      const status = Object.entries(attrs).map(([key, value]) => ({ key, value: String(value) }));
      return {
        sid: d.sid || d.deviceId,
        delaySeconds: deviceDelays[d.deviceId] || 0,
        status,
      };
    });
    // 校验：每个设备的 status 不能为空
    const invalidFn = functions.find(f => !f.sid || f.status.length === 0);
    if (invalidFn) {
      setSubmitError('每个选中设备至少需要设置一个属性');
      return;
    }
    // 从第一个选中设备获取 gatewayId（HDL 要求 scenes[].gatewayId 必填）
    const gatewayId = selectedDevices[0]?.gatewayId;
    setSubmitting(true);
    setSubmitError(undefined);
    try {
      const req: CreateSceneRequest = {
        sceneName: sceneName.trim(),
        functions,
        collect,
        delaySeconds: sceneDelay > 0 ? sceneDelay : undefined,
        executePush,
        gatewayId,
      } as CreateSceneRequest;
      const res = await createScene(req);
      if (res.success && res.data) {
        // 创建成功后调用 getSceneList() 获取最新场景列表，渲染 SceneListPage
        setLoadingScenes(true);
        try {
          const listRes = await getSceneList();
          if (listRes.success && listRes.data) {
            setSceneList(listRes.data.scenes ?? []);
          } else {
            // 获取列表失败时仍展示空列表，避免阻塞
            setSceneList([]);
          }
        } catch (e) {
          // 列表获取失败兜底：展示空列表
          setSceneList([]);
          console.warn('[SceneCreateResultPage] 获取场景列表失败:', e);
        } finally {
          setLoadingScenes(false);
        }
      } else {
        setSubmitError(res.message || '创建场景失败');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setSubmitError(`创建场景失败：${msg}`);
    } finally {
      setSubmitting(false);
    }
  };

  /** 点击"创建场景"按钮：启动 20 秒倒计时，不立即保存 */
  const handleStartCountdown = () => {
    if (submitting || countdown !== null) return;
    // 表单校验（提前反馈，避免倒计时结束后才发现错误）
    if (!sceneName.trim()) {
      setSubmitError('请输入场景名称');
      return;
    }
    if (selectedIds.size === 0) {
      setSubmitError('请至少选择一个设备');
      return;
    }
    setSubmitError(undefined);
    setCountdown(COUNTDOWN_SECONDS);
  };

  /** 取消倒计时：中止自动保存并提示用户已取消 */
  const handleCancelCountdown = () => {
    setCountdown(null);
    setSubmitError('已取消场景保存');
  };

  // 同步 handleSubmit 到 ref，让 'hdl-scene-create-submit' 事件监听器
  // 能调用到最新的 handleSubmit 闭包（依赖 sceneName/selectedIds/deviceAttrs 等状态）
  useEffect(() => {
    handleSubmitRef.current = handleSubmit;
  });

  // ===== 创建成功：渲染场景列表（调用 getSceneList 获取数据） =====
  if (sceneList) {
    return (
      <div className="animate-fade-in space-y-3">
        {/* 创建成功提示条 */}
        <div className="flex items-center gap-2 text-xs text-neon-green bg-neon-green/5 border border-neon-green/30 rounded-lg px-3 py-2">
          <CheckCircle2 size={14} />
          <span>场景「{sceneName}」创建成功，已加载最新场景列表</span>
        </div>
        {/* 加载中提示 */}
        {loadingScenes && (
          <div className="flex items-center gap-1.5 text-xs text-slate-400">
            <RefreshCw size={12} className="animate-spin text-neon-purple" />
            正在加载场景列表...
          </div>
        )}
        <SceneListPage data={{}} scenes={sceneList} />
      </div>
    );
  }

  // ===== 场景创建表单 =====
  return (
    <div className="animate-fade-in space-y-4">
      {/* 标题 */}
      <div className="flex items-center gap-2 text-sm">
        <Plus size={16} className="neon-text-purple" />
        <span className="gradient-text font-semibold">创建场景</span>
      </div>

      {/* 表单卡片 */}
      <div className="glass-strong rounded-xl p-4 card-shadow space-y-4">
        {/* 场景名称 */}
        <div>
          <label className="text-xs text-slate-400 mb-1.5 block font-mono">场景名称</label>
          <input
            type="text"
            value={sceneName}
            onChange={(e) => setSceneName(e.target.value)}
            placeholder="如：回家场景"
            className="w-full px-3 py-2 text-sm glass-panel border border-neon-purple/15 rounded-md focus:outline-none focus:border-neon-purple/50 text-slate-100 placeholder-slate-500"
          />
        </div>

        {/* 场景级配置：延时 + 收藏 + 推送 */}
        <div className="rounded-lg glass-panel border border-neon-purple/15 p-3 space-y-2.5">
          <div className="text-xs text-slate-300 font-medium flex items-center gap-1.5">
            <Power size={12} className="text-neon-purple" />
            场景选项
          </div>
          {/* 场景级延时 */}
          <div className="flex items-center gap-2">
            <Clock size={12} className="text-slate-400 flex-shrink-0" />
            <label className="text-xs text-slate-400 flex-shrink-0">场景延时（秒）</label>
            <input
              type="number"
              min={0}
              max={86400}
              value={sceneDelay}
              onChange={(e) => {
                const v = parseInt(e.target.value, 10);
                setSceneDelay(isNaN(v) || v < 0 ? 0 : v);
              }}
              className="w-20 px-2 py-1 text-xs glass-panel border border-neon-purple/15 rounded-md focus:outline-none focus:border-neon-purple/50 text-slate-200 font-mono"
            />
            <span className="text-[10px] text-slate-500">0=不延时</span>
          </div>
          {/* 是否收藏 */}
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={collect}
              onChange={(e) => setCollect(e.target.checked)}
              className="w-4 h-4 rounded accent-neon-purple"
            />
            <Star size={12} className="text-slate-400" />
            <span className="text-xs text-slate-300">展示在首页常用</span>
          </label>
          {/* 是否推送通知 */}
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={executePush}
              onChange={(e) => setExecutePush(e.target.checked)}
              className="w-4 h-4 rounded accent-neon-purple"
            />
            <Bell size={12} className="text-slate-400" />
            <span className="text-xs text-slate-300">执行结果推送通知</span>
          </label>
        </div>

        {/* 设备列表（多选 + 属性配置） */}
        <div>
          <div className="text-xs text-slate-400 mb-2 font-mono flex items-center gap-1.5">
            <Info size={12} className="text-neon-cyan" />
            <span>点击设备选择并配置动作（已选 {selectedIds.size} 个）</span>
          </div>
          {devices.length === 0 ? (
            <div className="text-xs text-slate-500 py-4 text-center glass-panel rounded-lg border border-neon-purple/15">
              当前房屋暂无设备
            </div>
          ) : (
            <div className="space-y-2">
              {devices.map(device => (
                <SceneDeviceForm
                  key={device.deviceId}
                  device={device}
                  selected={selectedIds.has(device.deviceId)}
                  onToggle={() => toggleDevice(device.deviceId)}
                  attrs={deviceAttrs[device.deviceId] || {}}
                  delay={deviceDelays[device.deviceId] || 0}
                  onAttrChange={(key, value) => handleAttrChange(device.deviceId, key, value)}
                  onColorPick={(attr, rgb) => handleColorPick(device.deviceId, attr, rgb)}
                  onDelayChange={(sec) => setDeviceDelays(prev => ({ ...prev, [device.deviceId]: sec }))}
                />
              ))}
            </div>
          )}
        </div>

        {/* 错误提示 */}
        {submitError && (
          <div className="text-xs text-red-400 bg-red-500/10 border border-red-500/30 rounded px-2.5 py-1.5">
            {submitError}
          </div>
        )}

        {/* 提交按钮区域：20 秒倒计时自动保存 + 取消按钮 */}
        {countdown !== null ? (
          <div className="space-y-2">
            {/* 倒计时提示 + 取消按钮 */}
            <div className="flex items-center justify-between gap-2 rounded-lg glass-panel border border-neon-cyan/30 px-3 py-2">
              <div className="flex items-center gap-1.5 text-xs text-slate-300">
                <Clock size={12} className="text-neon-cyan animate-pulse" />
                <span>
                  <span className="text-neon-cyan font-mono font-bold text-base">{countdown}</span>
                  <span className="ml-1">秒后自动保存</span>
                </span>
              </div>
              <button
                onClick={handleCancelCountdown}
                className="px-3 py-1 text-xs text-red-400 border border-red-500/60 rounded-md hover:bg-red-500/15 hover:text-red-300 transition-colors font-medium"
              >
                取消
              </button>
            </div>
            {/* 禁用的保存按钮（倒计时中不可点击） */}
            <button
              disabled
              className="shimmer-btn w-full rounded-lg bg-gradient-to-r from-neon-purple to-neon-violet px-3 py-2.5 text-sm font-medium text-white shadow-neon-purple opacity-40 cursor-not-allowed flex items-center justify-center gap-1.5"
            >
              <Clock size={14} className="text-neon-cyan" />
              等待自动保存...
            </button>
          </div>
        ) : (
          <button
            onClick={handleStartCountdown}
            disabled={submitting || !sceneName.trim() || selectedIds.size === 0}
            className="shimmer-btn w-full rounded-lg bg-gradient-to-r from-neon-purple to-neon-violet px-3 py-2.5 text-sm font-medium text-white shadow-neon-purple transition-all flex items-center justify-center gap-1.5 disabled:opacity-40 disabled:cursor-not-allowed hover:shadow-glow-md"
          >
            {submitting ? <RefreshCw size={14} className="animate-spin" /> : <Plus size={14} />}
            {submitting ? '创建中...' : '创建场景'}
          </button>
        )}
      </div>
    </div>
  );
}
