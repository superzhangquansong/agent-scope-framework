import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';

// ===== 设备图标颜色常量（消除魔法值，统一管理品牌色与状态色）=====

/** 设备图标核心色板 */
const DEVICE_COLORS = {
  /** 主品牌紫（开状态主色） */
  PURPLE: '#a855f7',
  /** 紫色深色变体 */
  PURPLE_DARK: '#6b21a8',
  /** 紫色浅色变体 */
  PURPLE_LIGHT: '#c084fc',
  /** 紫色 600 */
  PURPLE_600: '#7c3aed',
  /** 紫色 900 */
  PURPLE_900: '#581c87',
  /** 紫色 950 */
  PURPLE_950: '#3b0764',
  /** 青蓝（科幻主色，开关开启时的亮色，符合设计规范 #00D4FF） */
  CYAN: '#00D4FF',
  /** 深紫（渐变末端色，符合设计规范 #7B2FBE） */
  GRADIENT_PURPLE: '#7B2FBE',
  /** 靛蓝深色（音乐内圈） */
  INDIGO_DARK: '#1e1b4b',
  /** 浅紫（模式标签） */
  VIOLET: '#a78bfa',
  /** 石板色边框（关状态边框） */
  SLATE_BORDER: '#334155',
  /** 石板色静音（次要文字/边框） */
  SLATE_MUTED: '#475569',
  /** 石板色深色（深色背景） */
  SLATE_DARK: '#0f172a',
  /** 石板色深色 2（渐变背景） */
  SLATE_DARK2: '#1e293b',
  /** 红色（地暖/关闭指示） */
  RED: '#ef4444',
  /** 琥珀色（插座/温度标签） */
  AMBER: '#f59e0b',
  /** 绿色（安防/位置标签/开启指示） */
  GREEN: '#10b981',
  /** 绿色深色 */
  GREEN_DARK: '#059669',
  /** 金色（灯光回退色） */
  GOLD: '#faad14',
  /** 金色深色（灯光渐变） */
  GOLD_DARK: '#d48806',
  /** 浅色文字 */
  TEXT_LIGHT: '#e2e8f0',
} as const;

/** 常用 rgba 预设（阴影/光晕效果，供模板字符串引用） */
export const COLOR_PRESETS = {
  /** 紫色光晕（透明度 0.3） */
  PURPLE_GLOW: 'rgba(168,85,247,0.3)',
  /** 紫色光晕（透明度 0.4） */
  PURPLE_GLOW_STRONG: 'rgba(168,85,247,0.4)',
  /** 红色光晕（透明度 0.3） */
  RED_GLOW: 'rgba(239,68,68,0.3)',
  /** 琥珀光晕（透明度 0.3） */
  AMBER_GLOW: 'rgba(245,158,11,0.3)',
  /** 绿色光晕（透明度 0.3） */
  GREEN_GLOW: 'rgba(16,185,129,0.3)',
  /** 深色阴影（透明度 0.5） */
  SHADOW_DARK: 'rgba(0,0,0,0.5)',
} as const;


interface Props {
  spk: string;
  isOn: boolean;
  brightness?: number;
  temperature?: string;
  percent?: number;
  mode?: string;
  color?: [number, number, number];
  channels?: Record<string, number>;
  /** 空调风速（hvac.ac 的 fan_speed 属性，如 high/middle/low/auto） */
  fanSpeed?: string;
  /** 色温值（light.cct 设备的 cct 属性，0-65535K，用于底部状态栏展示） */
  cct?: number;
}

type DeviceProps = Omit<Props, 'spk'>;

const rgbStr = (color?: [number, number, number], fallback = DEVICE_COLORS.GOLD): string => {
  if (!color) return fallback;
  return `rgb(${color[0]},${color[1]},${color[2]})`;
};

const rgbAlpha = (color: [number, number, number], alpha: number): string => {
  return `rgba(${color[0]},${color[1]},${color[2]},${alpha})`;
};

// 内部简化版 SPK 分类规则（移植自 hdl-llm-platform helpers.ts）
const SPK_CATEGORY_RULES: Array<{ pattern: string; category: string }> = [
  { pattern: 'light', category: 'light' },
  { pattern: 'curtain', category: 'curtain' },
  { pattern: 'shades', category: 'curtain' },
  { pattern: 'hvac.ac', category: 'hvac' },
  { pattern: 'acst', category: 'hvac' },
  { pattern: 'hvac.air', category: 'hvac' },
  { pattern: 'airFresh', category: 'hvac' },
  { pattern: 'hvac.floorHeat', category: 'hvac' },
  { pattern: 'hvac.fan', category: 'hvac' },
  { pattern: 'hvac', category: 'hvac' },
  { pattern: 'ir.ac', category: 'ir' },
  { pattern: 'ir.tv', category: 'ir' },
  { pattern: 'ir', category: 'ir' },
  { pattern: 'security', category: 'security' },
  { pattern: 'panel.socket', category: 'panel' },
  { pattern: 'electrical.racks', category: 'electrical' },
  { pattern: 'electrical', category: 'electrical' },
  { pattern: 'av.music', category: 'av' },
  { pattern: 'av', category: 'av' },
];

function getSpkCategory(spk: string): string {
  if (!spk) return 'other';
  const rule = SPK_CATEGORY_RULES.find(r => spk.includes(r.pattern));
  return rule ? rule.category : 'other';
}

const LightDevice: React.FC<DeviceProps> = ({ isOn, brightness = 80, color }) => {
  const lightColor = color ? rgbStr(color) : DEVICE_COLORS.GOLD;
  const lightColorAlpha = color ? rgbAlpha(color, 0.4) : 'rgba(250,173,20,0.4)';
  const lightColorAlpha2 = color ? rgbAlpha(color, 0.1) : 'rgba(255,150,0,0.1)';
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 220, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
      <AnimatePresence>
        {isOn && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: brightness / 100, background: `radial-gradient(circle, ${lightColorAlpha} 0%, ${lightColorAlpha2} 40%, transparent 80%)` }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.5 }}
            style={{ position: 'absolute', width: 500, height: 500, top: -150, left: -140, zIndex: 0, pointerEvents: 'none', filter: 'blur(40px)' }}
          />
        )}
      </AnimatePresence>
      <AnimatePresence>
        {isOn && (
          <>
            <motion.div
              animate={{ scale: [1, 1.1 + brightness / 400, 1], opacity: [0.3, 0.6, 0.3] }}
              transition={{ duration: 4, repeat: Infinity }}
              style={{ position: 'absolute', width: 160, height: 160, borderRadius: '50%', background: `radial-gradient(circle, ${lightColor} 0%, transparent 70%)`, filter: 'blur(40px)', zIndex: 1 }}
            />
            <motion.div
              animate={{ scale: [1.2, 1, 1.2], opacity: [0.2, 0.4, 0.2] }}
              transition={{ duration: 3, repeat: Infinity }}
              style={{ position: 'absolute', width: 220, height: 220, borderRadius: '50%', background: `radial-gradient(circle, ${lightColor} 0%, transparent 60%)`, filter: 'blur(60px)', zIndex: 0 }}
            />
          </>
        )}
      </AnimatePresence>
      <motion.div
        animate={{ y: isOn ? [0, -3, 0] : 0 }}
        transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
        style={{ position: 'relative', zIndex: 2, display: 'flex', flexDirection: 'column', alignItems: 'center' }}
      >
        <div style={{ width: 2, height: 30, background: DEVICE_COLORS.SLATE_MUTED, marginBottom: -2 }} />
        <div style={{ width: 22, height: 12, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: '4px 4px 0 0', border: '1px solid #475569' }} />
        <motion.div
          animate={{ scale: isOn ? 1.05 : 1, filter: isOn ? `drop-shadow(0 0 ${brightness / 4}px ${lightColor})` : 'grayscale(0.8)' }}
          transition={{ duration: 0.5 }}
          style={{
            width: 65, height: 80, borderRadius: '50% 50% 45% 45% / 40% 40% 60% 60%',
            background: isOn ? `linear-gradient(135deg, #fff 0%, ${lightColor} ${100 - brightness / 2}%, ${color ? rgbAlpha(color, 1) : DEVICE_COLORS.GOLD_DARK} 100%)` : 'linear-gradient(135deg, #475569, #334155)',
            border: isOn ? `2px solid ${lightColor}` : '2px solid #475569',
            boxShadow: isOn ? 'inset -8px -8px 20px rgba(0,0,0,0.2), inset 8px 8px 20px rgba(255,255,255,0.4)' : 'inset -4px -4px 10px rgba(0,0,0,0.3)',
            position: 'relative', display: 'flex', justifyContent: 'center',
          }}
        >
          {isOn && (
            <div style={{ position: 'relative', width: '100%', height: '100%' }}>
              <motion.div
                animate={{ opacity: [0.6, 1, 0.6], boxShadow: ['0 0 10px #fff', '0 0 20px #fff', '0 0 10px #fff'] }}
                transition={{ duration: 1.5, repeat: Infinity }}
                style={{ position: 'absolute', top: '42%', left: '50%', transform: 'translate(-50%, -50%)', width: 22, height: 10, border: '2px solid #fff', borderRadius: '50% 50% 0 0', borderBottom: 'none' }}
              />
              <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translateX(-50%)', width: 1, height: 22, background: 'rgba(255,255,255,0.8)', boxShadow: '0 0 5px #fff' }} />
            </div>
          )}
        </motion.div>
        <div style={{ width: 32, height: 8, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: '0 0 8px 8px', marginTop: -4, backgroundImage: 'linear-gradient(to right, rgba(0,0,0,0.2), transparent, rgba(0,0,0,0.2))' }} />
      </motion.div>
    </div>
  );
};

const DimmingLightDevice: React.FC<DeviceProps> = ({ isOn, brightness = 80, color }) => {
  const lightColor = color ? rgbStr(color) : DEVICE_COLORS.GOLD;
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 200, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
      <AnimatePresence>
        {isOn && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: brightness / 200 }} exit={{ opacity: 0 }}
            style={{ position: 'absolute', width: 300, height: 300, borderRadius: '50%', background: `radial-gradient(circle, ${lightColor} 0%, transparent 60%)`, filter: 'blur(50px)', zIndex: 0 }}
          />
        )}
      </AnimatePresence>
      <motion.div
        animate={{ y: isOn ? [0, -2, 0] : 0 }}
        transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
        style={{ position: 'relative', zIndex: 2, display: 'flex', flexDirection: 'column', alignItems: 'center' }}
      >
        <div style={{ width: 2, height: 20, background: DEVICE_COLORS.SLATE_MUTED }} />
        <div style={{
          width: 90, height: 50, borderRadius: '45px 45px 0 0',
          background: isOn ? `linear-gradient(180deg, ${lightColor}, rgba(250,173,20,0.3))` : 'linear-gradient(180deg, #475569, #334155)',
          border: isOn ? `1.5px solid ${lightColor}` : '1.5px solid #475569',
          boxShadow: isOn ? `0 0 ${brightness / 3}px ${lightColor}, inset 0 -10px 20px rgba(255,255,255,0.2)` : 'none',
          display: 'flex', justifyContent: 'center', alignItems: 'flex-end', paddingBottom: 5,
        }}>
          {isOn && Array.from({ length: 8 }).map((_, i) => {
            const len = 15 + brightness * 0.2;
            return <div key={i} style={{ position: 'absolute', width: 1.5, height: len, background: lightColor, opacity: 0.5, transform: `rotate(${i * 45}deg)`, transformOrigin: '50% 0', top: 20 }} />;
          })}
        </div>
        <div style={{ width: 60, height: 6, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: '0 0 6px 6px' }} />
      </motion.div>
      {brightness !== undefined && isOn && (
        <div style={{ position: 'absolute', bottom: 5, width: 120, height: 6, background: 'rgba(0,0,0,0.3)', borderRadius: 3, overflow: 'hidden' }}>
          <motion.div animate={{ width: `${brightness}%` }} transition={{ duration: 0.5 }} style={{ height: '100%', background: `linear-gradient(90deg, ${lightColor}, #fff)`, borderRadius: 3 }} />
        </div>
      )}
    </div>
  );
};

const SwitchLightDevice: React.FC<DeviceProps> = ({ isOn }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 180, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <div style={{
      width: 100, height: 130, borderRadius: 16,
      background: 'linear-gradient(145deg, #1e293b, #0f172a)',
      border: '2px solid #334155', boxShadow: 'inset 0 2px 10px rgba(0,0,0,0.5), 0 4px 15px rgba(0,0,0,0.3)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12,
    }}>
      <div style={{ fontSize: 10, fontWeight: 900, color: DEVICE_COLORS.SLATE_MUTED, letterSpacing: 3 }}>POWER</div>
      <motion.div
        animate={{ background: isOn ? 'linear-gradient(135deg, #10b981, #059669)' : 'linear-gradient(135deg, #475569, #334155)', boxShadow: isOn ? '0 0 20px rgba(16,185,129,0.5)' : 'none' }}
        transition={{ duration: 0.3 }}
        style={{ width: 50, height: 26, borderRadius: 13, position: 'relative', cursor: 'pointer' }}
      >
        <motion.div
          animate={{ x: isOn ? 24 : 0 }}
          transition={{ type: 'spring', stiffness: 500, damping: 30 }}
          style={{ width: 22, height: 22, borderRadius: '50%', background: '#fff', position: 'absolute', top: 2, left: 2, boxShadow: '0 2px 4px rgba(0,0,0,0.3)' }}
        />
      </motion.div>
      <motion.div
        animate={{ backgroundColor: isOn ? DEVICE_COLORS.GREEN : DEVICE_COLORS.RED, boxShadow: isOn ? '0 0 10px #10b981' : '0 0 10px #ef4444' }}
        transition={{ duration: 0.3 }}
        style={{ width: 8, height: 8, borderRadius: '50%' }}
      />
    </div>
  </div>
);

const CCTLightDevice: React.FC<DeviceProps> = ({ isOn, brightness = 80, color }) => {
  const cctColor = color ? rgbStr(color) : DEVICE_COLORS.GOLD;
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 220, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
      <AnimatePresence>
        {isOn && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: brightness / 150 }} exit={{ opacity: 0 }}
            style={{ position: 'absolute', width: 350, height: 350, borderRadius: '50%', background: `radial-gradient(circle, ${cctColor} 0%, transparent 60%)`, filter: 'blur(50px)', zIndex: 0 }}
          />
        )}
      </AnimatePresence>
      <motion.div
        animate={{ y: isOn ? [0, -2, 0] : 0 }}
        transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
        style={{ position: 'relative', zIndex: 2, display: 'flex', flexDirection: 'column', alignItems: 'center' }}
      >
        <div style={{ width: 2, height: 25, background: DEVICE_COLORS.SLATE_MUTED }} />
        <div style={{ width: 20, height: 10, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: '4px 4px 0 0' }} />
        <motion.div
          animate={{ filter: isOn ? `drop-shadow(0 0 ${brightness / 5}px ${cctColor})` : 'grayscale(0.8)' }}
          style={{
            width: 55, height: 70, borderRadius: '50% 50% 45% 45% / 40% 40% 60% 60%',
            background: isOn ? `linear-gradient(135deg, #fff 0%, ${cctColor} 100%)` : 'linear-gradient(135deg, #475569, #334155)',
            border: isOn ? `2px solid ${cctColor}` : '2px solid #475569',
            boxShadow: isOn ? `inset -6px -6px 15px rgba(0,0,0,0.15), inset 6px 6px 15px rgba(255,255,255,0.3)` : 'inset -3px -3px 8px rgba(0,0,0,0.2)',
          }}
        />
        <div style={{ width: 28, height: 6, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: '0 0 6px 6px', marginTop: -3 }} />
      </motion.div>
      {isOn && (
        <div style={{ position: 'absolute', bottom: 8, width: 140, height: 8, borderRadius: 4, background: 'linear-gradient(90deg, #ff9329, #ffc87a, #fff4e8, #e0eeff, #c8dbff)', opacity: 0.8, boxShadow: '0 0 10px rgba(255,200,100,0.3)' }} />
      )}
    </div>
  );
};

const RGBLightDevice: React.FC<DeviceProps> = ({ isOn, brightness = 80, color }) => {
  const rgbColor = color ? rgbStr(color) : DEVICE_COLORS.PURPLE;
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 220, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
      <AnimatePresence>
        {isOn && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: 0.6 }} exit={{ opacity: 0 }}
            style={{ position: 'absolute', width: 300, height: 300, borderRadius: '50%', background: `radial-gradient(circle, ${rgbAlpha(color || [168, 85, 247], 0.5)} 0%, transparent 60%)`, filter: 'blur(50px)', zIndex: 0 }}
          />
        )}
      </AnimatePresence>
      <motion.div
        animate={{ y: isOn ? [0, -2, 0] : 0 }}
        transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
        style={{ position: 'relative', zIndex: 2, display: 'flex', flexDirection: 'column', alignItems: 'center' }}
      >
        <div style={{ width: 2, height: 25, background: DEVICE_COLORS.SLATE_MUTED }} />
        <div style={{ width: 20, height: 10, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: '4px 4px 0 0' }} />
        <motion.div
          animate={{ filter: isOn ? `drop-shadow(0 0 ${brightness / 4}px ${rgbColor})` : 'grayscale(0.8)' }}
          style={{
            width: 55, height: 70, borderRadius: '50% 50% 45% 45% / 40% 40% 60% 60%',
            background: isOn ? `linear-gradient(135deg, #fff 0%, ${rgbColor} 100%)` : 'linear-gradient(135deg, #475569, #334155)',
            border: isOn ? `2px solid ${rgbColor}` : '2px solid #475569',
            boxShadow: isOn ? `inset -6px -6px 15px rgba(0,0,0,0.15), inset 6px 6px 15px rgba(255,255,255,0.3)` : 'inset -3px -3px 8px rgba(0,0,0,0.2)',
          }}
        />
        <div style={{ width: 28, height: 6, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: '0 0 6px 6px', marginTop: -3 }} />
      </motion.div>
      {isOn && (
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ duration: 8, repeat: Infinity, ease: 'linear' }}
          style={{ position: 'absolute', bottom: 10, width: 100, height: 100, borderRadius: '50%', background: 'conic-gradient(#ef4444, #f59e0b, #22c55e, #a855f7, #3b82f6, #8b5cf6, #ec4899, #ef4444)', opacity: 0.3, filter: 'blur(2px)' }}
        />
      )}
    </div>
  );
};

const RGBWLightDevice: React.FC<DeviceProps> = ({ isOn, brightness = 80, color }) => {
  const rgbColor = color ? rgbStr(color) : DEVICE_COLORS.PURPLE;
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 200, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
      <AnimatePresence>
        {isOn && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 0.5 }} exit={{ opacity: 0 }}
            style={{ position: 'absolute', width: 280, height: 280, borderRadius: '50%', background: `radial-gradient(circle, ${rgbAlpha(color || [168, 85, 247], 0.4)} 0%, transparent 60%)`, filter: 'blur(40px)', zIndex: 0 }}
          />
        )}
      </AnimatePresence>
      <div style={{
        width: 120, height: 120, borderRadius: '50%',
        background: isOn ? `linear-gradient(135deg, ${rgbColor}, #fff, ${rgbColor})` : 'linear-gradient(135deg, #1e293b, #0f172a)',
        border: isOn ? `3px solid ${rgbColor}` : '3px solid #334155',
        boxShadow: isOn ? `0 0 ${brightness / 2}px ${rgbColor}, inset 0 0 20px rgba(255,255,255,0.2)` : 'inset 0 2px 10px rgba(0,0,0,0.5)',
        display: 'flex', justifyContent: 'center', alignItems: 'center', position: 'relative', zIndex: 2,
      }}>
        {isOn && (
          <motion.div animate={{ rotate: 360 }} transition={{ duration: 10, repeat: Infinity, ease: 'linear' }}
            style={{ width: 80, height: 80, borderRadius: '50%', background: 'conic-gradient(#ef4444, #f59e0b, #22c55e, #a855f7, #3b82f6, #8b5cf6, #ec4899, #ef4444)', opacity: 0.4, filter: 'blur(3px)' }}
          />
        )}
        <div style={{ position: 'absolute', width: 40, height: 40, borderRadius: '50%', background: isOn ? 'rgba(255,255,255,0.9)' : DEVICE_COLORS.SLATE_DARK2, boxShadow: isOn ? '0 0 15px rgba(255,255,255,0.5)' : 'none', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
          <span style={{ fontSize: 10, fontWeight: 900, color: isOn ? rgbColor : DEVICE_COLORS.SLATE_MUTED }}>RGBW</span>
        </div>
      </div>
    </div>
  );
};

const LedStripDevice: React.FC<DeviceProps> = ({ isOn, color, channels }) => {
  const colors = [DEVICE_COLORS.RED, DEVICE_COLORS.AMBER, '#22c55e', DEVICE_COLORS.PURPLE, '#3b82f6', '#8b5cf6', '#ec4899'];
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 260, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center' }}>
      <div style={{
        width: 240, height: 16, borderRadius: 8, position: 'relative', overflow: 'hidden',
        background: DEVICE_COLORS.SLATE_DARK, border: '1px solid #334155', boxShadow: 'inset 0 2px 4px rgba(0,0,0,0.5)',
      }}>
        <AnimatePresence>
          {isOn && (
            <motion.div
              initial={{ x: '-100%' }} animate={{ x: '100%' }}
              transition={{ duration: 5, repeat: Infinity, ease: 'linear' }}
              style={{ position: 'absolute', top: 0, left: 0, width: '200%', height: '100%', background: 'linear-gradient(90deg, #ef4444, #f59e0b, #22c55e, #a855f7, #3b82f6, #8b5cf6, #ec4899, #ef4444)', filter: 'blur(4px)', zIndex: 1, opacity: 0.8 }}
            />
          )}
        </AnimatePresence>
        <div style={{ display: 'flex', justifyContent: 'space-around', width: '100%', height: '100%', alignItems: 'center', padding: '0 8px' }}>
          {Array.from({ length: 15 }).map((_, i) => {
            const beadColor = isOn ? (color ? rgbStr(color) : colors[i % colors.length]) : DEVICE_COLORS.SLATE_DARK2;
            const boxShadow = isOn ? `0 0 12px ${beadColor}` : 'none';
            return (
              <div key={i}
                className={isOn ? 'led-bead-pulse' : undefined}
                style={{ width: 6, height: 6, borderRadius: '50%', zIndex: 2, backgroundColor: beadColor, boxShadow, animationDelay: `${i * 0.1}s` }}
              />
            );
          })}
        </div>
      </div>
      <AnimatePresence>
        {isOn && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: [0.2, 0.4, 0.2] }} exit={{ opacity: 0 }}
            transition={{ duration: 3, repeat: Infinity }}
            style={{ position: 'absolute', bottom: 5, width: 280, height: 40, background: 'radial-gradient(ellipse at center, rgba(255,255,255,0.15) 0%, transparent 70%)', filter: 'blur(20px)', zIndex: 0 }}
          />
        )}
      </AnimatePresence>
      {channels && isOn && (
        <div style={{ display: 'flex', gap: 4, marginTop: 10 }}>
          {Object.entries(channels).slice(0, 6).map(([key, val]) => (
            <div key={key} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
              <div style={{ width: 14, height: 30, background: 'rgba(0,0,0,0.3)', borderRadius: 2, overflow: 'hidden', position: 'relative' }}>
                <div style={{ position: 'absolute', bottom: 0, width: '100%', height: `${Math.min(100, val / 2.55)}%`, background: `hsl(${(Object.keys(channels).indexOf(key)) * 60}, 80%, 55%)`, borderRadius: 2 }} />
              </div>
              <span style={{ fontSize: 8, color: '#94a3b8', fontFamily: 'monospace' }}>{key}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const ACDevice: React.FC<DeviceProps> = ({ isOn, temperature, mode, fanSpeed }) => {
  const temp = temperature ? parseInt(temperature) : 24;
  const isHeat = mode === 'heat';
  const glowColor = isHeat ? 'rgba(239,68,68,0.4)' : 'rgba(168,85,247,0.4)';
  const flowColor = isHeat ? 'rgba(239,68,68,0.5)' : 'rgba(168,85,247,0.5)';
  // 风速中文映射（fan_speed 的 value 通常为 high/middle/low/auto）
  const fanSpeedLabel: Record<string, string> = { high: '高风', middle: '中风', low: '低风', auto: '自动' };
  const fanSpeedText = fanSpeed ? (fanSpeedLabel[fanSpeed] || fanSpeed) : '';
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 280, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <motion.div
        animate={{ boxShadow: isOn ? `0 15px 35px ${glowColor}` : '0 4px 12px rgba(0,0,0,0.3)' }}
        style={{
          width: '100%', height: 100, borderRadius: '12px 12px 40px 40px',
          background: 'linear-gradient(145deg, #1e293b, #0f172a, #1e293b)',
          border: '1px solid #334155', position: 'relative', overflow: 'hidden',
          display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', zIndex: 2,
        }}
      >
        <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '40%', background: 'linear-gradient(to bottom, rgba(255,255,255,0.05), transparent)', pointerEvents: 'none' }} />
        <div className="glass-frost" style={{ position: 'absolute', right: 30, top: 18, width: 70, height: 40, background: 'rgba(0,0,0,0.6)', borderRadius: 8, border: '1px solid rgba(255,255,255,0.1)', boxShadow: 'inset 0 2px 10px rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', overflow: 'hidden', zIndex: 3 }}>
          {isOn && (
            <motion.div animate={{ opacity: [0.2, 0.4, 0.2] }} transition={{ duration: 2, repeat: Infinity }}
              style={{ position: 'absolute', width: '100%', height: '100%', background: `radial-gradient(circle, ${isHeat ? 'rgba(239,68,68,0.2)' : 'rgba(168,85,247,0.2)'} 0%, transparent 70%)` }}
            />
          )}
          <AnimatePresence>
            {isOn && (
              <motion.div initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }}
                style={{ color: isHeat ? DEVICE_COLORS.RED : DEVICE_COLORS.PURPLE, fontFamily: '"Courier New", monospace', fontSize: 22, fontWeight: 'bold', zIndex: 3, textShadow: `0 0 8px ${isHeat ? 'rgba(239,68,68,0.8)' : 'rgba(168,85,247,0.8)'}`, display: 'flex', alignItems: 'baseline' }}
              >
                {temp}<span style={{ fontSize: 10, marginLeft: 2, opacity: 0.8 }}>°C</span>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        {/* 风速标签：左上角展示当前风速档位 */}
        {isOn && fanSpeedText && (
          <div style={{ position: 'absolute', left: 30, top: 22, fontSize: 9, color: DEVICE_COLORS.VIOLET, fontFamily: 'monospace', fontWeight: 'bold', letterSpacing: 1, textShadow: '0 0 6px rgba(167,139,250,0.5)', zIndex: 3 }}>
            {fanSpeedText}
          </div>
        )}
        {/* 模式标签：左下角展示当前模式（制冷/制热/送风/自动） */}
        {isOn && mode && (
          <div style={{ position: 'absolute', left: 30, top: 38, fontSize: 8, color: isHeat ? 'rgba(239,68,68,0.7)' : 'rgba(168,85,247,0.7)', fontFamily: 'monospace', letterSpacing: 1, zIndex: 3 }}>
            {mode}
          </div>
        )}
        <div style={{ fontSize: 9, fontWeight: 900, color: DEVICE_COLORS.SLATE_BORDER, position: 'absolute', bottom: 18, left: 35, letterSpacing: 4 }}>HDL</div>
        <motion.div
          animate={{ height: isOn ? 22 : 5, backgroundColor: isOn ? DEVICE_COLORS.SLATE_DARK2 : DEVICE_COLORS.SLATE_DARK }}
          transition={{ duration: 1.2, ease: 'easeInOut' }}
          style={{ position: 'absolute', bottom: 2, width: '90%', borderRadius: '4px 4px 15px 15px', zIndex: 1, border: '1px solid #334155', transformOrigin: 'top', display: 'flex', justifyContent: 'center', gap: 8, padding: '0 15px' }}
        >
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} style={{ width: '15%', height: '100%', borderLeft: '1px solid #1e293b', borderRight: '1px solid #1e293b', opacity: 0.5 }} />
          ))}
        </motion.div>
      </motion.div>
      <AnimatePresence>
        {isOn && (
          <div style={{ position: 'absolute', top: 95, width: '85%', height: 70, display: 'flex', justifyContent: 'center', overflow: 'hidden', maskImage: 'linear-gradient(to bottom, black 20%, transparent 90%)' }}>
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i}
                className="ac-airflow-particle"
                style={{
                  position: 'absolute', width: 18, height: 3,
                  background: `linear-gradient(to bottom, ${flowColor}, transparent)`,
                  borderRadius: '50%', filter: 'blur(5px)',
                  animationDelay: `${i * 0.12}s`,
                  '--ac-particle-x': `${(i - 2) * 12 + Math.sin(i) * 8}px`,
                } as React.CSSProperties}
              />
            ))}
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};

const CurtainDevice: React.FC<DeviceProps> = ({ isOn, percent = 0 }) => {
  const openPercent = percent;
  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: 240, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <div style={{ width: '100%', height: 7, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: 4, position: 'relative', zIndex: 5, boxShadow: '0 2px 4px rgba(0,0,0,0.3)' }}>
        <div style={{ position: 'absolute', top: 2.5, left: '10%', right: '10%', height: 1.5, background: 'rgba(255,255,255,0.08)' }} />
      </div>
      <div style={{ width: '88%', height: 130, position: 'relative', marginTop: -2, display: 'flex', justifyContent: 'space-between', overflow: 'hidden', background: 'rgba(0,0,0,0.15)', borderRadius: '0 0 8px 8px' }}>
        <div style={{ position: 'absolute', top: '8%', left: '8%', right: '8%', bottom: '8%', border: '2px solid #334155', display: 'flex' }}>
          <div style={{ flex: 1, borderRight: '1px solid #334155' }} />
          <div style={{ flex: 1 }} />
        </div>
        <motion.div animate={{ width: `${(100 - openPercent) / 2}%` }} transition={{ type: 'spring', stiffness: 30, damping: 12 }}
          style={{ height: '100%', background: isOn ? 'linear-gradient(to right, #334155, #1e293b)' : 'linear-gradient(to right, #475569, #334155)', boxShadow: '2px 0 10px rgba(0,0,0,0.3)', zIndex: 2, position: 'relative', display: 'flex' }}
        >
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} style={{ flex: 1, height: '100%', borderRight: '1px solid rgba(0,0,0,0.15)', borderLeft: '1px solid rgba(255,255,255,0.03)' }} />
          ))}
        </motion.div>
        <motion.div animate={{ width: `${(100 - openPercent) / 2}%` }} transition={{ type: 'spring', stiffness: 30, damping: 12 }}
          style={{ height: '100%', background: isOn ? 'linear-gradient(to left, #334155, #1e293b)' : 'linear-gradient(to left, #475569, #334155)', boxShadow: '-2px 0 10px rgba(0,0,0,0.3)', zIndex: 2, position: 'relative', display: 'flex' }}
        >
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} style={{ flex: 1, height: '100%', borderLeft: '1px solid rgba(0,0,0,0.15)', borderRight: '1px solid rgba(255,255,255,0.03)' }} />
          ))}
        </motion.div>
      </div>
      {percent !== undefined && (
        <div style={{ marginTop: 6, fontSize: 11, color: '#94a3b8', fontFamily: 'monospace' }}>
          {isOn ? '开度' : '关'} {isOn ? `${percent}%` : ''}
        </div>
      )}
    </div>
  );
};

const FanDevice: React.FC<DeviceProps> = ({ isOn }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 180, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
    <div style={{ width: 120, height: 120, borderRadius: '50%', background: DEVICE_COLORS.SLATE_DARK, border: '3px solid #334155', boxShadow: 'inset 0 2px 10px rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', position: 'relative' }}>
      {isOn && (
        <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
          style={{ width: 100, height: 100, position: 'relative' }}
        >
          {Array.from({ length: 5 }).map((_, i) => {
            return (
              <div key={i} style={{
                position: 'absolute', top: '50%', left: '50%',
                width: 12, height: 38, borderRadius: '6px 6px 2px 2px',
                background: 'linear-gradient(180deg, #a855f7, #7e22ce)',
                transformOrigin: '50% 0%', transform: `rotate(${i * 72}deg) translateY(-18px)`,
                opacity: 0.9,
              }} />
            );
          })}
        </motion.div>
      )}
      <div style={{ width: 16, height: 16, borderRadius: '50%', background: DEVICE_COLORS.SLATE_DARK2, border: '2px solid #334155', position: 'absolute', zIndex: 3 }} />
    </div>
    <div style={{ width: 6, height: 30, background: DEVICE_COLORS.SLATE_BORDER, marginTop: -2 }} />
    <div style={{ width: 60, height: 8, background: DEVICE_COLORS.SLATE_DARK2, borderRadius: 4, border: '1px solid #334155' }} />
    {isOn && (
      <motion.div animate={{ opacity: [0.2, 0.5, 0.2] }} transition={{ duration: 2, repeat: Infinity }}
        style={{ position: 'absolute', width: 160, height: 160, borderRadius: '50%', border: '1px solid rgba(168,85,247,0.2)', top: 10 }}
      />
    )}
  </div>
);

const SensorDevice: React.FC<DeviceProps> = ({ isOn, temperature }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 160, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <div style={{
      width: 110, height: 110, borderRadius: 20,
      background: 'linear-gradient(145deg, #1e293b, #0f172a)',
      border: '2px solid #334155', boxShadow: 'inset 0 2px 10px rgba(0,0,0,0.5), 0 4px 15px rgba(0,0,0,0.3)',
      display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', gap: 6,
    }}>
      <div style={{ fontSize: 8, fontWeight: 900, color: DEVICE_COLORS.SLATE_MUTED, letterSpacing: 2 }}>SENSOR</div>
      {temperature && (
        <div style={{ fontSize: 20, fontWeight: 'bold', color: isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_MUTED, fontFamily: '"Courier New", monospace', textShadow: isOn ? '0 0 8px rgba(168,85,247,0.5)' : 'none' }}>
          {temperature}°C
        </div>
      )}
      <motion.div
        animate={{ backgroundColor: isOn ? DEVICE_COLORS.GREEN : DEVICE_COLORS.SLATE_MUTED, boxShadow: isOn ? '0 0 8px #10b981' : 'none' }}
        style={{ width: 6, height: 6, borderRadius: '50%' }}
      />
    </div>
    {isOn && (
      <motion.div animate={{ scale: [1, 1.5, 1], opacity: [0.3, 0, 0.3] }} transition={{ duration: 2, repeat: Infinity }}
        style={{ position: 'absolute', width: 130, height: 130, borderRadius: '50%', border: '1px solid rgba(16,185,129,0.3)' }}
      />
    )}
  </div>
);

const FloorHeatDevice: React.FC<DeviceProps> = ({ isOn, temperature }) => (
  <div style={{ position: 'relative', width: 180, height: 160, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <div style={{
      width: 130, height: 100, borderRadius: 12,
      background: 'linear-gradient(145deg, #1e293b, #0f172a)',
      border: `2px solid ${isOn ? DEVICE_COLORS.RED : DEVICE_COLORS.SLATE_BORDER}`,
      boxShadow: isOn ? '0 0 20px rgba(239,68,68,0.3), inset 0 2px 10px rgba(0,0,0,0.5)' : 'inset 0 2px 10px rgba(0,0,0,0.5)',
      display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', gap: 4,
    }}>
      <div style={{ fontSize: 8, fontWeight: 900, color: DEVICE_COLORS.SLATE_MUTED, letterSpacing: 2 }}>FLOOR HEAT</div>
      {temperature && (
        <div style={{ fontSize: 18, fontWeight: 'bold', color: isOn ? DEVICE_COLORS.RED : DEVICE_COLORS.SLATE_MUTED, fontFamily: '"Courier New", monospace', textShadow: isOn ? '0 0 8px rgba(239,68,68,0.5)' : 'none' }}>
          {temperature}°C
        </div>
      )}
      {isOn && (
        <svg width="80" height="20" viewBox="0 0 80 20">
          <path d="M5 15 Q15 5 25 15 Q35 5 45 15 Q55 5 65 15 Q75 5 80 15" fill="none" stroke="#ef4444" strokeWidth="2" opacity="0.6">
            <animate attributeName="opacity" values="0.3;0.8;0.3" dur="2s" repeatCount="indefinite" />
          </path>
        </svg>
      )}
    </div>
    {isOn && Array.from({ length: 5 }).map((_, i) => (
      <motion.div key={i}
        animate={{ y: [0, -30], opacity: [0.5, 0] }}
        transition={{ duration: 1.5, repeat: Infinity, delay: i * 0.3 }}
        style={{ position: 'absolute', bottom: 20 + i * 8, left: 60 + i * 15, width: 4, height: 4, borderRadius: '50%', background: DEVICE_COLORS.RED, opacity: 0.5 }}
      />
    ))}
  </div>
);

const AirFreshDevice: React.FC<DeviceProps> = ({ isOn }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 160, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <div style={{
      width: 80, height: 120, borderRadius: '40px 40px 20px 20px',
      background: 'linear-gradient(180deg, #1e293b, #0f172a)',
      border: `2px solid ${isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_BORDER}`,
      boxShadow: isOn ? '0 0 15px rgba(168,85,247,0.3)' : 'none',
      display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', gap: 6,
    }}>
      <div style={{ fontSize: 7, fontWeight: 900, color: DEVICE_COLORS.SLATE_MUTED, letterSpacing: 2 }}>FRESH</div>
      <motion.div animate={{ backgroundColor: isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_MUTED }} style={{ width: 6, height: 6, borderRadius: '50%' }} />
    </div>
    {isOn && [0, 1, 2].map(r => (
      <motion.div key={r}
        animate={{ scale: [1, 2 + r * 0.5], opacity: [0.4, 0] }}
        transition={{ duration: 2, repeat: Infinity, delay: r * 0.5 }}
        style={{ position: 'absolute', width: 80 + r * 30, height: 80 + r * 30, borderRadius: '50%', border: '1px solid rgba(168,85,247,0.3)' }}
      />
    ))}
  </div>
);

const TVDevice: React.FC<DeviceProps> = ({ isOn }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 220, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
    <div style={{
      width: 200, height: 110, borderRadius: 8,
      background: isOn ? 'linear-gradient(135deg, #3b0764, #581c87)' : DEVICE_COLORS.SLATE_DARK,
      border: `3px solid ${isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_BORDER}`,
      boxShadow: isOn ? '0 0 20px rgba(168,85,247,0.3)' : 'none',
      display: 'flex', justifyContent: 'center', alignItems: 'center', overflow: 'hidden',
    }}>
      {isOn && (
        <motion.div animate={{ x: ['-100%', '100%'] }} transition={{ duration: 3, repeat: Infinity, ease: 'linear' }}
          style={{ position: 'absolute', width: '50%', height: '100%', background: 'linear-gradient(90deg, transparent, rgba(168,85,247,0.1), transparent)' }}
        />
      )}
      <span style={{ fontSize: 12, color: isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_MUTED, fontWeight: 'bold', letterSpacing: 3, zIndex: 1 }}>{isOn ? 'PLAYING' : 'STANDBY'}</span>
    </div>
    <div style={{ width: 60, height: 6, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: '0 0 4px 4px' }} />
    <div style={{ width: 80, height: 4, background: DEVICE_COLORS.SLATE_DARK2, borderRadius: 2, marginTop: 2 }} />
  </div>
);

const MusicDevice: React.FC<DeviceProps> = ({ isOn }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 160, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <motion.div
      animate={{ rotate: isOn ? 360 : 0 }}
      transition={{ duration: 4, repeat: Infinity, ease: 'linear' }}
      style={{
        width: 90, height: 90, borderRadius: '50%',
        background: isOn ? 'linear-gradient(135deg, #7c3aed, #a855f7, #c084fc)' : 'linear-gradient(135deg, #1e293b, #334155)',
        border: `3px solid ${isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_BORDER}`,
        boxShadow: isOn ? '0 0 20px rgba(168,85,247,0.4)' : 'none',
        display: 'flex', justifyContent: 'center', alignItems: 'center',
      }}
    >
      <div style={{ width: 25, height: 25, borderRadius: '50%', background: isOn ? DEVICE_COLORS.INDIGO_DARK : DEVICE_COLORS.SLATE_DARK, border: `2px solid ${isOn ? DEVICE_COLORS.PURPLE_600 : DEVICE_COLORS.SLATE_BORDER}` }} />
    </motion.div>
    {isOn && [0, 1, 2].map(r => (
      <motion.div key={r}
        animate={{ scale: [1, 2 + r * 0.5], opacity: [0.3, 0] }}
        transition={{ duration: 1.5, repeat: Infinity, delay: r * 0.4 }}
        style={{ position: 'absolute', width: 90 + r * 25, height: 90 + r * 25, borderRadius: '50%', border: '1px solid rgba(168,85,247,0.3)' }}
      />
    ))}
  </div>
);

const SecurityDevice: React.FC<DeviceProps> = ({ isOn }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 140, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <div style={{
      width: 80, height: 95, clipPath: 'polygon(50% 0%, 100% 30%, 100% 100%, 0% 100%, 0% 30%)',
      background: isOn ? 'linear-gradient(180deg, #10b981, #059669)' : 'linear-gradient(180deg, #475569, #334155)',
      boxShadow: isOn ? '0 0 20px rgba(16,185,129,0.4)' : 'none',
      display: 'flex', justifyContent: 'center', alignItems: 'center', paddingTop: 20,
    }}>
      <motion.div
        animate={{ scale: isOn ? [1, 1.1, 1] : 1 }}
        transition={{ duration: 2, repeat: Infinity }}
        style={{ fontSize: 28, color: '#fff', filter: isOn ? 'drop-shadow(0 0 5px #fff)' : 'none' }}
      >
        {isOn ? '🔓' : '🔒'}
      </motion.div>
    </div>
    {isOn && (
      <motion.div animate={{ scale: [1, 1.8], opacity: [0.3, 0] }} transition={{ duration: 2, repeat: Infinity }}
        style={{ position: 'absolute', width: 90, height: 110, clipPath: 'polygon(50% 0%, 100% 30%, 100% 100%, 0% 100%, 0% 30%)', border: '1px solid rgba(16,185,129,0.3)' }}
      />
    )}
  </div>
);

const SocketDevice: React.FC<DeviceProps> = ({ isOn }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 140, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <div style={{
      width: 90, height: 90, borderRadius: 16,
      background: 'linear-gradient(145deg, #1e293b, #0f172a)',
      border: `2px solid ${isOn ? DEVICE_COLORS.AMBER : DEVICE_COLORS.SLATE_BORDER}`,
      boxShadow: isOn ? '0 0 15px rgba(245,158,11,0.3)' : 'inset 0 2px 10px rgba(0,0,0,0.5)',
      display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 8,
    }}>
      <div style={{ width: 14, height: 14, borderRadius: '50%', background: DEVICE_COLORS.SLATE_DARK, border: '1px solid #334155' }} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <div style={{ width: 4, height: 10, background: DEVICE_COLORS.SLATE_DARK, border: '1px solid #334155', borderRadius: 1 }} />
        <div style={{ width: 4, height: 10, background: DEVICE_COLORS.SLATE_DARK, border: '1px solid #334155', borderRadius: 1 }} />
      </div>
    </div>
    {isOn && (
      <motion.div animate={{ scale: [1, 1.5], opacity: [0.3, 0] }} transition={{ duration: 2, repeat: Infinity }}
        style={{ position: 'absolute', width: 110, height: 110, borderRadius: '50%', border: '1px solid rgba(245,158,11,0.3)' }}
      />
    )}
  </div>
);

const RacksDevice: React.FC<DeviceProps> = ({ isOn, percent = 0 }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 160, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
    <div style={{ width: 120, height: 6, background: DEVICE_COLORS.SLATE_BORDER, borderRadius: 3 }} />
    <div style={{ display: 'flex', justifyContent: 'space-between', width: 110 }}>
      <div style={{ width: 2, height: 100, background: DEVICE_COLORS.SLATE_MUTED }} />
      <div style={{ width: 2, height: 100, background: DEVICE_COLORS.SLATE_MUTED }} />
    </div>
    <motion.div
      animate={{ y: isOn ? -(percent || 50) * 0.5 : 0 }}
      transition={{ type: 'spring', stiffness: 50, damping: 15 }}
      style={{ width: 106, height: 4, background: isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_MUTED, borderRadius: 2, marginTop: -102, position: 'relative' }}
    />
    <div style={{ marginTop: 8, fontSize: 12, color: isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_MUTED, fontFamily: 'monospace' }}>
      {isOn ? `${percent}%` : 'OFF'}
    </div>
  </div>
);

const ThermostatDevice: React.FC<DeviceProps> = ({ isOn, temperature }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 160, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <div style={{
      width: 120, height: 120, borderRadius: '50%',
      background: 'linear-gradient(145deg, #1e293b, #0f172a)',
      border: `3px solid ${isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_BORDER}`,
      boxShadow: isOn ? '0 0 20px rgba(168,85,247,0.3)' : 'inset 0 2px 10px rgba(0,0,0,0.5)',
      display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center',
    }}>
      {temperature && (
        <div style={{ fontSize: 24, fontWeight: 'bold', color: isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_MUTED, fontFamily: '"Courier New", monospace', textShadow: isOn ? '0 0 8px rgba(168,85,247,0.5)' : 'none' }}>
          {temperature}°
        </div>
      )}
    </div>
    {isOn && (
      <svg width="140" height="140" style={{ position: 'absolute' }} viewBox="0 0 140 140">
        <circle cx="70" cy="70" r="65" fill="none" stroke="rgba(168,85,247,0.2)" strokeWidth="4" strokeDasharray="8 4" />
      </svg>
    )}
  </div>
);

const GenericDevice: React.FC<DeviceProps> = ({ isOn }) => (
  <div style={{ position: 'relative', width: '100%', maxWidth: 140, height: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
    <motion.div
      animate={{ rotate: isOn ? 360 : 0 }}
      transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
      style={{
        width: 80, height: 80, borderRadius: '50%',
        border: `2px dashed ${isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_BORDER}`,
        display: 'flex', justifyContent: 'center', alignItems: 'center',
      }}
    >
      <div style={{
        width: 50, height: 50, borderRadius: 12,
        background: isOn ? 'linear-gradient(135deg, #a855f7, #6b21a8)' : 'linear-gradient(135deg, #1e293b, #334155)',
        border: `2px solid ${isOn ? DEVICE_COLORS.PURPLE : DEVICE_COLORS.SLATE_BORDER}`,
        boxShadow: isOn ? '0 0 15px rgba(168,85,247,0.3)' : 'none',
        display: 'flex', justifyContent: 'center', alignItems: 'center',
      }}>
        <span style={{ fontSize: 10, fontWeight: 900, color: isOn ? '#fff' : DEVICE_COLORS.SLATE_MUTED, letterSpacing: 1 }}>HDL</span>
      </div>
    </motion.div>
  </div>
);

const SPK_COMPONENT_MAP: Record<string, (props: Props) => React.ReactElement> = {
  'light.rgbwaf': (p) => <LedStripDevice isOn={p.isOn} brightness={p.brightness} color={p.color} channels={p.channels} />,
  'light.ledstrip': (p) => <LedStripDevice isOn={p.isOn} brightness={p.brightness} color={p.color} channels={p.channels} />,
  'light.rgbw': (p) => <RGBWLightDevice isOn={p.isOn} brightness={p.brightness} color={p.color} />,
  'light.rgbcw': (p) => <RGBLightDevice isOn={p.isOn} brightness={p.brightness} color={p.color} />,
  'light.rgb': (p) => <LedStripDevice isOn={p.isOn} brightness={p.brightness} color={p.color} channels={p.channels} />,
  'light.cct': (p) => <CCTLightDevice isOn={p.isOn} brightness={p.brightness} color={p.color} />,
  'light.dimming': (p) => <DimmingLightDevice isOn={p.isOn} brightness={p.brightness} color={p.color} />,
  'light.switch': (p) => <SwitchLightDevice isOn={p.isOn} />,
  'hvac.ac': (p) => <ACDevice isOn={p.isOn} temperature={p.temperature} mode={p.mode} fanSpeed={p.fanSpeed} />,
  'hvac.fan': (p) => <FanDevice isOn={p.isOn} />,
  'hvac.floorHeat': (p) => <FloorHeatDevice isOn={p.isOn} temperature={p.temperature} />,
  'hvac.airFresh': (p) => <AirFreshDevice isOn={p.isOn} />,
  'hvac.qing_luan': (p) => <ThermostatDevice isOn={p.isOn} temperature={p.temperature} />,
  'ir.tv': (p) => <TVDevice isOn={p.isOn} />,
  'ir.ac': (p) => <ACDevice isOn={p.isOn} temperature={p.temperature} mode={p.mode} fanSpeed={p.fanSpeed} />,
  'ir.pjt': (p) => <TVDevice isOn={p.isOn} />,
  'av.music': (p) => <MusicDevice isOn={p.isOn} />,
  'security.door': (p) => <SecurityDevice isOn={p.isOn} />,
  'panel.socket': (p) => <SocketDevice isOn={p.isOn} />,
  'electrical.racks': (p) => <RacksDevice isOn={p.isOn} percent={p.percent} />,
};

const SPK_PREFIX_MAP: [string, (props: Props) => React.ReactElement][] = [
  ['curtain.', (p) => <CurtainDevice isOn={p.isOn} percent={p.percent} />],
  ['sensor.', (p) => <SensorDevice isOn={p.isOn} temperature={p.temperature} />],
];

const CATEGORY_COMPONENT_MAP: Record<string, (props: Props) => React.ReactElement> = {
  light: (p) => <LightDevice isOn={p.isOn} brightness={p.brightness} color={p.color} />,
  curtain: (p) => <CurtainDevice isOn={p.isOn} percent={p.percent} />,
  hvac: (p) => <ACDevice isOn={p.isOn} temperature={p.temperature} mode={p.mode} fanSpeed={p.fanSpeed} />,
  ir: (p) => <TVDevice isOn={p.isOn} />,
  av: (p) => <MusicDevice isOn={p.isOn} />,
  security: (p) => <SecurityDevice isOn={p.isOn} />,
  panel: (p) => <SocketDevice isOn={p.isOn} />,
  electrical: (p) => <GenericDevice isOn={p.isOn} />,
  other: (p) => <GenericDevice isOn={p.isOn} />,
};

function resolveComponent(spk: string, props: Props): React.ReactElement {
  if (SPK_COMPONENT_MAP[spk]) return SPK_COMPONENT_MAP[spk](props);
  for (const [prefix, fn] of SPK_PREFIX_MAP) {
    if (spk.startsWith(prefix)) return fn(props);
  }
  const category = getSpkCategory(spk);
  const fn = CATEGORY_COMPONENT_MAP[category] || CATEGORY_COMPONENT_MAP.other;
  return fn(props);
}

const DeviceImageBase = function DeviceImage({ spk, isOn, brightness, temperature, percent, mode, color, channels, fanSpeed, cct }: Props) {
  // 默认主色改为青蓝（#00D4FF），符合设计规范"开关开启：亮色（#00D4FF）"
  // 当设备有实际颜色（如 RGB 灯设了红色）时优先使用实际颜色
  const activeColor = color ? rgbStr(color) : DEVICE_COLORS.CYAN;
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.85, y: 10 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      transition={{ type: "spring", stiffness: 200, damping: 20 }}
      whileHover={{ scale: 1.05, y: -6 }}
      className={`relative group w-full h-full${isOn ? ' neon-pulse' : ''}`}
      style={{ filter: isOn ? `drop-shadow(0 0 18px ${activeColor}50)` : 'none', transition: 'filter 0.5s ease' }}
    >
      <div style={{
        position: 'relative', width: '100%', height: '100%', borderRadius: 16, overflow: 'hidden',
        // 背景：开启时青蓝到紫色径向渐变，关闭时深色玻璃质感
        background: isOn
          ? `radial-gradient(ellipse at 50% 30%, ${activeColor}20 0%, ${DEVICE_COLORS.GRADIENT_PURPLE}10 40%, transparent 70%)`
          : 'rgba(15,23,42,0.6)',
        border: `1.5px solid ${isOn ? activeColor + '70' : '#33415560'}`,
        boxShadow: isOn
          ? `0 0 30px ${activeColor}20, inset 0 0 30px ${activeColor}10`
          : 'inset 0 0 20px rgba(0,0,0,0.3)',
        transition: 'box-shadow 0.5s, border-color 0.5s, background 0.5s, filter 0.5s, transform 0.5s',
      }}>
        {/* 网格背景：开启时青蓝色，关闭时紫色 */}
        <div style={{ position: 'absolute', inset: 0, opacity: 0.04, backgroundImage: `linear-gradient(${isOn ? DEVICE_COLORS.CYAN : DEVICE_COLORS.PURPLE} 1px, transparent 1px), linear-gradient(90deg, ${isOn ? DEVICE_COLORS.CYAN : DEVICE_COLORS.PURPLE} 1px, transparent 1px)`, backgroundSize: '20px 20px' }} />
        <div style={{ position: 'absolute', top: 2, left: 2, right: 2, bottom: 2, borderRadius: 14, border: `1px solid ${isOn ? activeColor + '25' : '#33415525'}` }} />
        {isOn && (
          <div
            className="device-card-glow"
            style={{ position: 'absolute', inset: 4, borderRadius: 12, border: `1px solid ${activeColor}35`, background: `radial-gradient(ellipse at 25% 15%, ${activeColor}15 0%, transparent 45%), radial-gradient(ellipse at 75% 85%, ${DEVICE_COLORS.GRADIENT_PURPLE}10 0%, transparent 45%)` }}
          />
        )}
        {/* 左上角状态指示灯（开启时青蓝呼吸，关闭时灰色） */}
        <div style={{ position: 'absolute', top: 8, left: 8, display: 'flex', flexDirection: 'column', gap: 3 }}>
          <motion.div animate={{ borderColor: isOn ? activeColor : DEVICE_COLORS.SLATE_MUTED, boxShadow: isOn ? `0 0 4px ${activeColor}60` : 'none' }}
            style={{ width: 6, height: 6, borderRadius: '50%', border: `1.5px solid ${isOn ? activeColor : DEVICE_COLORS.SLATE_MUTED}`, opacity: 0.9 }}
          />
          <div style={{ width: 6, height: 6, borderRadius: '50%', border: `1.5px solid ${isOn ? activeColor : DEVICE_COLORS.SLATE_MUTED}`, opacity: 0.5 }} />
        </div>
        {/* 右上角装饰旋转环 */}
        <div style={{ position: 'absolute', top: 8, right: 8 }}>
          <motion.div animate={{ rotate: 360 }} transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
            style={{ width: 14, height: 14, borderRadius: '50%', border: `1px dashed ${isOn ? activeColor + '40' : '#33415540'}`, opacity: 0.6 }}
          />
        </div>
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 'calc(100% - 44px)', paddingTop: 8 }}>
          {resolveComponent(spk, { isOn, brightness, temperature, percent, mode, color, channels, fanSpeed, cct } as Props)}
        </div>
        {/* 底部状态栏：毛玻璃背景，展示各项属性值 */}
        <div className="glass-frost" style={{
          position: 'absolute', bottom: 0, left: 0, right: 0, height: 38,
          background: 'linear-gradient(to top, rgba(11,14,20,0.85), rgba(26,31,46,0.6))',
          borderRadius: '0 0 14px 14px',
          borderTop: `0.5px solid ${isOn ? activeColor + '35' : '#33415530'}`,
          display: 'flex', alignItems: 'center', padding: '0 12px', gap: 14,
        }}>
          {brightness !== undefined && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: 7, color: DEVICE_COLORS.CYAN, fontFamily: 'monospace', opacity: 0.8, lineHeight: 1, letterSpacing: 1 }}>BRI</span>
              <span style={{ fontSize: 12, color: DEVICE_COLORS.TEXT_LIGHT, fontFamily: 'monospace', fontWeight: 'bold', lineHeight: 1.3, textShadow: isOn ? '0 0 6px rgba(0,212,255,0.4)' : 'none' }}>{brightness}%</span>
            </div>
          )}
          {temperature !== undefined && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: 7, color: DEVICE_COLORS.AMBER, fontFamily: 'monospace', opacity: 0.8, lineHeight: 1, letterSpacing: 1 }}>TEMP</span>
              <span style={{ fontSize: 12, color: DEVICE_COLORS.TEXT_LIGHT, fontFamily: 'monospace', fontWeight: 'bold', lineHeight: 1.3, textShadow: isOn ? '0 0 6px rgba(245,158,11,0.4)' : 'none' }}>{temperature}°C</span>
            </div>
          )}
          {percent !== undefined && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: 7, color: DEVICE_COLORS.GREEN, fontFamily: 'monospace', opacity: 0.8, lineHeight: 1, letterSpacing: 1 }}>POS</span>
              <span style={{ fontSize: 12, color: DEVICE_COLORS.TEXT_LIGHT, fontFamily: 'monospace', fontWeight: 'bold', lineHeight: 1.3, textShadow: isOn ? '0 0 6px rgba(16,185,129,0.4)' : 'none' }}>{percent}%</span>
            </div>
          )}
          {mode && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: 7, color: DEVICE_COLORS.VIOLET, fontFamily: 'monospace', opacity: 0.8, lineHeight: 1, letterSpacing: 1 }}>MODE</span>
              <span style={{ fontSize: 12, color: DEVICE_COLORS.TEXT_LIGHT, fontFamily: 'monospace', fontWeight: 'bold', lineHeight: 1.3 }}>{mode}</span>
            </div>
          )}
          {cct !== undefined && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: 7, color: DEVICE_COLORS.AMBER, fontFamily: 'monospace', opacity: 0.8, lineHeight: 1, letterSpacing: 1 }}>CCT</span>
              <span style={{ fontSize: 12, color: DEVICE_COLORS.TEXT_LIGHT, fontFamily: 'monospace', fontWeight: 'bold', lineHeight: 1.3, textShadow: isOn ? '0 0 6px rgba(245,158,11,0.4)' : 'none' }}>{cct}K</span>
            </div>
          )}
          {fanSpeed && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span style={{ fontSize: 7, color: DEVICE_COLORS.VIOLET, fontFamily: 'monospace', opacity: 0.8, lineHeight: 1, letterSpacing: 1 }}>FAN</span>
              <span style={{ fontSize: 12, color: DEVICE_COLORS.TEXT_LIGHT, fontFamily: 'monospace', fontWeight: 'bold', lineHeight: 1.3 }}>{fanSpeed}</span>
            </div>
          )}
          <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 5 }}>
            <motion.div
              animate={{ backgroundColor: isOn ? DEVICE_COLORS.CYAN : DEVICE_COLORS.RED, boxShadow: isOn ? `0 0 8px ${DEVICE_COLORS.CYAN}` : '0 0 8px #ef4444' }}
              transition={{ duration: 0.3 }}
              style={{ width: 8, height: 8, borderRadius: '50%' }}
            />
            <span style={{ fontSize: 8, color: isOn ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.25)', fontFamily: 'monospace', fontWeight: 'bold', letterSpacing: 1 }}>{isOn ? 'ON' : 'OFF'}</span>
          </div>
        </div>
      </div>
    </motion.div>
  );
};

export default React.memo(DeviceImageBase);
