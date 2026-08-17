package com.agent.scope.framework.hdl.port;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.vo.ToolResultVO;

import java.util.List;
import java.util.Map;

/**
 * HDL 业务 API 端口接口（六边形架构端口）。
 *
 * <p>定义工具类需要的所有 HDL 业务操作。适配当前项目使用 {@link SessionContext}
 * 替代原 hdl-agent 项目的 ToolSessionContext。</p>
 *
 * <p>所有方法接收 {@link SessionContext} 参数（含 accessToken、houseId 等会话信息），
 * 由工具方法从 RuntimeContext 中取出后传入。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface HdlApiPort {

    /**
     * 查询房屋列表。
     *
     * @param sessionContext 会话上下文（含 accessToken）
     * @return 工具结果 VO（data 为房屋列表 JSON）
     */
    ToolResultVO queryHomeList(SessionContext sessionContext);

    /**
     * 查询设备列表。
     *
     * @param spk            设备种类码（null 则查询全部）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO queryDeviceList(String spk, SessionContext sessionContext);

    /**
     * 查询设备列表完整数据（供内部工具方法使用）。
     *
     * @param sessionContext 会话上下文
     * @return 设备列表（List&lt;Map&gt;）；无数据返回空列表
     */
    List<Map<String, Object>> queryDeviceListRaw(SessionContext sessionContext);

    /**
     * 查询设备详情（最新状态）。
     *
     * @param deviceIds      设备 ID 列表（逗号分隔）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO queryDeviceDetail(String deviceIds, SessionContext sessionContext);

    /**
     * 控制设备。
     *
     * @param deviceId       设备 ID
     * @param gatewayId      网关 ID
     * @param spk            设备种类码
     * @param attributesJson 控制属性 JSON 字符串
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO controlDevice(String deviceId, String gatewayId, String spk,
                                String attributesJson, SessionContext sessionContext);

    /**
     * 批量控制设备。
     *
     * @param actionsJson    设备动作 JSON 数组字符串
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO batchControlDevice(String actionsJson, SessionContext sessionContext);

    /**
     * 批量控制设备（接收已解析的 actions）。
     *
     * @param actions            设备动作列表
     * @param gatewayId          网关 ID
     * @param deviceIdsForDetail 控制成功后需查询详情的设备 ID 列表（逗号分隔）
     * @param sessionContext     会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO batchControlDevice(List<Map<String, Object>> actions, String gatewayId,
                                     String deviceIdsForDetail, SessionContext sessionContext);

    /**
     * 查询场景列表。
     *
     * @param homeId         房屋 ID
     * @param roomId         房间 ID（可选）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO querySceneList(String homeId, String roomId, SessionContext sessionContext);

    /**
     * 执行场景。
     *
     * @param sceneIds        场景 ID
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO executeScene(List<String> sceneIds, SessionContext sessionContext);

    /**
     * 创建场景。
     *
     * @param body           场景参数
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO createScene(Map<String, Object> body, SessionContext sessionContext);

    /**
     * 更新场景。
     *
     * @param body           HDL 场景更新报文（scenes 数组结构，含 userSceneId）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO updateScene(Map<String, Object> body, SessionContext sessionContext);

    /**
     * 删除场景。
     *
     * @param sceneId        场景 ID（userSceneId）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO deleteScene(String sceneId, SessionContext sessionContext);

    /**
     * 查询产品详情。
     *
     * @param productId      产品 ID
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO queryProductDetail(String productId, SessionContext sessionContext);

    /**
     * 查询产品详情原始数据（含 skuId、price、erpNo 等加购必需字段）。
     *
     * @param productId      产品 ID
     * @param sessionContext 会话上下文
     * @return HDL 原始响应 Map，失败返回 null
     */
    Map<String, Object> queryProductDetailRaw(String productId, SessionContext sessionContext);

    /**
     * 搜索产品列表。
     *
     * @param productName    产品名关键词
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO searchProductList(String productName, SessionContext sessionContext);

    /**
     * 加入购物车。
     *
     * @param skuId          SKU ID
     * @param productId      产品 ID
     * @param quantity       数量
     * @param erpNo          ERP 编号（可选）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO addCart(String skuId, String productId, int quantity,
                          String erpNo, SessionContext sessionContext);

    /**
     * 查询购物车列表。
     *
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO queryCartList(SessionContext sessionContext);

    /**
     * 知识库检索（RAG）。
     *
     * @param question       用户问题
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO queryKnowledge(String question, SessionContext sessionContext);

    /**
     * 列出知识库所有文档。
     *
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO listKnowledgeDocuments(SessionContext sessionContext);

    /**
     * 删除知识库文档。
     *
     * @param docCode        文档编码
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO deleteKnowledgeDocument(String docCode, SessionContext sessionContext);

    /**
     * 查询储能电站列表。
     *
     * @param homeName       电站名称关键词（可为空）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO queryEnergyStationList(String homeName, SessionContext sessionContext);

    /**
     * 查询储能电站详情。
     *
     * @param homeId         电站 ID（可为空时自动填充）
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO queryEnergyStationDetail(String homeId, SessionContext sessionContext);

    /**
     * 查询逆变器实时数据。
     *
     * @param homeId         电站 ID
     * @param sessionContext 会话上下文
     * @return 工具结果 VO
     */
    ToolResultVO queryInverterInfo(String homeId, SessionContext sessionContext);
}
