/**
 * Agent 拓扑图组件（ECharts graph）
 *
 * 用力导向图展示 Agent 之间的主子调用关系：
 *  - 节点大小反映调用次数
 *  - 节点颜色反映健康状态（绿=健康 / 黄=警告 / 红=错误）
 *  - 连线表示主 Agent → 子 Agent 的调用关系
 *  - 悬浮节点显示详细信息（调用次数 / 平均耗时 / 错误次数）
 */

import { useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import type { AgentNode } from '../../api/observe';

/** AgentTopology 属性 */
export interface AgentTopologyProps {
  /** Agent 节点列表 */
  nodes: AgentNode[];
}

/** 健康状态到颜色的映射 */
const HEALTH_COLOR: Record<string, string> = {
  healthy: '#4ade80', // neon-green
  warning: '#fbbf24', // amber-400
  error: '#f87171',   // red-400
};

/** 分类到颜色的映射（用于节点边框区分业务域） */
const CATEGORY_COLOR: Record<string, string> = {
  device: '#22d3ee',
  scene: '#a855f7',
  product: '#ec4899',
  energy: '#fbbf24',
  cart: '#4ade80',
  chat: '#60a5fa',
  qa: '#f97316',
  通用: '#94a3b8',
};

/** Agent 拓扑图组件 */
export default function AgentTopology({ nodes }: AgentTopologyProps) {
  const option = useMemo(() => {
    // 构造 ECharts graph 数据
    const chartNodes = nodes.map((node) => {
      const size = Math.max(20, Math.min(60, 15 + Math.sqrt(node.callCount) * 4));
      const color = HEALTH_COLOR[node.healthStatus] ?? HEALTH_COLOR.healthy;
      const borderColor = CATEGORY_COLOR[node.category] ?? CATEGORY_COLOR['通用'];
      return {
        id: node.agentId,
        name: node.displayName,
        symbolSize: size,
        itemStyle: {
          color,
          borderColor,
          borderWidth: 2,
          shadowColor: color,
          shadowBlur: 10,
        },
        label: {
          show: true,
          position: 'bottom',
          color: '#cbd5e1',
          fontSize: 10,
          formatter: `{a|${node.displayName}}\n{b|调用 ${node.callCount}}`,
          rich: {
            a: { color: '#e2e8f0', fontSize: 10, lineHeight: 14 },
            b: { color: '#64748b', fontSize: 9, lineHeight: 12 },
          },
        },
        // 自定义数据（tooltip 用）
        raw: node,
      };
    });

    // 构造边：主 Agent → 子 Agent
    const links = nodes.flatMap((node) =>
      (node.subAgents ?? []).map((subId) => ({
        source: node.agentId,
        target: subId,
        lineStyle: {
          color: 'rgba(168, 85, 247, 0.4)',
          width: 1.5,
          curveness: 0.2,
        },
      })),
    );

    return {
      tooltip: {
        backgroundColor: 'rgba(15, 23, 42, 0.95)',
        borderColor: 'rgba(168, 85, 247, 0.4)',
        textStyle: { color: '#e2e8f0', fontSize: 11 },
        formatter: (params: { data?: { raw?: AgentNode } }) => {
          const node = params.data?.raw;
          if (!node) return '';
          return `
            <div style="font-weight:bold;color:#a855f7;margin-bottom:4px">${node.displayName}</div>
            <div style="color:#94a3b8">ID: <span style="color:#22d3ee">${node.agentId}</span></div>
            <div style="color:#94a3b8">分类: <span style="color:#cbd5e1">${node.category}</span></div>
            <div style="color:#94a3b8">调用次数: <span style="color:#4ade80">${node.callCount}</span></div>
            <div style="color:#94a3b8">错误次数: <span style="color:#f87171">${node.errorCount}</span></div>
            <div style="color:#94a3b8">健康状态: <span style="color:${HEALTH_COLOR[node.healthStatus]}">${node.healthStatus}</span></div>
            ${node.subAgents.length > 0 ? `<div style="color:#94a3b8">子 Agent: <span style="color:#a855f7">${node.subAgents.join(', ')}</span></div>` : ''}
          `;
        },
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          data: chartNodes,
          links,
          roam: true,
          draggable: true,
          force: {
            repulsion: 200,
            edgeLength: [80, 150],
            gravity: 0.1,
          },
          emphasis: {
            focus: 'adjacency',
            lineStyle: { width: 3 },
            itemStyle: { shadowBlur: 20 },
          },
        },
      ],
    };
  }, [nodes]);

  if (nodes.length === 0) {
    return (
      <div className="glass-panel rounded-lg p-3 border border-neon-purple/20 flex items-center justify-center h-full min-h-[200px]">
        <div className="text-center text-slate-500 text-sm">
          暂无 Agent 节点数据
        </div>
      </div>
    );
  }

  return (
    <div className="glass-panel rounded-lg p-3 border border-neon-purple/20 flex flex-col h-full min-h-0">
      <div className="text-xs text-slate-400 uppercase tracking-wider mb-1">
        Agent 拓扑（{nodes.length} 个节点）
      </div>
      <ReactECharts
        option={option}
        style={{ height: '100%', width: '100%', minHeight: '200px' }}
        opts={{ renderer: 'canvas' }}
        notMerge={true}
      />
    </div>
  );
}
