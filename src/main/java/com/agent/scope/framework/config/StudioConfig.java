package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 GA 特性四十：AgentScope Studio 可视化调试配置
 * <p>
 * AgentScope Studio 是官方提供的可视化调试工具，支持：
 * <ul>
 *   <li>对话回放：查看历史对话的完整推理过程</li>
 *   <li>工具调用追踪：可视化工具调用链路与耗时</li>
 *   <li>Token 消耗分析：按会话/模型/工具维度统计 Token 用量</li>
 *   <li>事件流可视化：时间轴展示 AgentEvent 序列</li>
 *   <li>状态检查：查看 AgentState 的完整快照</li>
 * </ul>
 * </p>
 * <p>
 * Studio 通过 HTTP 端点暴露调试数据，开发环境启用，生产环境按需关闭。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "studio-enabled", havingValue = "true")
public class StudioConfig {

    private final AgentScopeProperties properties;

    /**
     * Studio 调试端点初始化 Bean。
     * <p>
     * 启用 Studio 后，通过 {@code http://host:port/studio} 访问可视化调试界面。
     * Studio 读取已落库的对话记录（ChatMessageRecord/ToolCallRecord/ModelCallRecord/
     * TokenUsageRecord）进行可视化展示。
     * </p>
     *
     * @return 初始化标识
     */
    @Bean
    public String studioInitializer() {
        log.info("[StudioConfig] AgentScope Studio 可视化调试已启用: endpoint=/studio");
        return "studio-enabled";
    }
}
