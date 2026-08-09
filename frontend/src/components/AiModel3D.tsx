/**
 * GLB 3D 模型加载器组件（多模型切换版）
 *
 * <p>使用 react-three-fiber + @react-three/drei 加载 GLB 格式的 3D 角色模型。</p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>支持多个 3D 模型候选，模型脚下弧形选择器切换（支持鼠标滚轮滚动）</li>
 *   <li>默认预加载 5 个候选模型，避免切换时卡顿</li>
 *   <li>自定义 GLB 链接地址从外部 props 传入，加入候选列表</li>
 *   <li>使用 SkeletonUtils.clone 正确克隆蒙皮角色模型</li>
 *   <li>播放动画时过滤 scale/position 轨道，只保留 rotation（避免模型被缩没或移走）</li>
 *   <li>OrbitControls：鼠标拖拽旋转、滚轮缩放</li>
 *   <li>多光源照明 + 接触阴影</li>
 *   <li>自动居中 + 缩放适配 + 相机定位</li>
 * </ul>
 *
 * @author zqs
 * @since 3.2.0
 */
import { Suspense, useEffect, useRef, useState, Component, ReactNode, useMemo, useCallback } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { useGLTF, OrbitControls, ContactShadows, Html, useProgress } from '@react-three/drei';
import * as THREE from 'three';
/** SkeletonUtils：正确克隆蒙皮角色模型（scene.clone(true) 不会复制 SkinnedMesh.skeleton） */
import { clone as cloneSkinned } from 'three/examples/jsm/utils/SkeletonUtils.js';

/** DRACO 解码器路径（jsdelivr CDN，避免 gstatic 国内不可访问） */
const DRACO_PATH = 'https://cdn.jsdelivr.net/npm/three@0.185.1/examples/jsm/libs/draco/gltf/';

/**
 * 模型 2D 头像配色方案映射表。
 * <p>每个模型有独立的渐变色 + 缩写字母，作为 2D 头像展示。</p>
 * <p>避免加载 GLB 生成缩略图（内存开销大），用 CSS 渐变 + 文字代替。</p>
 */
const MODEL_AVATAR_MAP: Record<string, { gradient: string; initials: string }> = {
  lacrimosa:   { gradient: 'linear-gradient(135deg, #667eea, #764ba2)', initials: 'La' },
  nanally:     { gradient: 'linear-gradient(135deg, #f093fb, #f5576c)', initials: 'Na' },
  black_bird:  { gradient: 'linear-gradient(135deg, #434343, #000000)', initials: 'Bl' },
  esper_zero:  { gradient: 'linear-gradient(135deg, #11998e, #38ef7d)', initials: 'Es' },
  dafodil:     { gradient: 'linear-gradient(135deg, #f6d365, #fda085)', initials: 'Da' },
};

/**
 * 获取模型的 2D 头像配色方案。
 * @param modelId 模型 ID
 * @returns 渐变色 + 缩写字母（未匹配时返回默认紫色方案）
 */
function getModelAvatar(modelId: string): { gradient: string; initials: string } {
  return MODEL_AVATAR_MAP[modelId] || { gradient: 'linear-gradient(135deg, #a855f7, #06b6d4)', initials: '?' };
}

/** 默认候选模型列表（提前预加载，避免切换卡顿） */
const DEFAULT_MODELS: ModelInfo[] = [
  {
    id: 'robotPlayground',
    name: 'robotPlayground',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/mode/robot_playground.glb',
  },
  {
    id: 'mechaWarrior',
    name: 'mechaWarrior',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/mode/bot_mecha_warrior_3d_by_oscar_creativo.glb',
  },
  {
    id: 'oscarGirl',
    name: 'oscarGirl',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/mode/girl_cartoon_cyber_by_oscar_creativo.glb',
  },
  {
    id: 'fluxs_pit_stop',
    name: 'fluxs',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/mode/fluxs_pit_stop.glb',
  },
  {
    id: 'lacrimosa',
    name: 'Lacrimosa',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/lacrimosa_animated_-_neverness_to_everness.glb',
  },
  {
    id: 'nanally',
    name: 'Nanally',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/mode/nanally_coluccisre_-_neverness_to_everness.glb',
  },
  {
    id: 'black_bird',
    name: 'Black Bird',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/mode/black_bird_animated_-_neverness_to_evenress.glb',
  },
  {
    id: 'esper_zero',
    name: 'Esper Zero',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/mode/esper_zero_-_neverness_to_everness.glb',
  },
  {
    id: 'dafodil',
    name: 'Dafodil',
    url: 'https://hdl-hz-prod.oss-cn-hangzhou.aliyuncs.com/20/2026/01/mode/dafodil_animated_-_neverness_to_everness.glb',
  },
];

/** 模型信息（id + 显示名 + GLB 链接） */
export interface ModelInfo {
  /** 唯一 ID */
  id: string;
  /** 显示名称 */
  name: string;
  /** GLB 文件 URL */
  url: string;
}

/** AiModel3D 组件 Props */
interface AiModel3DProps {
  /** 交互状态：控制旋转速度和视觉效果 */
  state?: 'idle' | 'listening' | 'thinking' | 'speaking';
  /** 自定义尺寸（px，默认全屏） */
  size?: number;
  /** 自定义 GLB 模型列表（从设置面板传入，会作为候选模型加入列表） */
  customModels?: ModelInfo[];
  /** 模型被选中时的回调 */
  onModelSelect?: (model: ModelInfo) => void;
}

/**
 * 加载进度提示组件（3D 空间内 HTML 覆盖层）。
 */
function LoadingIndicator() {
  const { progress, active } = useProgress();
  return (
    <Html center>
      <div className="flex flex-col items-center gap-2 px-4 py-3 rounded-lg bg-slate-900/80 border border-neon-purple/30">
        <div className="text-xs text-neon-cyan font-mono">
          {active ? `加载中... ${progress.toFixed(0)}%` : '准备中...'}
        </div>
        <div className="w-32 h-1 rounded-full bg-slate-700 overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-neon-purple to-neon-cyan transition-all duration-300"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
    </Html>
  );
}

/**
 * 计算仅 Mesh 顶点的包围盒（跳过 Bone/Skeleton，避免 SkinnedMesh 的 boundingBox 膨胀）。
 *
 * <p>对每个 Mesh 单独调用 Box3.setFromObject，读取 geometry.attributes.position 的实际顶点坐标，
 * 不受骨骼权重影响。</p>
 *
 * @param obj 场景根对象
 * @returns 包围盒 Box3
 */
function computeMeshOnlyBoundingBox(obj: THREE.Object3D): THREE.Box3 {
  obj.updateMatrixWorld(true);
  const box = new THREE.Box3();
  obj.traverse((child) => {
    if (!(child as THREE.Mesh).isMesh) return;
    const meshBox = new THREE.Box3().setFromObject(child);
    box.union(meshBox);
  });
  return box;
}

/** ModelContent Props */
interface ModelContentProps {
  /** 交互状态 */
  state: string;
  /** 当前要加载的模型 URL */
  modelUrl: string;
}

/**
 * GLB 模型渲染子组件。
 *
 * <p>加载指定 URL 的 GLB 文件、使用 SkeletonUtils 克隆、缩放居中、相机定位、
 * 播放过滤后的动画（仅 rotation 轨道）。</p>
 *
 * @param props.state 交互状态
 * @param props.modelUrl GLB 文件 URL
 */
function ModelContent({ state: _state, modelUrl }: ModelContentProps) {
  /** useThree 获取相机（用于自动定位） */
  const { camera } = useThree();

  console.log('[AiModel3D] ModelContent 渲染, 加载模型:', modelUrl);
  /** 加载 GLB 文件（使用 jsdelivr CDN 的 DRACO 解码器） */
  const { scene, animations } = useGLTF(modelUrl, DRACO_PATH);
  console.log('[AiModel3D] useGLTF 已返回, scene:', scene?.type,
    'animations:', animations?.length, 'children:', scene?.children?.length);

  /**
   * 克隆场景并立即执行所有变换（在渲染前完成，避免 useEffect 时机问题）。
   *
   * 关键点：
   * 1. 使用 SkeletonUtils.clone 而非 scene.clone(true)（正确复制 SkinnedMesh.skeleton）
   * 2. 包围盒计算只遍历 Mesh（跳过 Bone，避免 SkinnedMesh boundingBox 膨胀）
   * 3. 目标高度 6.0（模型在视野中较大）
   */
  const clonedScene = useRef<THREE.Group | null>(null);
  const originalMaterials = useRef<Map<THREE.Object3D, THREE.Material | THREE.Material[]>>(new Map());
  const modelBbox = useRef<THREE.Box3>(new THREE.Box3());

  if (!clonedScene.current) {
    console.log('[AiModel3D] ====== 开始克隆和变换模型 ======');
    // 1. 克隆场景：使用 SkeletonUtils.clone（正确复制蒙皮骨架）
    clonedScene.current = cloneSkinned(scene) as THREE.Group;
    const obj = clonedScene.current;
    console.log('[AiModel3D] scene 已克隆(SkeletonUtils), children:', obj.children.length);

    // 2. 遍历 Mesh：保存原始材质 + 禁用视锥剔除 + 统计
    let meshCount = 0;
    let totalVertices = 0;
    obj.traverse((child) => {
      if (!(child as THREE.Mesh).isMesh) return;
      meshCount++;
      const mesh = child as THREE.Mesh;
      originalMaterials.current.set(mesh, mesh.material);
      // 禁用视锥剔除（蒙皮网格 boundingSphere 可能不准）
      mesh.frustumCulled = false;
      const vCount = mesh.geometry?.attributes?.position?.count || 0;
      totalVertices += vCount;
    });
    console.log('[AiModel3D] Mesh 总数:', meshCount, '顶点总数:', totalVertices);

    // 3. 计算原始包围盒（只算 Mesh 顶点，跳过 Bone）
    const box0 = computeMeshOnlyBoundingBox(obj);
    const size0 = new THREE.Vector3();
    const center0 = new THREE.Vector3();
    box0.getSize(size0);
    box0.getCenter(center0);
    console.log('[AiModel3D] 原始包围盒 size:', size0, 'center:', center0);

    // 4. 缩放适配（目标高度 6.0，让模型在视野中较大）
    const targetHeight = 6.0;
    let scaleFactor = 1;
    if (size0.y > 0 && Number.isFinite(size0.y)) {
      scaleFactor = targetHeight / size0.y;
    } else {
      console.warn('[AiModel3D] size0.y 无效:', size0.y, '保持 scale=1');
    }
    if (!Number.isFinite(scaleFactor) || scaleFactor <= 0) {
      console.warn('[AiModel3D] scaleFactor 无效:', scaleFactor, '回退为 1');
      scaleFactor = 1;
    }
    obj.scale.setScalar(scaleFactor);
    obj.updateMatrixWorld(true);
    console.log('[AiModel3D] 缩放系数:', scaleFactor, '目标高度:', targetHeight);

    // 5. 居中（缩放后重新计算包围盒，将中心点移到原点）
    const box1 = computeMeshOnlyBoundingBox(obj);
    const center1 = new THREE.Vector3();
    box1.getCenter(center1);
    if (Number.isFinite(center1.x) && Number.isFinite(center1.y) && Number.isFinite(center1.z)) {
      obj.position.sub(center1);
    } else {
      console.warn('[AiModel3D] center1 含 NaN:', center1, '跳过居中');
    }
    obj.updateMatrixWorld(true);
    console.log('[AiModel3D] 居中完成, position:', obj.position);

    // 6. 保存最终包围盒（供相机定位使用）
    modelBbox.current = computeMeshOnlyBoundingBox(obj);
    const finalSize = new THREE.Vector3();
    modelBbox.current.getSize(finalSize);
    console.log('[AiModel3D] 最终包围盒 size:', finalSize);
    console.log('[AiModel3D] ====== 模型变换完成 ======');
  }

  /** 相机自动定位（基于真实模型高度计算距离） */
  const cameraDone = useRef(false);
  useEffect(() => {
    if (cameraDone.current || !clonedScene.current) return;
    cameraDone.current = true;

    const perspCam = camera as THREE.PerspectiveCamera;
    const fov = perspCam.fov * (Math.PI / 180);
    const finalSize = new THREE.Vector3();
    modelBbox.current.getSize(finalSize);
    const modelHeight = finalSize.y;

    // 距离 = (模型高度/2) / tan(fov/2) * 2.5（留边距看全身）
    let cameraZ = Math.abs((modelHeight / 2) / Math.tan(fov / 2)) * 2.5;
    if (!Number.isFinite(cameraZ) || cameraZ <= 0) cameraZ = 15;
    camera.position.set(0, 0, cameraZ);
    camera.lookAt(0, 0, 0);
    camera.near = 0.01;
    camera.far = 1000;
    camera.updateProjectionMatrix();
    console.log('[AiModel3D] 相机定位完成: Z=', cameraZ.toFixed(3), 'modelHeight=', modelHeight.toFixed(3));
  }, [camera]);

  /** 播放动画（过滤掉 scale/position 轨道，只保留 rotation quaternion 轨道，
   *  避免动画里的 scale 关键帧把模型缩没，或 position 关键帧把模型移走） */
  const mixerRef = useRef<THREE.AnimationMixer | null>(null);
  useEffect(() => {
    if (!animations || animations.length === 0 || !clonedScene.current) return;

    // 克隆 AnimationClip 并过滤轨道：只保留 quaternion（旋转）轨道
    const originalClip = animations[0];
    const filteredTracks = originalClip.tracks.filter(track =>
      track.name.endsWith('.quaternion')
    );
    const cleanClip = new THREE.AnimationClip(
      originalClip.name + '_rotation_only',
      originalClip.duration,
      filteredTracks
    );
    console.log('[AiModel3D] 原轨道数:', originalClip.tracks.length,
      '过滤后(仅rotation):', cleanClip.tracks.length);

    // 用干净的 clip 创建独立 AnimationMixer 并播放
    const mixer = new THREE.AnimationMixer(clonedScene.current);
    const action = mixer.clipAction(cleanClip);
    action.reset().fadeIn(0.5).play();
    mixerRef.current = mixer;
    console.log('[AiModel3D] 播放动画(仅旋转):', cleanClip.name);

    // 组件卸载或切换模型时清理 mixer
    return () => {
      mixer.stopAllAction();
      mixer.uncacheRoot(clonedScene.current as THREE.Object3D);
    };
  }, [animations]);

  /** 每帧更新 AnimationMixer（驱动骨骼动画播放） */
  useFrame((_, delta) => {
    if (mixerRef.current) {
      mixerRef.current.update(delta);
    }
  });

  // 直接渲染 clonedScene，不包裹额外 group
  return <primitive object={clonedScene.current} />;
}

/**
 * 3D 模型局部错误边界。
 *
 * <p>当 Canvas/GLB 加载失败时，仅在此处显示错误提示，不向上抛出，
 * 确保输入框、语音唤醒等外层功能不受影响。</p>
 */
class ModelErrorBoundary extends Component<
  { children: ReactNode },
  { hasError: boolean; errorMsg: string }
> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false, errorMsg: '' };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, errorMsg: error.message || '3D 模型加载失败' };
  }

  componentDidCatch(error: Error) {
    console.error('[AiModel3D] 错误被局部 ErrorBoundary 捕获（不影响外层）：', error.message);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex items-center justify-center w-full h-full">
          <div className="text-center">
            <div className="text-3xl mb-3 opacity-60">🎮</div>
            <p className="text-xs text-slate-400 mb-1">3D 模型暂时不可用</p>
            <p className="text-[10px] text-slate-600 max-w-xs">
              输入框和语音功能正常使用
            </p>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

/**
 * 单个弧形缩略图按钮
 *
 * <p>使用原生 wheel 事件监听（passive: false）以确保 preventDefault 生效，
 * 阻止 OrbitControls 在滚动切换模型时同时缩放相机。</p>
 */
function ArcThumbnail({
  model,
  isSelected,
  size,
  scale,
  opacity,
  translateX,
  translateY,
  zIndex,
  onClick,
  onWheel,
}: {
  model: ModelInfo;
  isSelected: boolean;
  size: number;
  scale: number;
  opacity: number;
  translateX: number;
  translateY: number;
  zIndex: number;
  onClick: () => void;
  onWheel: (e: WheelEvent) => void;
}) {
  /** 按钮元素 ref（用于原生事件监听） */
  const btnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const el = btnRef.current;
    if (!el) return;
    // 添加 non-passive wheel 监听器
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [onWheel]);

  // 获取当前模型的 2D 头像配色方案（渐变色 + 缩写字母）
  const avatar = getModelAvatar(model.id);

  return (
    <button
      ref={btnRef}
      onClick={onClick}
      title={model.name}
      style={{
        position: 'absolute',
        left: '50%',
        bottom: '0',
        width: `${size}px`,
        height: `${size}px`,
        marginLeft: `-${size / 2}px`,
        // 使用 translate + scale 实现弧形排列与缩放
        transform: `translate(${translateX}px, ${translateY}px) scale(${scale})`,
        opacity,
        pointerEvents: 'auto',   // 缩略图可点击/可滚轮
        cursor: 'pointer',
        // 平滑过渡动画
        transition: 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)',
        zIndex,
        borderRadius: '50%',
        // 选中时紫色发光边框，非选中半透明边框
        border: `2px solid ${isSelected ? '#a855f7' : 'rgba(168,85,247,0.3)'}`,
        // 每个模型使用自己的渐变色作为头像背景
        background: avatar.gradient,
        // 选中时紫色发光阴影
        boxShadow: isSelected ? '0 0 16px rgba(168,85,247,0.7)' : 'none',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 0,
        overflow: 'hidden',
        fontWeight: 'bold',
        color: 'white',
        textShadow: '0 1px 2px rgba(0,0,0,0.5)',
      }}
    >
      {/* 模型缩写字母（2D 头像，替代通用 emoji，每个模型不同） */}
      <span style={{ fontSize: isSelected ? '16px' : '11px', lineHeight: 1, letterSpacing: '-0.5px' }}>
        {avatar.initials}
      </span>
    </button>
  );
}

/**
 * 半圆弧形模型选择器组件
 *
 * <p>位于 Canvas 底部中央（3D 模型脚下），沿半圆弧排列候选模型缩略图。</p>
 * <p>选中模型位于弧顶（中央最高点），相邻模型向两侧下沉并缩小，越远越小越透明。</p>
 * <p>支持鼠标滚轮滚动切换模型，点击缩略图也可切换。</p>
 *
 * <p>角度范围：-60° 到 +60°，相邻模型间隔 15°，选中项在 0°（正前方）。</p>
 * <p>缩略图大小：选中 48px，非选中 32px。</p>
 *
 * <p>容器 pointer-events: none，缩略图 pointer-events: auto，
 * 不影响 Canvas 其他区域的拖拽/缩放交互。</p>
 */
function ArcModelSelector({
  models,
  selectedId,
  onSelect,
}: {
  models: ModelInfo[];
  selectedId: string;
  onSelect: (id: string) => void;
}) {
  /** 当前选中的索引（基于 selectedId 计算） */
  const selectedIndex = useMemo(
    () => Math.max(0, models.findIndex(m => m.id === selectedId)),
    [models, selectedId]
  );

  /** 弧形参数 */
  const ARC_RADIUS = 140;       // 弧形半径（像素），决定弧的大小
  const ANGLE_STEP = 15;        // 相邻模型的角度间隔（度）
  const MAX_OFFSET = 4;         // 可见偏移上限（±4 → ±60°）

  /**
   * 鼠标滚轮切换模型
   *
   * <p>向下滚 → 下一个模型；向上滚 → 上一个模型。</p>
   * <p>使用 useCallback 保持引用稳定，避免每次渲染都重新绑定监听器。</p>
   */
  const handleWheel = useCallback((e: WheelEvent) => {
    e.preventDefault();  // 阻止 OrbitControls 缩放
    if (e.deltaY > 0) {
      // 向下滚 → 下一个模型
      const next = Math.min(selectedIndex + 1, models.length - 1);
      if (next !== selectedIndex) onSelect(models[next].id);
    } else {
      // 向上滚 → 上一个模型
      const prev = Math.max(selectedIndex - 1, 0);
      if (prev !== selectedIndex) onSelect(models[prev].id);
    }
  }, [selectedIndex, models, onSelect]);

  return (
    <div
      style={{
        position: 'absolute',
        bottom: '20px',
        left: 0,
        right: 0,
        display: 'flex',
        justifyContent: 'center',
        pointerEvents: 'none',   // 容器不拦截事件，让缩略图单独处理
        zIndex: 100,
      }}
    >
      <div
        style={{
          position: 'relative',
          width: `${ARC_RADIUS * 2}px`,
          height: `${ARC_RADIUS + 40}px`,
          pointerEvents: 'none',
        }}
      >
        {/* 沿弧形排列的缩略图 */}
        {models.map((model, idx) => {
          /** 相对选中项的偏移（负=左侧，正=右侧） */
          const offset = idx - selectedIndex;
          /** 超出可见范围（±60°）则不渲染 */
          if (Math.abs(offset) > MAX_OFFSET) return null;

          /** 角度（度→弧度） */
          const angleDeg = offset * ANGLE_STEP;
          const angleRad = (angleDeg * Math.PI) / 180;
          /** 是否选中 */
          const isSelected = offset === 0;

          /**
           * 计算弧形位置（pivot 在底部正中，圆弧凹面朝向客户）：
           * - translateX = R * sin(angle)：水平偏移
           * - translateY = R * (1 - cos(angle))：垂直偏移（0° 时在最低点=最前方，
           *   两侧逐渐升高，形成 ∪ 形弧，凹面朝向客户）
           */
          const translateX = ARC_RADIUS * Math.sin(angleRad);
          const translateY = ARC_RADIUS * (1 - Math.cos(angleRad));

          /** 缩放：选中 1.0，越远越小（最小 0.5） */
          const scale = isSelected ? 1 : Math.max(0.5, 1 - Math.abs(offset) * 0.15);
          /** 透明度：选中 1.0，越远越透明（最小 0.3） */
          const opacity = isSelected ? 1 : Math.max(0.3, 1 - Math.abs(offset) * 0.2);
          /** 缩略图尺寸：选中 48px，非选中 32px */
          const size = isSelected ? 48 : 32;

          return (
            <ArcThumbnail
              key={model.id}
              model={model}
              isSelected={isSelected}
              size={size}
              scale={scale}
              opacity={opacity}
              translateX={translateX}
              translateY={translateY}
              zIndex={100 - Math.abs(offset)}
              onClick={() => onSelect(model.id)}
              onWheel={handleWheel}
            />
          );
        })}
      </div>
    </div>
  );
}

/**
 * 内部 Canvas 组件（不含 ErrorBoundary）
 *
 * @param props.state 交互状态
 * @param props.size 尺寸
 * @param props.customModels 外部传入的自定义 GLB 模型列表（从设置面板传入）
 * @param props.onModelSelect 模型被选中时的回调
 */
function AiModel3DInner({
  state = 'idle',
  size,
  customModels,
  onModelSelect,
}: AiModel3DProps) {
  /**
   * 候选模型列表（默认 5 个 + 外部传入的自定义模型）
   *
   * <p>当 customModels 变化时，自动将其加入候选列表末尾。</p>
   */
  const models = useMemo<ModelInfo[]>(() => {
    if (customModels && customModels.length > 0) {
      return [...DEFAULT_MODELS, ...customModels];
    }
    return DEFAULT_MODELS;
  }, [customModels]);

  /** 当前选中的模型 ID */
  const [selectedId, setSelectedId] = useState<string>(DEFAULT_MODELS[0].id);

  /** 当前选中的模型信息 */
  const selectedModel = useMemo(
    () => models.find(m => m.id === selectedId) || models[0],
    [models, selectedId]
  );

  /**
   * 模型切换时清理旧模型的 GLB 缓存，释放内存。
   * <p>useGLTF 会缓存已加载的 GLB 文件，切换模型后旧模型仍占用内存。
   * 此处清理非当前模型的所有缓存，避免内存累积导致浏览器崩溃。</p>
   */
  const prevModelUrl = useRef<string>(selectedModel.url);
  useEffect(() => {
    if (prevModelUrl.current !== selectedModel.url) {
      const oldUrl = prevModelUrl.current;
      prevModelUrl.current = selectedModel.url;
      // 延迟清理，确保新模型已开始加载
      setTimeout(() => {
        try {
          useGLTF.clear(oldUrl);
          console.log('[AiModel3D] 已清理旧模型 GLB 缓存:', oldUrl);
        } catch (e) {
          console.warn('[AiModel3D] 清理 GLB 缓存失败:', e);
        }
      }, 500);
    }
  }, [selectedModel.url]);

  /**
   * 组件卸载时清理当前模型的 GLB 缓存，释放 GPU 内存。
   * <p>当切换到 3D 户型图时，AiModel3D 被卸载，此时清理 GLB 缓存
   * 可以显著降低内存占用，避免浏览器内存不足崩溃。</p>
   * <p>下次返回 3D 模型时会重新从 CDN 加载（有浏览器 HTTP 缓存，不会太慢）。</p>
   */
  useEffect(() => {
    return () => {
      try {
        // 清理当前模型的 GLB 缓存
        useGLTF.clear(selectedModel.url);
        console.log('[AiModel3D] 组件卸载，已清理 GLB 缓存:', selectedModel.url);
      } catch (e) {
        console.warn('[AiModel3D] 卸载清理 GLB 缓存失败:', e);
      }
    };
  }, [selectedModel.url]);

  /**
   * 模型被选中时通知外部
   *
   * <p>跳过首次渲染，避免初始化时触发回调。</p>
   */
  const firstRender = useRef(true);
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    onModelSelect?.(selectedModel);
  }, [selectedId, selectedModel, onModelSelect]);

  return (
    <div
      className="ai-model-3d-container w-full h-full"
      style={{
        width: '100%',
        height: size ? `${size}px` : '100%',
        position: 'relative',
      }}
    >
      {/* ===== 主 3D Canvas（展示当前选中模型） ===== */}
      <Canvas
        key={selectedModel.id}
        camera={{ position: [0, 0.5, 15], fov: 45, near: 0.1, far: 200000 }}
        dpr={[1, 1.2]}
        gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
        style={{ background: 'transparent' }}
      >
        {/* 环境光（高强度基础照明） */}
        <ambientLight intensity={1.2} />
        {/* 半球光（天空-地面渐变） */}
        <hemisphereLight args={['#ffffff', '#cccccc', 1.0]} />
        {/* 方向光（主光源，不投射阴影——ContactShadows 已提供地面投影，省去 shadow map 内存） */}
        <directionalLight
          position={[5, 8, 5]}
          intensity={2.5}
        />
        {/* 点光源（紫色补光） */}
        <pointLight position={[-3, 2, -2]} intensity={1.5} color="#a855f7" />
        {/* 点光源（青色补光） */}
        <pointLight position={[3, 1, 2]} intensity={1.2} color="#06b6d4" />
        {/* 前方补光（移除后方补光，减少光源数量） */}
        <pointLight position={[0, 1, 3]} intensity={1.0} color="#ffffff" />

        <Suspense fallback={<LoadingIndicator />}>
          <ModelContent
            state={state}
            modelUrl={selectedModel.url}
          />
          {/* 接触阴影（底部投影，降低分辨率节省内存） */}
          <ContactShadows
            position={[0, -3.5, 0]}
            opacity={0.4}
            scale={20}
            blur={2.5}
            far={6}
            color="#a855f7"
            resolution={128}
          />
        </Suspense>

        {/* 轨道控制：鼠标拖拽旋转、滚轮缩放 */}
        <OrbitControls
          enablePan={false}
          minDistance={1}
          maxDistance={500}
          minPolarAngle={0}
          maxPolarAngle={Math.PI}
          autoRotate={false}
          enableDamping={false}
        />
      </Canvas>

      {/* ===== 模型脚下弧形选择器（叠加在 Canvas 之上） ===== */}
      <ArcModelSelector
        models={models}
        selectedId={selectedId}
        onSelect={setSelectedId}
      />
    </div>
  );
}

/**
 * GLB 3D 模型加载器主组件（包裹局部 ErrorBoundary）
 *
 * <p>3D 加载失败时仅在此处显示错误提示，不向上抛出，
 * 确保输入框、语音唤醒等外层功能不受影响。</p>
 *
 * @param state 交互状态
 * @param size  尺寸（高度 px）
 */
export default function AiModel3D(props: AiModel3DProps) {
  return (
    <ModelErrorBoundary>
      <AiModel3DInner {...props} />
    </ModelErrorBoundary>
  );
}

// 注意：不预加载候选模型（5 个 GLB 同时加载内存不够）
// 缩略图使用 2D 图标，不加载 GLB，仅点击切换时才加载对应模型
