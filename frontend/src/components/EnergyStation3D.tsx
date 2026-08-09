/**
 * 储能电站 3D 效果展示组件（逼真版）
 *
 * <p>使用 React Three Fiber 创建储能电站 3D 场景，直观展示光伏发电 → 电池储能 → 逆变器输出的能量链路。</p>
 *
 * <p>场景包含：</p>
 * <ul>
 *   <li>光伏板：倾斜 35°，由 6×4 个方形硅片组成阵列，含汇流条、铝合金边框和支架</li>
 *   <li>电池柜：立柜造型，含散热片、LCD 数字显示屏（SOC%）、侧面电量条</li>
 *   <li>逆变器：壁挂箱体，含 LCD 屏幕、散热孔、接线端子、工作指示灯</li>
 *   <li>能量流动：光带 + 粒子混合效果，从光伏板 → 电池 → 逆变器流动</li>
 *   <li>地面：反光地面 + 科幻网格</li>
 * </ul>
 *
 * <p>内存优化：dpr=[1, 1.2]，无 shadows，ContactShadows resolution=128</p>
 *
 * @author hdl-ai-assistant
 * @since 1.0.0
 */
import { Suspense, useRef, useMemo, Component, ReactNode } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { OrbitControls, ContactShadows } from '@react-three/drei';
import * as THREE from 'three';

// ==================== 常量定义 ====================

const NEON_PURPLE = '#a855f7';
const NEON_CYAN = '#06b6d4';
const SOLAR_BLUE = '#1e40af';
const BATTERY_DARK = '#1a1a2e';
const INVERTER_GRAY = '#3a3a4e';
const GROUND_DARK = '#0a0a1e';
const ALLOY_SILVER = '#888899';

/** 光伏板倾斜角度（35 度，更接近真实安装角度） */
const SOLAR_TILT_RAD = THREE.MathUtils.degToRad(35);

/** 能量流动粒子数量 */
const FLOW_PARTICLE_COUNT = 40;

/** SOC 颜色阈值 */
const SOC_LOW_THRESHOLD = 20;
const SOC_MID_THRESHOLD = 50;
const SOC_COLOR_LOW = '#ef4444';
const SOC_COLOR_MID = '#f59e0b';
const SOC_COLOR_HIGH = '#22c55e';

const DEFAULT_CAM_POS: [number, number, number] = [7, 5, 9];

// ==================== 类型定义 ====================

export interface EnergyStation3DProps {
  power?: string;
  batterySoc?: number;
  todayElectricity?: string;
}

// ==================== 工具函数 ====================

function getSocColor(soc: number): string {
  if (soc < SOC_LOW_THRESHOLD) return SOC_COLOR_LOW;
  if (soc < SOC_MID_THRESHOLD) return SOC_COLOR_MID;
  return SOC_COLOR_HIGH;
}

// ==================== 光伏板组件 ====================

/**
 * 光伏板组件（逼真版）。
 * 由 6×4 个方形硅片组成阵列，含汇流条、铝合金边框和斜支架。
 */
function SolarPanel({ position }: { position: [number, number, number] }) {
  // 电池单元阵列：6 列 × 4 行
  const cells = useMemo(() => {
    const arr: Array<{ x: number; z: number }> = [];
    const cols = 6;
    const rows = 4;
    const cellSize = 0.28;
    const gap = 0.04;
    const startX = -((cols - 1) * (cellSize + gap)) / 2;
    const startZ = -((rows - 1) * (cellSize + gap)) / 2;
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        arr.push({
          x: startX + c * (cellSize + gap),
          z: startZ + r * (cellSize + gap),
        });
      }
    }
    return arr;
  }, []);

  return (
    <group position={position}>
      {/* 支架底座（混凝土基座） */}
      <mesh position={[0, 0.05, 0.8]}>
        <boxGeometry args={[0.6, 0.12, 0.5]} />
        <meshStandardMaterial color="#555566" metalness={0.3} roughness={0.8} />
      </mesh>
      {/* 斜支撑杆（左右两根） */}
      <mesh position={[-0.3, 0.6, 0.5]} rotation={[SOLAR_TILT_RAD * 0.6, 0, 0]}>
        <cylinderGeometry args={[0.03, 0.04, 1.5, 8]} />
        <meshStandardMaterial color={ALLOY_SILVER} metalness={0.9} roughness={0.2} />
      </mesh>
      <mesh position={[0.3, 0.6, 0.5]} rotation={[SOLAR_TILT_RAD * 0.6, 0, 0]}>
        <cylinderGeometry args={[0.03, 0.04, 1.5, 8]} />
        <meshStandardMaterial color={ALLOY_SILVER} metalness={0.9} roughness={0.2} />
      </mesh>

      {/* 光伏板主体（倾斜 35°） */}
      <group rotation={[SOLAR_TILT_RAD, 0, 0]} position={[0, 1.4, 0]}>
        {/* 铝合金边框 */}
        <mesh>
          <boxGeometry args={[2.1, 0.08, 1.5]} />
          <meshStandardMaterial color={ALLOY_SILVER} metalness={0.9} roughness={0.15} />
        </mesh>
        {/* 背板（深色） */}
        <mesh position={[0, -0.03, 0]}>
          <boxGeometry args={[2.0, 0.02, 1.4]} />
          <meshStandardMaterial color="#0a0a1a" metalness={0.5} roughness={0.6} />
        </mesh>
        {/* 电池单元阵列（方形硅片，蓝色反光，模拟单晶硅电池） */}
        {cells.map((cell, i) => (
          <mesh key={i} position={[cell.x, 0.045, cell.z]}>
            <boxGeometry args={[0.26, 0.02, 0.26]} />
            <meshStandardMaterial
              color={SOLAR_BLUE}
              metalness={0.8}
              roughness={0.12}
              emissive="#1e3a8a"
              emissiveIntensity={0.25}
            />
          </mesh>
        ))}
        {/* 电池单元之间的汇流条（细银色线条，模拟光伏板导电带） */}
        {Array.from({ length: 5 }).map((_, i) => (
          <mesh key={`busbar-${i}`} position={[0, 0.057, -0.56 + i * 0.28]}>
            <boxGeometry args={[1.96, 0.004, 0.012]} />
            <meshStandardMaterial color={ALLOY_SILVER} metalness={0.95} roughness={0.1} />
          </mesh>
        ))}
        {/* 紫色霓虹边框线 */}
        <lineSegments>
          <edgesGeometry args={[new THREE.BoxGeometry(2.1, 0.08, 1.5)]} />
          <lineBasicMaterial color={NEON_PURPLE} />
        </lineSegments>
      </group>
    </group>
  );
}

// ==================== 电池柜组件 ====================

interface BatteryCabinetProps {
  position: [number, number, number];
  soc: number;
}

/**
 * 电池柜组件（逼真版）。
 * 立柜造型，含散热片、LCD 数字显示屏、侧面电量条。
 */
function BatteryCabinet({ position, soc }: BatteryCabinetProps) {
  const barMatRef = useRef<THREE.MeshStandardMaterial>(null);
  const screenMatRef = useRef<THREE.MeshStandardMaterial>(null);
  const cabinetHeight = 2.2;
  const clampedSoc = Math.max(0, Math.min(100, soc));
  const targetRatio = clampedSoc / 100;
  const socColor = getSocColor(clampedSoc);

  // 脉冲动画
  useFrame((state) => {
    if (barMatRef.current) {
      barMatRef.current.emissiveIntensity = 0.6 + 0.4 * Math.sin(state.clock.elapsedTime * 2);
    }
    if (screenMatRef.current) {
      screenMatRef.current.emissiveIntensity = 0.5 + 0.3 * Math.sin(state.clock.elapsedTime * 3);
    }
  });

  return (
    <group position={position}>
      {/* 柜体主体 */}
      <mesh position={[0, cabinetHeight / 2, 0]}>
        <boxGeometry args={[1.0, cabinetHeight, 0.8]} />
        <meshStandardMaterial color={BATTERY_DARK} metalness={0.8} roughness={0.25} />
      </mesh>
      {/* 霓虹边缘 */}
      <lineSegments position={[0, cabinetHeight / 2, 0]}>
        <edgesGeometry args={[new THREE.BoxGeometry(1.0, cabinetHeight, 0.8)]} />
        <lineBasicMaterial color={NEON_PURPLE} />
      </lineSegments>

      {/* 顶部散热片（5 片铝制散热片） */}
      {[0, 1, 2, 3, 4].map(i => (
        <mesh key={i} position={[-0.35 + i * 0.175, cabinetHeight + 0.04, 0]}>
          <boxGeometry args={[0.08, 0.08, 0.7]} />
          <meshStandardMaterial color={ALLOY_SILVER} metalness={0.9} roughness={0.15} />
        </mesh>
      ))}

      {/* LCD 数字显示屏（正面顶部） */}
      <mesh position={[0, cabinetHeight - 0.35, 0.41]}>
        <boxGeometry args={[0.5, 0.25, 0.02]} />
        <meshStandardMaterial color="#000000" metalness={0.3} roughness={0.5} />
      </mesh>
      {/* 屏幕发光区域（SOC 颜色） */}
      <mesh position={[0, cabinetHeight - 0.35, 0.43]}>
        <planeGeometry args={[0.42, 0.18]} />
        <meshStandardMaterial
          ref={screenMatRef}
          color={socColor}
          emissive={socColor}
          emissiveIntensity={0.6}
          transparent
          opacity={0.7}
        />
      </mesh>

      {/* 散热孔（正面中部，3 排） */}
      {[0, 1, 2].map(row =>
        [0, 1, 2, 3, 4, 5].map(col => (
          <mesh key={`${row}-${col}`} position={[-0.3 + col * 0.12, cabinetHeight - 0.7 - row * 0.12, 0.41]}>
            <boxGeometry args={[0.06, 0.03, 0.02]} />
            <meshStandardMaterial color="#0a0a1a" metalness={0.6} roughness={0.4} />
          </mesh>
        ))
      )}

      {/* 侧面 SOC 电量条背景槽 */}
      <mesh position={[0.51, cabinetHeight / 2, 0]}>
        <boxGeometry args={[0.03, cabinetHeight * 0.75, 0.4]} />
        <meshStandardMaterial color="#0a0a1a" metalness={0.5} roughness={0.5} />
      </mesh>
      {/* SOC 电量条（发光） */}
      <mesh position={[0.53, (cabinetHeight * 0.75 * targetRatio) / 2 + 0.28, 0]}>
        <boxGeometry args={[0.02, cabinetHeight * 0.75 * targetRatio, 0.35]} />
        <meshStandardMaterial
          ref={barMatRef}
          color={socColor}
          emissive={socColor}
          emissiveIntensity={0.8}
          metalness={0.3}
          roughness={0.4}
        />
      </mesh>

      {/* 底部基座 */}
      <mesh position={[0, 0.06, 0]}>
        <boxGeometry args={[1.1, 0.12, 0.9]} />
        <meshStandardMaterial color="#333344" metalness={0.6} roughness={0.4} />
      </mesh>

      {/* 品牌标识灯（顶部小灯，SOC 颜色） */}
      <mesh position={[0, cabinetHeight + 0.12, 0.35]}>
        <sphereGeometry args={[0.05, 12, 12]} />
        <meshStandardMaterial color={socColor} emissive={socColor} emissiveIntensity={1.5} />
      </mesh>
    </group>
  );
}

// ==================== 逆变器组件 ====================

/**
 * 逆变器组件（逼真版）。
 * 壁挂箱体，含 LCD 屏幕、散热孔、接线端子、工作指示灯。
 */
function Inverter({ position }: { position: [number, number, number] }) {
  const ledMatRef = useRef<THREE.MeshStandardMaterial>(null);
  const screenMatRef = useRef<THREE.MeshStandardMaterial>(null);

  useFrame((state) => {
    if (ledMatRef.current) {
      ledMatRef.current.emissiveIntensity = 0.5 + 0.5 * Math.sin(state.clock.elapsedTime * 3);
    }
    if (screenMatRef.current) {
      screenMatRef.current.emissiveIntensity = 0.4 + 0.2 * Math.sin(state.clock.elapsedTime * 1.5);
    }
  });

  return (
    <group position={position}>
      {/* 主体（壁挂箱体） */}
      <mesh position={[0, 0.8, 0]}>
        <boxGeometry args={[0.9, 1.4, 0.35]} />
        <meshStandardMaterial color={INVERTER_GRAY} metalness={0.8} roughness={0.2} />
      </mesh>
      {/* 霓虹边缘 */}
      <lineSegments position={[0, 0.8, 0]}>
        <edgesGeometry args={[new THREE.BoxGeometry(0.9, 1.4, 0.35)]} />
        <lineBasicMaterial color={NEON_PURPLE} />
      </lineSegments>

      {/* LCD 屏幕（正面上部） */}
      <mesh position={[0, 1.15, 0.19]}>
        <boxGeometry args={[0.6, 0.3, 0.02]} />
        <meshStandardMaterial color="#000000" metalness={0.3} roughness={0.5} />
      </mesh>
      <mesh position={[0, 1.15, 0.21]}>
        <planeGeometry args={[0.52, 0.22]} />
        <meshStandardMaterial
          ref={screenMatRef}
          color={NEON_CYAN}
          emissive={NEON_CYAN}
          emissiveIntensity={0.5}
          transparent
          opacity={0.6}
        />
      </mesh>

      {/* 散热孔（正面中部，4 排） */}
      {[0, 1, 2, 3].map(row =>
        [0, 1, 2, 3, 4, 5, 6].map(col => (
          <mesh key={`inv-${row}-${col}`} position={[-0.3 + col * 0.1, 0.75 - row * 0.1, 0.19]}>
            <cylinderGeometry args={[0.015, 0.015, 0.02, 8]} />
            <meshStandardMaterial color="#0a0a1a" metalness={0.6} roughness={0.4} />
          </mesh>
        ))
      )}

      {/* 底部接线端子（2 个） */}
      <mesh position={[-0.2, 0.15, 0.2]}>
        <cylinderGeometry args={[0.04, 0.04, 0.08, 12]} />
        <meshStandardMaterial color={ALLOY_SILVER} metalness={0.9} roughness={0.15} />
      </mesh>
      <mesh position={[0.2, 0.15, 0.2]}>
        <cylinderGeometry args={[0.04, 0.04, 0.08, 12]} />
        <meshStandardMaterial color={ALLOY_SILVER} metalness={0.9} roughness={0.15} />
      </mesh>

      {/* 顶部工作指示灯（绿色脉冲） */}
      <mesh position={[0, 1.55, 0.1]}>
        <sphereGeometry args={[0.06, 16, 16]} />
        <meshStandardMaterial
          ref={ledMatRef}
          color="#22c55e"
          emissive="#22c55e"
          emissiveIntensity={1}
        />
      </mesh>
      {/* 指示灯底座 */}
      <mesh position={[0, 1.5, 0.1]}>
        <cylinderGeometry args={[0.08, 0.1, 0.04, 16]} />
        <meshStandardMaterial color="#555566" metalness={0.8} roughness={0.2} />
      </mesh>

      {/* 壁挂支架（背面） */}
      <mesh position={[0, 0.8, -0.2]}>
        <boxGeometry args={[0.7, 0.6, 0.08]} />
        <meshStandardMaterial color="#333344" metalness={0.7} roughness={0.3} />
      </mesh>
    </group>
  );
}

// ==================== 能量流动组件 ====================

interface EnergyFlowProps {
  path: [number, number, number][];
}

/**
 * 能量流动组件（光带 + 粒子混合）。
 * 粒子沿路径流动，AdditiveBlending 发光效果。
 */
function EnergyFlow({ path }: EnergyFlowProps) {
  const pointsRef = useRef<THREE.Points>(null);

  const { positions, progresses } = useMemo(() => {
    const pos = new Float32Array(FLOW_PARTICLE_COUNT * 3);
    const prog: number[] = [];
    for (let i = 0; i < FLOW_PARTICLE_COUNT; i++) {
      prog.push(i / FLOW_PARTICLE_COUNT);
      pos[i * 3] = path[0][0];
      pos[i * 3 + 1] = path[0][1];
      pos[i * 3 + 2] = path[0][2];
    }
    return { positions: pos, progresses: prog };
  }, [path]);

  useFrame((_, delta) => {
    if (!pointsRef.current) return;
    const geom = pointsRef.current.geometry;
    const pos = geom.attributes.position.array as Float32Array;

    for (let i = 0; i < FLOW_PARTICLE_COUNT; i++) {
      progresses[i] += delta * 0.25;
      if (progresses[i] >= 1) progresses[i] -= 1;

      const totalSegs = path.length - 1;
      const segProgress = progresses[i] * totalSegs;
      const segIdx = Math.floor(segProgress);
      const segT = segProgress - segIdx;
      const nextIdx = Math.min(segIdx + 1, totalSegs);

      pos[i * 3] = THREE.MathUtils.lerp(path[segIdx][0], path[nextIdx][0], segT);
      pos[i * 3 + 1] = THREE.MathUtils.lerp(path[segIdx][1], path[nextIdx][1], segT) + Math.sin(progresses[i] * Math.PI) * 0.4;
      pos[i * 3 + 2] = THREE.MathUtils.lerp(path[segIdx][2], path[nextIdx][2], segT);
    }
    geom.attributes.position.needsUpdate = true;
  });

  return (
    <points ref={pointsRef}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
      </bufferGeometry>
      <pointsMaterial
        size={0.12}
        color={NEON_CYAN}
        transparent
        opacity={0.9}
        sizeAttenuation
        blending={THREE.AdditiveBlending}
      />
    </points>
  );
}

// ==================== 地面组件 ====================

/**
 * 地面组件（反光地面 + 科幻网格）。
 */
function Ground() {
  return (
    <>
      {/* 反光地面 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, 0]}>
        <planeGeometry args={[20, 14]} />
        <meshStandardMaterial
          color={GROUND_DARK}
          metalness={0.6}
          roughness={0.4}
          transparent
          opacity={0.7}
        />
      </mesh>
      {/* 科幻网格 */}
      <gridHelper args={[20, 20, NEON_PURPLE, '#222244']} position={[0, 0.01, 0]} />
    </>
  );
}

// ==================== 场景组件 ====================

interface SceneProps {
  batterySoc: number;
}

function Scene({ batterySoc }: SceneProps) {
  const flowPath: [number, number, number][] = useMemo(() => [
    [-3, 2.4, 0],  // 光伏板顶部
    [0, 2.6, 0],   // 电池柜顶部
    [3, 1.8, 0],   // 逆变器顶部
  ], []);

  return (
    <>
      {/* 光照系统 */}
      <ambientLight intensity={0.45} />
      <directionalLight position={[5, 8, 5]} intensity={0.7} />
      <pointLight position={[-3, 4, 2]} intensity={0.6} color={NEON_PURPLE} distance={12} />
      <pointLight position={[3, 4, 2]} intensity={0.5} color={NEON_CYAN} distance={12} />
      {/* 电池柜 SOC 颜色补光 */}
      <pointLight position={[0, 1.5, 1.5]} intensity={0.4} color={getSocColor(batterySoc)} distance={6} />

      <Ground />

      <SolarPanel position={[-3, 0, 0]} />
      <BatteryCabinet position={[0, 0, 0]} soc={batterySoc} />
      <Inverter position={[3, 0, 0]} />

      <EnergyFlow path={flowPath} />

      <ContactShadows
        position={[0, 0.02, 0]}
        opacity={0.45}
        scale={16}
        blur={2}
        far={4}
        color={NEON_PURPLE}
        resolution={128}
      />

      <OrbitControls
        enablePan={true}
        minDistance={5}
        maxDistance={20}
        target={[0, 1, 0]}
        autoRotate
        autoRotateSpeed={0.5}
      />
    </>
  );
}

// ==================== ErrorBoundary ====================

class EnergyStation3DErrorBoundary extends Component<
  { children: ReactNode },
  { hasError: boolean }
> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: Error) {
    console.error('[EnergyStation3D] 3D 场景加载失败：', error.message);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex items-center justify-center w-full h-full">
          <div className="text-center">
            <div className="text-2xl mb-2 opacity-60">⚡</div>
            <p className="text-[10px] text-slate-500">3D 储能场景不可用</p>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

// ==================== 主组件 ====================

export default function EnergyStation3D({ batterySoc = 60 }: EnergyStation3DProps) {
  return (
    <div className="w-full h-[220px] rounded-xl overflow-hidden glass-panel border border-neon-cyan/20">
      <EnergyStation3DErrorBoundary>
        <Canvas
          camera={{ position: DEFAULT_CAM_POS, fov: 45, near: 0.1, far: 200 }}
          dpr={[1, 1.2]}
          gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
          style={{ background: 'transparent' }}
        >
          <Suspense fallback={null}>
            <Scene batterySoc={batterySoc} />
          </Suspense>
        </Canvas>
      </EnergyStation3DErrorBoundary>
    </div>
  );
}
