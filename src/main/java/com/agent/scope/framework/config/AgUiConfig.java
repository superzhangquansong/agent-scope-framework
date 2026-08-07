package com.agent.scope.framework.config;

import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.model.ToolMergeMode;
import io.agentscope.core.agui.processor.AguiRequestProcessor;
import io.agentscope.core.agui.processor.AgentResolver;
import io.agentscope.core.agent.Agent;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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
 * 本配置将 {@link HarnessAgent} 包装为 {@link AguiAgentAdapter}，
 * 并装配 {@link AguiRequestProcessor} 处理前端 AG-UI 请求：
 * <ul>
 *   <li>{@link AguiAdapterConfig}：适配器配置（工具合并模式/状态事件/推理事件/超时）</li>
 *   <li>{@link AguiAgentAdapter}：把 HarnessAgent 包装为 AG-UI 适配器，输出 AG-UI 事件流</li>
 *   <li>{@link AguiRequestProcessor}：请求处理器，解析 RunAgentInput 并路由到 HarnessAgent</li>
 * </ul>
 * </p>
 * <p>
 * 配置示例（Nacos application-config.yml）：
 * <pre>
 * scope:
 *   agentscope:
 *     advanced:
 *       ag-ui-enabled: true
 * </pre>
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

    /**
     * HarnessAgent（由 HarnessAgentConfig 装配的系统主入口 Agent）
     * <p>
     * AG-UI 适配器将其包装为 AG-UI 协议兼容的事件源，供前端消费。
     * </p>
     */
    private final HarnessAgent harnessAgent;

    /**
     * AG-UI 适配器配置 Bean。
     * <p>
     * 配置项：
     * <ul>
     *   <li>工具合并模式：AGENT_ONLY（仅使用 Agent 自身工具，忽略前端注入工具）</li>
     *   <li>状态事件：开启，实时同步 Agent 运行状态到前端</li>
     *   <li>工具调用参数事件：开启，流式输出工具调用参数</li>
     *   <li>推理事件：开启，输出思考过程（REASONING_MESSAGE）</li>
     *   <li>运行超时：{@link BusinessConst#AGUI_RUN_TIMEOUT_MINUTES} 分钟</li>
     *   <li>默认 Agent 标识：{@link BusinessConst#AGUI_DEFAULT_AGENT_ID}</li>
     * </ul>
     * </p>
     *
     * @return AG-UI 适配器配置
     */
    @Bean
    public AguiAdapterConfig aguiAdapterConfig() {
        try {
            AguiAdapterConfig config = AguiAdapterConfig.builder()
                    .toolMergeMode(ToolMergeMode.AGENT_ONLY)
                    .emitStateEvents(true)
                    .emitToolCallArgs(true)
                    .enableReasoning(true)
                    .runTimeout(Duration.ofMinutes(BusinessConst.AGUI_RUN_TIMEOUT_MINUTES))
                    .defaultAgentId(BusinessConst.AGUI_DEFAULT_AGENT_ID)
                    .build();
            log.info("[AgUiConfig] AG-UI 适配器配置装配完成: toolMergeMode={}, defaultAgentId={}, timeout={}min",
                    config.getToolMergeMode(), config.getDefaultAgentId(), BusinessConst.AGUI_RUN_TIMEOUT_MINUTES);
            return config;
        } catch (Exception e) {
            log.error("[AgUiConfig] AG-UI 适配器配置装配失败", e);
            throw new BusinessException(ErrorCode.CONFIG_LOAD_FAILED, "AG-UI 适配器配置装配失败", e);
        }
    }

    /**
     * AG-UI 适配器 Bean：把 HarnessAgent 包装为 {@link AguiAgentAdapter}。
     * <p>
     * 适配器内部完成 AgentScope AgentEvent → AG-UI AguiEvent 的事件映射，
     * 通过 {@link AguiAgentAdapter#run} 输出 SSE 兼容的 AG-UI 事件流，
     * 供 AG-UI 兼容前端（如 CopilotKit）直接消费。
     * </p>
     *
     * @param aguiAdapterConfig AG-UI 适配器配置
     * @return 包装 HarnessAgent 的 AG-UI 适配器
     */
    @Bean
    public AguiAgentAdapter aguiAgentAdapter(AguiAdapterConfig aguiAdapterConfig) {
        try {
            AguiAgentAdapter adapter = new AguiAgentAdapter(harnessAgent, aguiAdapterConfig);
            log.info("[AgUiConfig] AG-UI 适配器装配完成: agent={}, defaultAgentId={}",
                    harnessAgent.getName(), aguiAdapterConfig.getDefaultAgentId());
            return adapter;
        } catch (Exception e) {
            log.error("[AgUiConfig] AG-UI 适配器装配失败", e);
            throw new BusinessException(ErrorCode.CONFIG_LOAD_FAILED, "AG-UI 适配器装配失败", e);
        }
    }

    /**
     * AG-UI 请求处理器 Bean。
     * <p>
     * 装配 {@link AguiRequestProcessor}，通过内置 {@link AgentResolver} 将前端请求路由到
     * {@link HarnessAgent}，完成 RunAgentInput 解析、最新用户消息提取与事件流构建。
     * </p>
     *
     * @param aguiAdapterConfig AG-UI 适配器配置
     * @return AG-UI 请求处理器
     */
    @Bean
    public AguiRequestProcessor aguiRequestProcessor(AguiAdapterConfig aguiAdapterConfig) {
        try {
            // AgentResolver：所有 AG-UI 请求统一路由到 HarnessAgent（单 Agent 场景）
            AgentResolver agentResolver = new AgentResolver() {
                @Override
                public Agent resolveAgent(String threadId, String runId) {
                    return harnessAgent;
                }

                @Override
                public boolean hasMemory(String threadId) {
                    // HarnessAgent 内置长期记忆与上下文压缩，支持跨会话记忆
                    return true;
                }
            };
            AguiRequestProcessor processor = AguiRequestProcessor.builder()
                    .agentResolver(agentResolver)
                    .config(aguiAdapterConfig)
                    .build();
            log.info("[AgUiConfig] AG-UI 请求处理器装配完成: agent={}, toolMergeMode={}",
                    harnessAgent.getName(), aguiAdapterConfig.getToolMergeMode());
            return processor;
        } catch (Exception e) {
            log.error("[AgUiConfig] AG-UI 请求处理器装配失败", e);
            throw new BusinessException(ErrorCode.CONFIG_LOAD_FAILED, "AG-UI 请求处理器装配失败", e);
        }
    }
}
