package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.vo.ToolResultVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 房屋工具（特性 3：ReAct 智能体与工具调用）。
 *
 * <p>封装 HDL 房屋列表查询能力。用户首次接入或切换房屋时，
 * LLM 通过本工具获取房屋列表供用户选择。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeTool extends AbstractTool {

    /**
     * 查询房屋列表。
     *
     * <p>查询当前用户绑定的所有房屋，返回房屋 ID、名称、类型、设备数量等信息。
     * 用户说"我有哪些房屋"或"切换到客厅"时，LLM 调用本工具获取候选列表。</p>
     *
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 为房屋列表 JSON）
     */
    @Tool(name = "query_home_list",
            description = "查询用户绑定的 HDL 房屋列表。返回房屋 ID、名称、类型、设备数量等信息。"
                    + "使用场景：用户首次接入、切换房屋、或询问'我有哪些房屋'时调用。"
                    + "参数来源要求：无业务参数，无需 LLM 提供任何 ID。"
                    + "禁止事项：禁止编造房屋 ID，场景查询等后续操作所需的 homeId 必须来自本工具返回结果。",
            readOnly = true)
    public ToolResultVO queryHomeList(RuntimeContext runtimeContext) {
        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[HomeTool] 查询房屋列表: userId={}", sessionContext.getUserId());
        return new ToolResultVO();
    }
}
