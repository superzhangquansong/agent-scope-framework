package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.nacos.mcp.NacosMcpServerManager;
import io.agentscope.extensions.nacos.mcp.client.NacosMcpClientBuilder;
import io.agentscope.extensions.nacos.mcp.client.NacosMcpClientWrapper;
import io.agentscope.extensions.nacos.mcp.tool.NacosMcpTool;
import io.agentscope.extensions.nacos.mcp.tool.NacosMcpToolBuilder;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * AgentScope 2.0 GA 特性二十六：MCP（Model Context Protocol）配置
 * <p>
 * MCP 是 Anthropic 提出的标准化外部工具调用协议，AgentScope 通过 Nacos 注册中心
 * 动态发现 MCP 服务器，将其工具注册为 AgentScope 工具：
 * <ul>
 *   <li>标准化协议：任何 MCP 兼容服务器均可接入（通过 Nacos 注册中心统一管理）</li>
 *   <li>动态发现：自动从 Nacos 获取 MCP 服务器详情（地址/协议/工具列表）</li>
 *   <li>统一调用：通过 Toolkit.registerAgentTool() 统一管理，Agent 无感知差异</li>
 *   <li>热更新：Nacos 配置变更时自动刷新工具列表（NacosMcpServerManager 内置订阅）</li>
 * </ul>
 * </p>
 * <p>
 * 装配链路：
 * <ol>
 *   <li>从 Spring 环境读取 Nacos 连接信息（serverAddr/namespace/username/password）</li>
 *   <li>构建 {@link NacosMcpServerManager}（内部创建 AiService 并订阅 MCP Server 变更）</li>
 *   <li>遍历配置的 MCP 服务器列表，为每个服务器创建 {@link NacosMcpClientWrapper}</li>
 *   <li>调用 {@link NacosMcpClientWrapper#initialize()} 建立连接</li>
 *   <li>通过 {@link NacosMcpToolBuilder} 构建工具列表并注册到 {@link Toolkit}</li>
 * </ol>
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
 *           include-tools: [read_file, write_file]
 *         - name: search-mcp
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

    /** Nacos 服务器地址（从 Spring 环境注入，与 application.yaml 中 nacos.discovery.server-addr 一致） */
    @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:30104}")
    private String nacosServerAddr;

    /** Nacos 命名空间（从 Spring 环境注入） */
    @Value("${spring.cloud.nacos.discovery.namespace:agent-scope-framework-dev}")
    private String nacosNamespace;

    /** Nacos 用户名（从 Spring 环境注入） */
    @Value("${spring.cloud.nacos.discovery.username:nacos}")
    private String nacosUsername;

    /** Nacos 密码（从 Spring 环境注入） */
    @Value("${spring.cloud.nacos.discovery.password:nacos}")
    private String nacosPassword;

    /** 已创建的 MCP 客户端列表（销毁时统一关闭） */
    private final List<NacosMcpClientWrapper> createdClients = new ArrayList<>();

    /**
     * NacosMcpServerManager Bean：MCP 服务器管理器。
     * <p>
     * 基于 Nacos 连接信息构建 {@link NacosMcpServerManager}，内部通过
     * {@code AiFactory.createAiService(properties)} 创建 AiService，
     * 支持从 Nacos 动态发现 MCP Server 详情并订阅变更通知。
     * </p>
     *
     * @return MCP 服务器管理器
     */
    @Bean
    public NacosMcpServerManager nacosMcpServerManager() {
        try {
            Properties nacosProps = new Properties();
            nacosProps.setProperty(BusinessConst.NACOS_PROP_KEY_SERVER_ADDR, nacosServerAddr);
            nacosProps.setProperty(BusinessConst.NACOS_PROP_KEY_NAMESPACE, nacosNamespace);
            nacosProps.setProperty(BusinessConst.NACOS_PROP_KEY_USERNAME, nacosUsername);
            nacosProps.setProperty(BusinessConst.NACOS_PROP_KEY_PASSWORD, nacosPassword);

            NacosMcpServerManager manager = NacosMcpServerManager.from(nacosProps);
            log.info("[McpConfig] NacosMcpServerManager 装配完成: serverAddr={}, namespace={}",
                    nacosServerAddr, nacosNamespace);
            return manager;
        } catch (Exception e) {
            log.error("[McpConfig] NacosMcpServerManager 初始化失败: serverAddr={}", nacosServerAddr, e);
            throw new BusinessException(ErrorCode.MCP_SERVER_MANAGER_INIT_FAILED,
                    "MCP 服务器管理器初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * MCP 客户端初始化 Bean。
     * <p>
     * 启动时遍历 {@code scope.agentscope.mcp.servers} 配置列表，
     * 为每个 MCP 服务器创建 {@link NacosMcpClientWrapper}，初始化连接后
     * 通过 {@link NacosMcpToolBuilder} 构建工具列表并注册到 {@link Toolkit}。
     * </p>
     * <p>
     * 注册后的工具名与 MCP Server 中定义的工具名一致，
     * Agent 可像调用普通工具一样通过 LLM 工具调用机制使用 MCP 工具。
     * </p>
     *
     * @param nacosMcpServerManager MCP 服务器管理器
     * @return 注册结果标识
     */
    @Bean
    public String mcpClientInitializer(NacosMcpServerManager nacosMcpServerManager) {
        List<AgentScopeProperties.McpServer> servers = properties.getMcp().getServers();
        if (servers == null || servers.isEmpty()) {
            log.warn("[McpConfig] 未配置 MCP 服务器，MCP 工具注册跳过");
            return "mcp-empty";
        }

        int totalRegistered = 0;
        List<String> serverNames = new ArrayList<>();
        for (AgentScopeProperties.McpServer serverCfg : servers) {
            if (serverCfg.getName() == null || serverCfg.getName().isBlank()) {
                log.warn("[McpConfig] 跳过未命名的 MCP 服务器配置");
                continue;
            }
            try {
                int count = registerMcpServerTools(nacosMcpServerManager, serverCfg);
                totalRegistered += count;
                serverNames.add(serverCfg.getName() + "(" + count + ")");
            } catch (Exception e) {
                log.error("[McpConfig] 注册 MCP 服务器工具失败: server={}", serverCfg.getName(), e);
                throw new BusinessException(ErrorCode.MCP_TOOL_REGISTER_FAILED,
                        "注册 MCP 工具失败: " + serverCfg.getName(), e);
            }
        }

        log.info("[McpConfig] MCP 协议装配完成: serverCount={}, totalTools={}, servers={}",
                servers.size(), totalRegistered, serverNames);
        return "mcp-registered-" + totalRegistered;
    }

    /**
     * 为单个 MCP 服务器创建客户端、初始化并注册工具到 Toolkit。
     * <p>
     * 流程：
     * <ol>
     *   <li>通过 {@link NacosMcpClientBuilder} 创建异步客户端</li>
     *   <li>调用 {@link NacosMcpClientWrapper#initialize()} 建立连接（block 等待初始化完成）</li>
     *   <li>通过 {@link NacosMcpToolBuilder} 构建工具列表（支持 include/exclude 过滤）</li>
     *   <li>遍历工具列表，逐个调用 {@link Toolkit#registerAgentTool} 注册</li>
     * </ol>
     * </p>
     *
     * @param serverManager MCP 服务器管理器
     * @param serverCfg     MCP 服务器配置
     * @return 注册的工具数量
     */
    private int registerMcpServerTools(NacosMcpServerManager serverManager,
                                        AgentScopeProperties.McpServer serverCfg) {
        String serverName = serverCfg.getName();

        // 1. 创建 MCP 客户端（异步模式，支持非阻塞工具调用）
        NacosMcpClientWrapper client = NacosMcpClientBuilder.create(serverName, serverManager)
                .asyncClient(true)
                .delayInitialize(true)
                .build();

        // 2. 初始化客户端连接（阻塞等待，确保启动时已连接就绪）
        try {
            client.initialize().block();
            log.info("[McpConfig] MCP 客户端初始化成功: server={}", serverName);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MCP_CLIENT_INIT_FAILED,
                    "MCP 客户端初始化失败: " + serverName, e);
        }

        // 注册到管理器以接收服务器配置变更通知（热更新）
        serverManager.registerSubscribeMcpClient(serverName, client);
        createdClients.add(client);

        // 3. 构建工具列表（支持 include/exclude 过滤）
        NacosMcpToolBuilder toolBuilder = NacosMcpToolBuilder.create(client);
        if (serverCfg.getIncludeTools() != null && !serverCfg.getIncludeTools().isEmpty()) {
            toolBuilder.includeTools(serverCfg.getIncludeTools());
        }
        if (serverCfg.getExcludeTools() != null && !serverCfg.getExcludeTools().isEmpty()) {
            toolBuilder.excludeTools(serverCfg.getExcludeTools());
        }
        List<NacosMcpTool> tools = toolBuilder.build();

        // 4. 逐个注册工具到 Toolkit（NacosMcpTool 实现 AgentTool 接口）
        for (NacosMcpTool tool : tools) {
            toolkit.registerAgentTool(tool);
            log.info("[McpConfig] 注册 MCP 工具: server={}, tool={}", serverName, tool.getName());
        }

        return tools.size();
    }

    /**
     * 容器销毁时关闭所有 MCP 客户端连接，释放资源。
     */
    @PreDestroy
    public void shutdown() {
        for (NacosMcpClientWrapper client : createdClients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[McpConfig] 关闭 MCP 客户端失败: {}", e.getMessage());
            }
        }
        log.info("[McpConfig] 已关闭 {} 个 MCP 客户端", createdClients.size());
    }
}
