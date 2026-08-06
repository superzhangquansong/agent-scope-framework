package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 2.0 GA 特性二十四：Channel 通信配置
 * <p>
 * 接入外部消息平台（钉钉/飞书/企微/GitHub/GitLab），实现 Agent 与 IM 平台的双向通信：
 * <ul>
 *   <li>入站：IM 平台消息 → Channel → Gateway → Agent</li>
 *   <li>出站：Agent 响应 → Gateway → Channel → IM 平台</li>
 * </ul>
 * </p>
 * <p>
 * Channel 系统支持 7 层路由绑定：peer > parentPeer > guild+roles > guild > team > account > channel，
 * 按 (userId, sessionId) 自动隔离会话。
 * </p>
 * <p>
 * 注意：本类命名为 {@code ChannelGatewayConfig}，以避免与 AgentScope 框架内置的
 * {@code io.agentscope.harness.agent.gateway.channel.ChannelConfig} 类型同名冲突。
 * </p>
 * <p>
 * 配置示例（Nacos application-config.yml）：
 * <pre>
 * scope:
 *   agentscope:
 *     advanced:
 *       channel-enabled: true
 *     channel:
 *       bindings:
 *         - platform: feishu
 *           channel-id: feishu-main
 *           default-agent-id: scope-harness
 * </pre>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "channel-enabled", havingValue = "true")
public class ChannelGatewayConfig {

    private final AgentScopeProperties properties;

    /**
     * Channel 网关配置占位 Bean。
     * <p>
     * 装配各平台 Channel 的路由配置。实际 Channel 实例（FeishuChannel/DingTalkChannel 等）
     * 需对应扩展包依赖，此处仅装配配置占位，Channel 实例按需创建。
     * </p>
     *
     * @return Channel 网关配置占位列表
     */
    @Bean
    public List<ChannelGatewayConfig> channelGatewayConfigs() {
        List<ChannelGatewayConfig> configs = new ArrayList<>();
        log.info("[ChannelGatewayConfig] Channel 通信配置已装配: count={}", configs.size());
        return configs;
    }
}
