/**
 * 户型图方案推荐页面（内联卡片模式，通过 DynamicPage 路由 floor-plan-result 渲染）。
 *
 * <p>当后端户型图产品方案 Agent（floor-plan-agent）调用成功后，SSE result 事件携带
 * routePath=/floor-plan/result 和 FloorPlanVO 数据，前端 DynamicPage 据此渲染本组件。</p>
 *
 * 功能：
 * 1. 展示上传的原户型图图片
 * 2. 在户型图上按 XY 轴坐标标注房间位置和推荐设备位置
 * 3. 展示推荐产品清单（含价格、数量、位置、推荐理由）
 * 4. 支持一键加购所有推荐产品到购物车（下单）
 * 5. 支持下载方案清单（CSV 格式，可后续扩展为 Excel）
 *
 * @author zqs
 * @since 4.5.0
 */
import { useState, useRef, useEffect } from 'react';
import {
  MapPin, ShoppingCart, Download, Package, Loader2, CheckCircle, ArrowLeft,
} from 'lucide-react';
import type { FloorPlanVO, RecommendedProduct, ProductDetailData } from '../api/client';
import { addToCart, getProductDetail } from '../api/client';
import type { RoutePageProps } from './DynamicPage';
import ProductDetailPage from './ProductDetailPage';

/**
 * 将 DynamicPage 传入的 Record<string, unknown> 数据安全转换为 FloorPlanVO。
 *
 * <p>DynamicPage 统一传递 data: Record<string, unknown>，本组件需要 FloorPlanVO 强类型，
 * 通过 as unknown as FloorPlanVO 转换（后端 ToolResultVO.data 即 FloorPlanVO 序列化结果）。</p>
 */
export default function FloorPlanPage({ data }: RoutePageProps) {
  // DynamicPage 传入的 data 是 SSE result 事件的 data 字段。
  // 后端 FloorPlanTool 返回 ToolResultVO.success(message, vo, routePath, broadcastText)，
  // AgentScopeResult.toDataMap() 将 ToolResultVO 的所有字段（data/reply/broadcastText）平铺，
  // 导致 SSE result.data 结构为 { data: FloorPlanVO, reply: "...", broadcastText: "..." }，
  // FloorPlanVO 被嵌套在 data.data 中。
  // 兼容两种结构：嵌套（ToolResultVO 包装）→ 取 data.data；扁平（直接 FloorPlanVO）→ 取 data 本身。
  const rawData = data as Record<string, unknown>;
  const floorPlanData = ((rawData.data ?? rawData) as unknown as FloorPlanVO);
  const [addingAll, setAddingAll] = useState(false);
  const [addedIds, setAddedIds] = useState<Set<string>>(new Set());
  const [addingId, setAddingId] = useState<string | null>(null);
  /**
   * 当前高亮的产品序号（null 表示无高亮）。
   *
   * <p>点击户型图上的数字标注或右侧列表项时，左右联动高亮：
   * 左侧标注展开详情卡片并放大高亮，右侧列表项高亮背景并滚动到可视区域。</p>
   */
  const [highlightedIdx, setHighlightedIdx] = useState<number | null>(null);
  /** 右侧产品列表容器 ref，用于滚动到高亮项 */
  const listRef = useRef<HTMLDivElement>(null);
  /** 各产品列表项 ref，用于滚动定位 */
  const itemRefs = useRef<(HTMLDivElement | null)[]>([]);
  /** 内联产品详情数据（点击产品图片/名称时调用 getProductDetail 获取，内联展示 ProductDetailPage） */
  const [inlineDetail, setInlineDetail] = useState<ProductDetailData | null>(null);
  /** 产品详情加载中状态 */
  const [loadingDetail, setLoadingDetail] = useState(false);

  /**
   * 高亮指定产品（左右联动）。
   * 点击同一项时取消高亮，点击不同项时切换高亮。
   */
  const toggleHighlight = (idx: number) => {
    setHighlightedIdx(prev => prev === idx ? null : idx);
  };

  /**
   * 查看产品详情（内联展示）。
   *
   * <p>点击产品图片或名称时调用 getProductDetail REST API 获取产品详情，
   * 内联渲染 ProductDetailPage 组件（与 ProductListPage 相同的交互模式）。
   * 不使用 window.open 跳转，因为前端无 /product/:id 路由。</p>
   *
   * @param product 推荐产品（需含 productId）
   */
  const handleViewDetail = async (product: RecommendedProduct) => {
    if (loadingDetail || !product.productId) return;
    setLoadingDetail(true);
    try {
      const res = await getProductDetail({ productId: product.productId });
      if (res.success && res.data) {
        const detail: ProductDetailData = {
          ...(res.data as ProductDetailData),
          productId: (res.data as ProductDetailData).productId ?? product.productId,
          productNameCn: (res.data as ProductDetailData).productNameCn ?? product.productName,
          productModel: (res.data as ProductDetailData).productModel ?? product.productModel,
        };
        setInlineDetail(detail);
      } else {
        alert(res.message || '获取产品详情失败');
      }
    } catch (e) {
      alert(`获取产品详情失败：${(e as Error).message}`);
    } finally {
      setLoadingDetail(false);
    }
  };

  /**
   * 高亮项变化时，滚动右侧列表到对应位置。
   */
  useEffect(() => {
    if (highlightedIdx !== null && itemRefs.current[highlightedIdx] && listRef.current) {
      itemRefs.current[highlightedIdx]!.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  }, [highlightedIdx]);

  /**
   * 加购单个产品到购物车。
   */
  const handleAddToCart = async (product: RecommendedProduct) => {
    if (!product.skuId || !product.productId) {
      alert('产品信息不完整，无法加购');
      return;
    }
    setAddingId(product.productId + '-' + product.skuId);
    try {
      const result = await addToCart({
        skuId: product.skuId,
        productId: product.productId,
        quantity: product.quantity,
        erpNo: product.erpNo,
      });
      if (result.code === 200) {
        setAddedIds(prev => new Set(prev).add(product.productId + '-' + product.skuId));
      } else {
        alert(`加购失败: ${result.message}`);
      }
    } catch (err) {
      alert(`加购异常: ${(err as Error).message}`);
    } finally {
      setAddingId(null);
    }
  };

  /**
   * 一键加购所有推荐产品。
   */
  const handleAddAllToCart = async () => {
    if (!floorPlanData.recommendedProducts || floorPlanData.recommendedProducts.length === 0) {
      return;
    }
    setAddingAll(true);
    const failed: string[] = [];
    for (const product of floorPlanData.recommendedProducts) {
      if (!product.skuId || !product.productId) {
        failed.push(product.productName || '未知产品');
        continue;
      }
      try {
        const result = await addToCart({
          skuId: product.skuId,
          productId: product.productId,
          quantity: product.quantity,
          erpNo: product.erpNo,
        });
        if (result.code === 200) {
          setAddedIds(prev => new Set(prev).add(product.productId + '-' + product.skuId));
        } else {
          failed.push(product.productName || product.productId);
        }
      } catch {
        failed.push(product.productName || product.productId);
      }
    }
    setAddingAll(false);
    if (failed.length > 0) {
      alert(`部分产品加购失败: ${failed.join(', ')}`);
    } else {
      alert('全部产品已加入购物车');
    }
  };

  /**
   * 下载方案清单（CSV格式）。
   *
   * <p>产品信息为空时用类别+房间+理由兜底填充，避免清单中出现空行。
   * 价格为"面议"时小计也显示"面议"，有价格时计算 数量×单价。</p>
   */
  const handleDownloadList = () => {
    if (!floorPlanData.recommendedProducts || floorPlanData.recommendedProducts.length === 0) {
      alert('暂无推荐产品');
      return;
    }
    const headers = ['序号', '产品名称', '型号', '类别', '安装位置', '数量', '单价', '小计', '推荐理由'];
    const rows = floorPlanData.recommendedProducts.map((p, idx) => {
      // 产品名兜底：productName → category+roomName → "未知产品"
      const productName = p.productName
        || [p.category, p.roomName].filter(Boolean).join('-')
        || '未知产品';
      // 价格和小计：有价格时计算小计，无价格时均显示"面议"
      const price = p.price || '面议';
      let subtotal = '面议';
      if (price !== '面议') {
        const priceNum = parseFloat(price.replace(/[^\d.]/g, ''));
        if (!isNaN(priceNum)) {
          subtotal = `¥${(priceNum * p.quantity).toFixed(2)}`;
        }
      }
      return [
        idx + 1,
        productName,
        p.productModel || '—',
        p.category || '—',
        p.roomName || '未指定',
        p.quantity,
        price,
        subtotal,
        p.reason || '—',
      ];
    });
    const csvContent = '\uFEFF' + [headers, ...rows].map(row =>
      row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')
    ).join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `HDL方案清单_${floorPlanData.planId.substring(0, 8)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const products = floorPlanData.recommendedProducts || [];
  const rooms = floorPlanData.rooms || [];
  const allAdded = products.length > 0 && products.every(p =>
    addedIds.has(`${p.productId}-${p.skuId}`));

  // 内联展示产品详情时，渲染 ProductDetailPage 并提供返回按钮
  if (inlineDetail) {
    return (
      <div className="w-full bg-slate-900/95 border border-cyan-500/30 rounded-xl shadow-2xl overflow-hidden flex flex-col">
        <button
          onClick={() => setInlineDetail(null)}
          className="flex items-center gap-1 px-4 py-2 text-xs text-cyan-400 hover:text-cyan-300 transition border-b border-slate-700/50"
        >
          <ArrowLeft size={14} />
          返回方案推荐
        </button>
        <div className="overflow-auto p-3">
          <ProductDetailPage data={inlineDetail} onBack={() => setInlineDetail(null)} />
        </div>
      </div>
    );
  }

  return (
    <div className="w-full bg-slate-900/95 border border-cyan-500/30 rounded-xl shadow-2xl overflow-hidden flex flex-col">
      {/* 标题栏（内联卡片模式，无关闭按钮，由便利贴面板统一管理关闭） */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-700/50 bg-gradient-to-r from-cyan-900/20 to-blue-900/20">
        <div className="flex items-center gap-2 min-w-0">
          <MapPin className="text-cyan-400 shrink-0" size={20} />
          <div className="min-w-0">
            <h2 className="text-sm font-bold text-white truncate">户型图智能方案推荐</h2>
            <p className="text-[11px] text-slate-400 truncate">{floorPlanData.analysisSummary || '方案已生成'}</p>
            {floorPlanData.userRequirement && (
              <p className="text-[11px] text-cyan-300/80 mt-0.5 truncate">
                <span className="text-slate-500">需求：</span>{floorPlanData.userRequirement}
              </p>
            )}
          </div>
        </div>
        <div className="text-right shrink-0 ml-2">
          <div className="text-[10px] text-slate-400">方案总价</div>
          <div className="text-base font-bold text-cyan-400">{floorPlanData.totalPrice || '面议'}</div>
          {/* 预算约束状态展示 */}
          {floorPlanData.budgetStatus && floorPlanData.budgetStatus !== 'no_budget' && floorPlanData.budgetLimit && (
            <div className="text-[10px] mt-0.5">
              <span className={
                floorPlanData.budgetStatus === 'within_budget' ? 'text-green-400' : 'text-orange-400'
              }>
                {floorPlanData.budgetStatus === 'within_budget' ? '✓ 预算内' : '⚠ 超预算'}
              </span>
              <span className="text-slate-500 ml-1">
                上限¥{floorPlanData.budgetLimit.toLocaleString()}
                {floorPlanData.budgetUsageRate != null && ` · 使用率${floorPlanData.budgetUsageRate}%`}
              </span>
            </div>
          )}
        </div>
      </div>

      <div className="overflow-auto p-4 grid grid-cols-1 lg:grid-cols-2 gap-4 items-stretch">
        {/* 左侧：原户型图展示 + XY 轴设备标注 */}
        <div className="flex flex-col gap-2 min-h-0">
          <h3 className="text-xs font-semibold text-cyan-400 flex items-center gap-1.5">
            <MapPin size={12} />
            户型图标注（{rooms.length}个房间，{products.length}个产品）
          </h3>
          {/*
            外层容器：p-3 内边距为边缘标记预留空间，overflow-visible 允许展开卡片超出容器边界。
            内层 relative 容器精确包裹图片，百分比定位的标记相对于内层容器（=图片实际边界），
            避免 object-contain 留白导致标记落在图片可视区域之外。
          */}
          <div className="rounded-lg overflow-visible border border-slate-700/50 bg-slate-800/50 p-3">
            <div className="relative">
            {floorPlanData.imageUrl ? (
              <img
                src={floorPlanData.imageUrl}
                alt="户型图"
                className="w-full h-auto block"
              />
            ) : (
              <div className="w-full h-[240px] flex items-center justify-center text-slate-500 text-sm">
                图片加载失败
              </div>
            )}
              {/* 房间标注 */}
              {rooms.map((room, idx) => (
                <div
                  key={`room-${idx}`}
                  className="absolute pointer-events-none"
                  style={{
                    left: `${room.position.x}%`,
                    top: `${room.position.y}%`,
                    transform: 'translate(-50%, -50%)',
                  }}
                >
                  <div className="px-2 py-1 rounded-lg bg-blue-500/80 text-white text-[10px] font-medium whitespace-nowrap shadow-lg border border-blue-300/50">
                    {room.name}
                    {room.estimatedArea && ` · ${room.estimatedArea}㎡`}
                  </div>
                </div>
              ))}
              {/* 产品标注（点击展开详情卡片，左右联动高亮） */}
              {products.map((product, idx) => {
                const isExpanded = highlightedIdx === idx;
                // 智能定位：根据标注位置决定卡片展开方向，确保卡片始终在容器内
                // y < 50 时卡片向下展开，y >= 50 时向上展开
                const cardBelow = product.position.y < 50;
                // x 靠左时卡片左对齐，靠右时右对齐，中间时居中
                const cardAlign = product.position.x < 33 ? 'left-0' : product.position.x > 67 ? 'right-0' : 'left-1/2 -translate-x-1/2';
                return (
                  <div
                    key={`product-${idx}`}
                    className="absolute cursor-pointer"
                    style={{
                      left: `${product.position.x}%`,
                      top: `${product.position.y}%`,
                      transform: 'translate(-50%, -50%)',
                      zIndex: isExpanded ? 20 : 10,
                    }}
                    onClick={(e) => {
                      e.stopPropagation();
                      toggleHighlight(idx);
                    }}
                  >
                    {/* 数字圆点（选中时高亮放大） */}
                    <div className={`w-7 h-7 rounded-full border-2 shadow-lg flex items-center justify-center text-white text-[10px] font-bold transition ${
                      isExpanded
                        ? 'bg-orange-500 border-orange-300 scale-125'
                        : 'bg-cyan-500 border-cyan-300 hover:scale-110'
                    }`}>
                      {idx + 1}
                    </div>
                    {/* 点击展开的产品详情卡片（智能定位避免超出容器） */}
                    {isExpanded && (
                      <div
                        className={`absolute ${cardAlign} ${cardBelow ? 'top-full mt-2' : 'bottom-full mb-2'} z-30`}
                        style={{ minWidth: '208px' }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        <div className="bg-slate-900/95 border border-cyan-500/40 rounded-lg shadow-2xl p-2.5 w-52 max-h-[280px] overflow-auto">
                          {/* 关闭按钮 */}
                          <button
                            onClick={(e) => { e.stopPropagation(); setHighlightedIdx(null); }}
                            className="absolute top-1 right-1 w-5 h-5 rounded-full bg-slate-700/80 hover:bg-red-600/80 text-white flex items-center justify-center text-[10px] leading-none z-10"
                            title="关闭"
                          >
                            ✕
                          </button>
                          {/* 产品图片 + 信息（点击图片或名称内联展示产品详情） */}
                          <div className="flex gap-2">
                            {product.productImage && (
                              <img
                                src={product.productImage}
                                alt={product.productName || product.category || ''}
                                className="w-14 h-14 rounded-md object-cover border border-slate-600/50 flex-shrink-0 cursor-pointer hover:border-cyan-500/60 transition"
                                onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                                onClick={(e) => { e.stopPropagation(); handleViewDetail(product); }}
                              />
                            )}
                            <div className="flex-1 min-w-0 pr-3">
                              <div
                                className="text-[11px] font-bold text-white truncate cursor-pointer hover:text-cyan-300 transition"
                                onClick={(e) => { e.stopPropagation(); handleViewDetail(product); }}
                                title="点击查看产品详情"
                              >
                                {product.productName || product.category || '未知产品'}
                              </div>
                              {product.productModel && (
                                <div className="text-[9px] text-slate-400 truncate">型号: {product.productModel}</div>
                              )}
                              <div className="text-[10px] text-cyan-300 font-medium mt-0.5">
                                {product.price || '面议'}
                              </div>
                            </div>
                          </div>
                          {/* 详细信息 */}
                          <div className="mt-1.5 space-y-0.5 text-[10px] text-slate-300">
                            <div className="flex justify-between">
                              <span className="text-slate-500">类别:</span>
                              <span>{product.category || '—'}</span>
                            </div>
                            <div className="flex justify-between">
                              <span className="text-slate-500">安装位置:</span>
                              <span>{product.roomName || '未指定'}</span>
                            </div>
                            <div className="flex justify-between">
                              <span className="text-slate-500">数量:</span>
                              <span>{product.quantity}</span>
                            </div>
                            {product.erpNo && (
                              <div className="flex justify-between">
                                <span className="text-slate-500">ERP:</span>
                                <span className="font-mono">{product.erpNo}</span>
                              </div>
                            )}
                          </div>
                          {/* 推荐理由 */}
                          {product.reason && (
                            <div className="mt-1.5 pt-1.5 border-t border-slate-700/50">
                              <div className="text-[9px] text-slate-500 mb-0.5">推荐理由</div>
                              <div className="text-[10px] text-slate-300 leading-relaxed">{product.reason}</div>
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
            </div>
          </div>

          {/* 右侧：推荐产品清单（与左侧户型图等高，列表区域自适应滚动） */}
          <div className="flex flex-col gap-3 min-h-0 h-full">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-cyan-400 flex items-center gap-1.5">
                <Package size={14} />
                推荐产品清单（{products.length}个产品）
              </h3>
              <div className="flex gap-2">
                <button
                  onClick={handleDownloadList}
                  disabled={products.length === 0}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-slate-700/50 hover:bg-slate-600/50 text-slate-300 text-xs font-medium transition disabled:opacity-50"
                >
                  <Download size={12} />
                  下载清单
                </button>
                <button
                  onClick={handleAddAllToCart}
                  disabled={addingAll || products.length === 0 || allAdded}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-medium transition disabled:opacity-50"
                >
                  {addingAll ? <Loader2 size={12} className="animate-spin" /> :
                   allAdded ? <CheckCircle size={12} /> : <ShoppingCart size={12} />}
                  {allAdded ? '已全部加购' : '一键加购'}
                </button>
              </div>
            </div>

            {/* 产品列表（点击联动高亮左侧标注，高度跟随左侧户型图自适应） */}
            <div ref={listRef} className="flex-1 overflow-auto space-y-2 min-h-0">
              {products.length === 0 ? (
                <div className="text-center py-8 text-slate-500 text-sm">
                  暂无推荐产品
                </div>
              ) : (
                products.map((product, idx) => {
                  const productKey = `${product.productId}-${product.skuId}`;
                  const isAdded = addedIds.has(productKey);
                  const isAdding = addingId === productKey;
                  const isHighlighted = highlightedIdx === idx;
                  return (
                    <div
                      key={idx}
                      ref={el => { itemRefs.current[idx] = el; }}
                      onClick={() => toggleHighlight(idx)}
                      className={`flex items-start gap-2 p-2 rounded-lg border cursor-pointer transition ${
                        isHighlighted
                          ? 'bg-orange-500/15 border-orange-500/50 shadow-lg shadow-orange-500/10'
                          : 'bg-slate-800/50 border-slate-700/50 hover:border-cyan-500/30'
                      }`}
                    >
                      {/* 序号（高亮时变色） */}
                      <div className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold transition ${
                        isHighlighted
                          ? 'bg-orange-500/30 border border-orange-500/60 text-orange-400'
                          : 'bg-cyan-500/20 border border-cyan-500/40 text-cyan-400'
                      }`}>
                        {idx + 1}
                      </div>
                      {/* 产品图片（点击查看详情） */}
                      {product.productImage && (
                        <img
                          src={product.productImage}
                          alt={product.productName || product.category || ''}
                          className="flex-shrink-0 w-10 h-10 rounded-md object-cover border border-slate-600/50 cursor-pointer hover:border-cyan-500/60 transition"
                          onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                          onClick={(e) => { e.stopPropagation(); handleViewDetail(product); }}
                        />
                      )}
                      {/* 产品信息 */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span
                            className="text-xs font-medium text-white truncate cursor-pointer hover:text-cyan-300 transition"
                            onClick={(e) => { e.stopPropagation(); handleViewDetail(product); }}
                            title="点击查看产品详情"
                          >
                            {product.productName || product.category || '未知产品'}
                          </span>
                          {product.productModel && (
                            <span className="text-[10px] text-slate-500">{product.productModel}</span>
                          )}
                        </div>
                        <div className="flex items-center gap-2 mt-0.5 text-[10px] text-slate-400">
                          <span className="flex items-center gap-0.5">
                            <MapPin size={9} />
                            {product.roomName || '未指定'}
                          </span>
                          <span>数量: {product.quantity}</span>
                          <span className="text-cyan-400 font-medium">{product.price || '面议'}</span>
                        </div>
                        {product.reason && (
                          <p className="text-[10px] text-slate-500 mt-0.5 line-clamp-2">{product.reason}</p>
                        )}
                      </div>
                      {/* 加购按钮 */}
                      <button
                        onClick={() => handleAddToCart(product)}
                        disabled={isAdding || isAdded || !product.skuId}
                        className="flex-shrink-0 flex items-center gap-1 px-2 py-1 rounded-md text-[11px] font-medium transition disabled:opacity-50"
                        style={{
                          background: isAdded ? 'rgba(34,197,94,0.15)' : 'rgba(8,145,178,0.2)',
                          color: isAdded ? '#4ade80' : '#22d3ee',
                          border: isAdded ? '1px solid rgba(34,197,94,0.3)' : '1px solid rgba(8,145,178,0.3)',
                        }}
                      >
                        {isAdding ? <Loader2 size={10} className="animate-spin" /> :
                         isAdded ? <CheckCircle size={10} /> : <ShoppingCart size={10} />}
                        {isAdded ? '已加购' : '加购'}
                      </button>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>
  );
}
