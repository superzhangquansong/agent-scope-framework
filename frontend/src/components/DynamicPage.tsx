import type { ReactNode } from 'react';
import ProductListPage from './ProductListPage';
import ProductDetailPage from './ProductDetailPage';
import DeviceDetailPage from './DeviceDetailPage';
import DeviceStatusPage from './DeviceStatusPage';
import DeviceListPage from './DeviceListPage';
import HomeListPage from './HomeListPage';
import SceneListPage from './SceneListPage';
import SceneDetailPage from './SceneDetailPage';
import SceneCreateResultPage from './SceneCreateResultPage';
import SceneRecommendPage from './SceneRecommendPage';
import SceneExecuteResultPage from './SceneExecuteResultPage';
import SceneDeleteResultPage from './SceneDeleteResultPage';
import CartListPage from './CartListPage';
import CartAddResultPage from './CartAddResultPage';
import CartCheckoutPage from './CartCheckoutPage';
import QaListPage from './QaListPage';
import EnergyStationListPage from './EnergyStationListPage';
import EnergyStationDetailPage from './EnergyStationDetailPage';
import EnergyBatteryReportPage from './EnergyBatteryReportPage';
import EnergySavingsReportPage from './EnergySavingsReportPage';
import EnergyInverterInfoPage from './EnergyInverterInfoPage';
import EnergyFaultDiagnosisPage from './EnergyFaultDiagnosisPage';
import FloorPlanPage from './FloorPlanPage';
import GenericResultPage from './GenericResultPage';
import EmptyResultPage from './EmptyResultPage';
import ErrorResultPage from './ErrorResultPage';

/** 路由组件统一接收的 props */
export interface RoutePageProps {
  data: Record<string, unknown>;
}

/** 路由路径常量（与后端 result.routePath 对齐） */
export const ROUTE_PATH = {
  PRODUCT_LIST: 'product-list',
  PRODUCT_DETAIL: 'product-detail',
  DEVICE_DETAIL: 'device-detail',
  DEVICE_STATUS: 'device-status',
  DEVICE_LIST: 'device-list',
  HOME_LIST: 'home-list',
  SCENE_LIST: 'scene-list',
  SCENE_DETAIL: 'scene-detail',
  SCENE_CREATE: 'scene-create',
  SCENE_EXECUTE: 'scene-execute',
  SCENE_DELETE: 'scene-delete',
  SCENE_RECOMMEND: 'scene-recommend',
  CART_LIST: 'cart-list',
  CART_ADD: 'cart-add',
  CART_CHECKOUT: 'cart-checkout',
  QA_LIST: 'qa-list',
  ENERGY_STATION_LIST: 'energy-station-list',
  ENERGY_STATION_DETAIL: 'energy-station-detail',
  ENERGY_INVERTER_INFO: 'energy-inverter-info',
  ENERGY_BATTERY_REPORT: 'energy-battery-report',
  ENERGY_SAVINGS_REPORT: 'energy-savings-report',
  ENERGY_FAULT_DIAGNOSIS: 'energy-fault-diagnosis',
  /**
   * 户型图产品方案推荐结果路由。
   *
   * <p>后端 floor-plan-agent 调用成功后，SSE result 事件携带 routePath=/floor-plan/result，
   * 经 StickyNotesContainer 归一化（去掉前导 /，将 / 替换为 -）后变为 floor-plan-result，
   * 匹配此常量渲染 FloorPlanPage 内联卡片（展示原户型图+设备 XY 标注+清单+下单）。</p>
   */
  FLOOR_PLAN_RESULT: 'floor-plan-result',
  GENERIC: 'generic',
  EMPTY: 'empty',
  ERROR: 'error',
} as const;

/**
 * 动态页面路由器
 *
 * 根据 currentRoute.path 渲染对应的结果页面组件（switch case）。
 * 未匹配的 path 兜底渲染 GenericResultPage。
 */
export default function DynamicPage({ path, data }: { path: string; data: Record<string, unknown> }) {
  let content: ReactNode;
  switch (path) {
    case ROUTE_PATH.PRODUCT_LIST:
      content = <ProductListPage data={data} />;
      break;
    case ROUTE_PATH.PRODUCT_DETAIL:
      content = <ProductDetailPage data={data} />;
      break;
    case ROUTE_PATH.DEVICE_DETAIL:
      content = <DeviceDetailPage data={data} />;
      break;
    case ROUTE_PATH.DEVICE_STATUS:
      content = <DeviceStatusPage data={data} />;
      break;
    case ROUTE_PATH.DEVICE_LIST:
      content = <DeviceListPage data={data} />;
      break;
    case ROUTE_PATH.HOME_LIST:
      content = <HomeListPage data={data} />;
      break;
    case ROUTE_PATH.SCENE_LIST:
      content = <SceneListPage data={data} />;
      break;
    case ROUTE_PATH.SCENE_DETAIL:
      content = <SceneDetailPage data={data} />;
      break;
    case ROUTE_PATH.SCENE_CREATE:
      content = <SceneCreateResultPage data={data} />;
      break;
    case ROUTE_PATH.SCENE_EXECUTE:
      content = <SceneExecuteResultPage data={data} />;
      break;
    case ROUTE_PATH.SCENE_DELETE:
      content = <SceneDeleteResultPage data={data} />;
      break;
    case ROUTE_PATH.SCENE_RECOMMEND:
      content = <SceneRecommendPage data={data} />;
      break;
    case ROUTE_PATH.CART_LIST:
      content = <CartListPage data={data} />;
      break;
    case ROUTE_PATH.CART_ADD:
      content = <CartAddResultPage data={data} />;
      break;
    case ROUTE_PATH.CART_CHECKOUT:
      content = <CartCheckoutPage data={data} />;
      break;
    case ROUTE_PATH.QA_LIST:
      content = <QaListPage data={data} />;
      break;
    case ROUTE_PATH.ENERGY_STATION_LIST:
      content = <EnergyStationListPage data={data} />;
      break;
    case ROUTE_PATH.ENERGY_STATION_DETAIL:
      content = <EnergyStationDetailPage data={data} />;
      break;
    case ROUTE_PATH.ENERGY_BATTERY_REPORT:
      content = <EnergyBatteryReportPage data={data} />;
      break;
    case ROUTE_PATH.ENERGY_SAVINGS_REPORT:
      content = <EnergySavingsReportPage data={data} />;
      break;
    case ROUTE_PATH.ENERGY_INVERTER_INFO:
      // 逆变器实时数据页（含充放电功率、电池SOC、光伏功率、负载功率等）
      content = <EnergyInverterInfoPage data={data} />;
      break;
    case ROUTE_PATH.ENERGY_FAULT_DIAGNOSIS:
      // 电站故障排查页（展示诊断结果、SOC 对比、预期动作 vs 实际状态）
      content = <EnergyFaultDiagnosisPage data={data} />;
      break;
    case ROUTE_PATH.FLOOR_PLAN_RESULT:
      // 户型图产品方案推荐页（内联卡片：原户型图+设备 XY 标注+清单下载+加购下单）
      content = <FloorPlanPage data={data} />;
      break;
    case ROUTE_PATH.GENERIC:
      content = <GenericResultPage data={data} />;
      break;
    case ROUTE_PATH.EMPTY:
      content = <EmptyResultPage data={data} />;
      break;
    case ROUTE_PATH.ERROR:
      content = <ErrorResultPage data={data} />;
      break;
    default:
      content = <GenericResultPage data={data} />;
  }
  return <>{content}</>;
}
