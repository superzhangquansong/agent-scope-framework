/**
 * 吞吐量折线图组件（ECharts）
 *
 * 展示最近 60 秒每秒事件数（实时折线图）。
 * 数据来源：/api/observe/metrics 返回的 throughputSeries。
 */

import { useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import type { ThroughputPoint } from '../../api/observe';

/** ThroughputChart 属性 */
export interface ThroughputChartProps {
  /** 吞吐量数据点列表 */
  data: ThroughputPoint[];
}

/** 吞吐量折线图组件 */
export default function ThroughputChart({ data }: ThroughputChartProps) {
  // 基于 data 计算 ECharts option（data 变化时重新计算）
  const option = useMemo(() => {
    // X 轴：秒级时间戳转成 HH:mm:ss 格式
    const xAxisData = data.map((p) => {
      const d = new Date(p.timestamp * 1000);
      return d.toLocaleTimeString('zh-CN', { hour12: false });
    });
    // Y 轴：每秒事件数
    const seriesData = data.map((p) => p.count);

    return {
      grid: { top: 20, right: 16, bottom: 24, left: 36 },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 23, 42, 0.95)',
        borderColor: 'rgba(34, 211, 238, 0.4)',
        textStyle: { color: '#e2e8f0', fontSize: 11 },
        formatter: (params: Array<{ axisValue: string; data: number }>) => {
          const p = params[0];
          return `${p.axisValue}<br/>事件数: <span style="color:#22d3ee;font-weight:bold">${p.data}</span>`;
        },
      },
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLabel: {
          color: '#64748b',
          fontSize: 9,
          interval: Math.max(0, Math.floor(data.length / 6) - 1), // 仅显示约 6 个时间标签
        },
        axisLine: { lineStyle: { color: 'rgba(100, 116, 139, 0.3)' } },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: '#64748b', fontSize: 10 },
        splitLine: { lineStyle: { color: 'rgba(100, 116, 139, 0.1)' } },
      },
      series: [
        {
          type: 'line',
          data: seriesData,
          smooth: true,
          symbol: 'none',
          lineStyle: {
            color: '#22d3ee',
            width: 2,
            shadowColor: 'rgba(34, 211, 238, 0.6)',
            shadowBlur: 10,
          },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(34, 211, 238, 0.4)' },
                { offset: 1, color: 'rgba(34, 211, 238, 0.02)' },
              ],
            },
          },
        },
      ],
    };
  }, [data]);

  return (
    <div className="glass-panel rounded-lg p-3 border border-neon-cyan/20">
      <div className="text-xs text-slate-400 uppercase tracking-wider mb-1">
        事件吞吐量（最近 60 秒）
      </div>
      <ReactECharts
        option={option}
        style={{ height: '160px', width: '100%' }}
        opts={{ renderer: 'canvas' }}
        notMerge={true}
      />
    </div>
  );
}
