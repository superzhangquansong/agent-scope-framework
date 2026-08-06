package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 GA 特性三十六：AG-UI 协议适配配置
 * <p>
 * AG-UI（Agent-UI）协议是 Agent 与前端 UI 的标准化通信协议，
 * 桥接 AgentScope 与 AG-UI 兼容前端（如 CopilotKit）：
 * <ul>
 *   <li>事件映射：AgentScope AgentEvent → AG-UI AguiEvent（TEXT_MESSAGE/REASONING/TOOL_CALL）</li>
 *   <li>工具合并：三种模式（AGENT_ONLY/MERGE_FRONTEND_PRIORITY/FRONTEND_ONLY）</li>
 *   <li>状态同步：Agent 运行状态实时同步到前端</li>
 *   <li>SSE 编码：AG-UI 事件通过 SSE 流式输出</li>
 * </ul>
 * </p>
 * <p>
 * 注意：当前依赖的 AgentScope 版本暂未提供 {@code io.agentscope.core.agui.*} API，
 * 本配置类作为占位保留，启用后仅输出日志，待官方 API 落地后再补充装配。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "ag-ui-enabled", havingValue = "true")
public class AgUiConfig {

    private final AgentScopeProperties properties;

    static {
        log.info("[AgUiConfig] AG-UI 协议适配配置已启用（占位模式：当前 agentscope 版本未提供 agui API，暂不装配适配器）");
    }
}
