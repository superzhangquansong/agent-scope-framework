/**
 * 全局粒子背景 + 流星划过特效组件
 *
 * <p>两层视觉效果：</p>
 * <ul>
 *   <li>底层：22 个浮动粒子（原有效果，CSS 动画驱动）</li>
 *   <li>上层：流星划过特效（带发光头部 + 渐变拖尾，随机角度随机时长）</li>
 * </ul>
 *
 * <p>memo 包裹：避免父组件状态变化（如消息更新）导致重新计算与重渲染。</p>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { memo, useMemo } from 'react';

// ==================== 粒子配置 ====================

/** 浮动粒子数量（降低以节省内存） */
const PARTICLE_COUNT = 12;
/** 粒子颜色类名（对应 index.css 中 .particle / .particle.purple / .particle.cyan） */
const PARTICLE_COLORS = ['', 'purple', 'cyan'] as const;

// ==================== 流星配置 ====================

/** 同时存在的流星数量（降低以节省内存） */
const METEOR_COUNT = 3;
/** 流星颜色列表（霓虹色系） */
const METEOR_COLORS = ['#a855f7', '#06b6d4', '#00d4ff', '#e879f9', '#ffffff'] as const;

/** 粒子属性 */
interface Particle {
  left: string;
  top: string;
  delay: string;
  duration: string;
  size: number;
  cls: string;
}

/** 流星属性 */
interface Meteor {
  /** 起始 top 位置（百分比） */
  top: string;
  /** 起始 left 位置（百分比） */
  left: string;
  /** 动画延迟（秒） */
  delay: string;
  /** 动画时长（秒） */
  duration: string;
  /** 流星颜色 */
  color: string;
  /** 流星长度（px，拖尾长度） */
  length: number;
  /** 划过角度（度数，控制流星方向） */
  angle: number;
}

/**
 * 全局粒子背景 + 流星特效组件
 *
 * <p>粒子位置和流星参数在首次挂载时随机生成并固定（useMemo 空依赖），
 * 后续父组件状态变化不会触发重新计算。</p>
 */
function ParticleFieldBase() {
  // ===== 浮动粒子（原有效果） =====
  const particles = useMemo<Particle[]>(() => {
    const arr: Particle[] = [];
    const colors = PARTICLE_COLORS;
    for (let i = 0; i < PARTICLE_COUNT; i++) {
      arr.push({
        left: `${Math.random() * 100}%`,
        top: `${Math.random() * 100}%`,
        delay: `${Math.random() * 8}s`,
        duration: `${8 + Math.random() * 8}s`,
        size: 2 + Math.random() * 3,
        cls: colors[Math.floor(Math.random() * colors.length)],
      });
    }
    return arr;
  }, []);

  // ===== 流星划过特效 =====
  // 每颗流星从屏幕左上区域出发，向右下方斜向划过，带渐变拖尾
  const meteors = useMemo<Meteor[]>(() => {
    const arr: Meteor[] = [];
    for (let i = 0; i < METEOR_COUNT; i++) {
      // 流星从屏幕上方不同位置出发
      arr.push({
        top: `${-5 - Math.random() * 15}%`,
        left: `${Math.random() * 80}%`,
        // 每颗流星间隔不同时间出现，循环周期 8~20 秒
        delay: `${i * 3 + Math.random() * 5}s`,
        duration: `${1.5 + Math.random() * 2}s`,
        color: METEOR_COLORS[i % METEOR_COLORS.length],
        length: 80 + Math.random() * 120,
        // 角度范围 25°~45°（向右下方倾斜划过）
        angle: 25 + Math.random() * 20,
      });
    }
    return arr;
  }, []);

  return (
    <div className="particle-layer" aria-hidden="true">
      {/* ===== 浮动粒子层 ===== */}
      {particles.map((p, i) => (
        <span
          key={`particle-${i}`}
          className={`particle ${p.cls}`}
          style={{
            left: p.left,
            top: p.top,
            width: `${p.size}px`,
            height: `${p.size}px`,
            animationDelay: p.delay,
            animationDuration: p.duration,
          }}
        />
      ))}

      {/* ===== 流星划过层 ===== */}
      {meteors.map((m, i) => (
        <div
          key={`meteor-${i}`}
          className="meteor"
          style={{
            top: m.top,
            left: m.left,
            // 流星拖尾使用线性渐变：从透明到发光头部颜色
            background: `linear-gradient(${m.angle}deg, transparent 0%, ${m.color}33 40%, ${m.color}aa 80%, ${m.color} 100%)`,
            width: `${m.length}px`,
            height: '2px',
            transform: `rotate(${m.angle}deg)`,
            animationDelay: m.delay,
            animationDuration: m.duration,
            // 流星头部发光阴影（颜色匹配流星颜色）
            boxShadow: `0 0 6px ${m.color}, 0 0 12px ${m.color}66, 0 0 20px ${m.color}33`,
            // 圆角让拖尾末端更柔和
            borderRadius: '50%',
            // 发光混合模式，让流星叠加时更炫酷
            mixBlendMode: 'screen',
          }}
        />
      ))}
    </div>
  );
}

const ParticleField = memo(ParticleFieldBase);
export default ParticleField;
