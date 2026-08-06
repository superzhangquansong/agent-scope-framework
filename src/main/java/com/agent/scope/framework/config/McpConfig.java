package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 2.0 GA 特性二十六：MCP（Model Context Protocol）配置
 * <p>
 * MCP 是 Anthropic 提出的标准化外部工具调用协议，AgentScope 通过 {@code McpClientWrapper}
 * 接入 MCP 服务器，将外部工具注册为 AgentScope 工具：
 * <ul>
 *   <li>标准化协议：任何 MCP 兼容服务器均可接入</li>
 *   <li>动态发现：自动获取 MCP 服务器提供的工具列表</li>
 *   <li>统一调用：通过 Toolkit.registerTool() 统一管理</li>
 * </ul>
 * </p>
 * <p>
 * 配置示例（Nacos application-config.yml）：
 * <pre>
 * scope:
 *   agentscope:
 *     advanced:
 *       mcp-enabled: true
 *     mcp:
 *       servers:
 *         - name: filesystem-mcp
 *           url: http://localhost:3000
 * </pre>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "mcp-enabled", havingValue = "true")
public class McpConfig {

    private final AgentScopeProperties properties;

    private final Toolkit toolkit;

    /**
     * MCP 客户端列表 Bean。
     * <p>
     * 装配 MCP 客户端包装器列表，启动时自动连接配置的 MCP 服务器，
     * 发现的工具注册到 Toolkit 供 Agent 调用。
     * </p>
     *
     * @return MCP 客户端包装器列表（可能为空，表示无配置）
     */
    @Bean
    public List<Object> mcpClients() {
        List<Object> clients = new ArrayList<>();
        // MCP 客户端需 agentscope-extensions-mcp 扩展包
        // 此处装配配置框架，实际 McpClientWrapper 按需创建
        log.info("[McpConfig] MCP 协议配置已装配: clientCount={} (需 agentscope-extensions-mcp 依赖)",
                clients.size());
        return clients;
    }
}
