//package com.agent.scope.framework.tool;
//
//import com.agent.scope.framework.context.SessionContext;
//import com.agent.scope.framework.hdl.port.HdlApiPort;
//import com.agent.scope.framework.vo.ToolResultVO;
//import io.agentscope.core.agent.RuntimeContext;
//import io.agentscope.core.tool.Tool;
//import io.agentscope.core.tool.ToolParam;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
///**
// * 储能工具。
// *
// * <p>封装 HDL 储能电站查询能力，包括电站列表、电站详情和逆变器实时数据。</p>
// *
// * @author zqs
// * @since 2.0.0
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class EnergyTool extends AbstractTool {
//
//    /** HDL 业务 API 端口 */
//    private final HdlApiPort hdlApiPort;
//
//    /**
//     * 查询储能电站列表。
//     *
//     * @param homeName       电站名称关键词（可选）
//     * @param runtimeContext 运行时上下文（自动注入）
//     * @return 工具结果 VO
//     */
//    @Tool(name = "query_energy_station_list",
//            description = "查询 HDL 储能电站列表。返回用户绑定的所有储能电站信息。"
//                    + "使用场景：用户询问'电站列表'、'有哪些储能电站'时调用。"
//                    + "参数来源要求：homeName 可选，从用户输入提取。",
//            readOnly = true)
//    public ToolResultVO queryEnergyStationList(
//            @ToolParam(name = "homeName", required = false,
//                    description = "电站名称关键词，可选。留空时查询所有电站") String homeName,
//            RuntimeContext runtimeContext) {
//
//        SessionContext sessionContext = resolveSessionContext(runtimeContext);
//        log.info("[EnergyTool] 查询储能电站列表: homeName={}, userId={}",
//                homeName, sessionContext.getUserId());
//        return hdlApiPort.queryEnergyStationList(homeName, sessionContext);
//    }
//
//    /**
//     * 查询储能电站详情。
//     *
//     * @param homeId         电站 ID（可选，留空时使用当前房屋 ID）
//     * @param runtimeContext 运行时上下文（自动注入）
//     * @return 工具结果 VO
//     */
//    @Tool(name = "query_energy_station_detail",
//            description = "查询 HDL 储能电站详情。返回电站的详细信息（含电池 SOC、充放电状态等）。"
//                    + "使用场景：用户询问'电站详情'、'储能电站状态'时调用。"
//                    + "参数来源要求：homeId 可选，留空时自动使用当前房屋 ID。",
//            readOnly = true)
//    public ToolResultVO queryEnergyStationDetail(
//            @ToolParam(name = "homeId", required = false,
//                    description = "电站ID，可选。留空时自动从会话上下文获取当前房屋ID") String homeId,
//            RuntimeContext runtimeContext) {
//
//        SessionContext sessionContext = resolveSessionContext(runtimeContext);
//        log.info("[EnergyTool] 查询储能电站详情: homeId={}, userId={}",
//                homeId, sessionContext.getUserId());
//        return hdlApiPort.queryEnergyStationDetail(homeId, sessionContext);
//    }
//
//    /**
//     * 查询逆变器实时数据。
//     *
//     * @param homeId         电站 ID（可选，留空时使用当前房屋 ID）
//     * @param runtimeContext 运行时上下文（自动注入）
//     * @return 工具结果 VO
//     */
//    @Tool(name = "query_inverter_info",
//            description = "查询 HDL 逆变器实时数据。返回电池 SOC、充放电功率、光伏功率等实时信息。"
//                    + "使用场景：用户询问'逆变器数据'、'电池电量'、'光伏功率'时调用。"
//                    + "参数来源要求：homeId 可选，留空时自动使用当前房屋 ID。",
//            readOnly = true)
//    public ToolResultVO queryInverterInfo(
//            @ToolParam(name = "homeId", required = false,
//                    description = "电站ID，可选。留空时自动从会话上下文获取当前房屋ID") String homeId,
//            RuntimeContext runtimeContext) {
//
//        SessionContext sessionContext = resolveSessionContext(runtimeContext);
//        log.info("[EnergyTool] 查询逆变器实时数据: homeId={}, userId={}",
//                homeId, sessionContext.getUserId());
//        return hdlApiPort.queryInverterInfo(homeId, sessionContext);
//    }
//}
