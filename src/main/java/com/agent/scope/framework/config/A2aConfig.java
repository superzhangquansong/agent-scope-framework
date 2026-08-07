package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import io.agentscope.core.a2a.server.executor.runner.AgentRunner;
import io.agentscope.core.a2a.server.executor.runner.ReActAgentWithBuilderRunner;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AgentScope 2.0 GA 特性二十五：A2A 协议（Agent2Agent）配置
 * <p>
 * A2A 协议实现 Agent 间标准化互调用，支持跨服务、跨语言协作：
 * <ul>
 *   <li>服务端：将本 Agent 导出为 A2A 服务，供其他 Agent 调用</li>
 *   <li>传输层：JSON-RPC over HTTP（由 {@link AgentScopeA2aServer} 自动装配传输包装器）</li>
 *   <li>元数据：通过 {@link ConfigurableAgentCard} 声明 Agent 能力卡片</li>
 * </ul>
 * </p>
 * <p>
 * A2A 协议核心概念：
 * <ul>
 *   <li>{@link ConfigurableAgentCard}：Agent 元数据卡片（名称/描述/版本/能力声明）</li>
 *   <li>{@link AgentRunner}：Agent 运行器，每个 A2A 请求构建独立 ReActAgent 执行</li>
 *   <li>{@link AgentScopeA2aServer}：A2A 服务端，导出 Agent 为服务并暴露 JSON-RPC 端点</li>
 * </ul>
 * </p>
 * <p>
 * 配置示例（Nacos application-config.yml）：
 * <pre>
 * scope:
 *   agentscope:
 *     advanced:
 *       a2a-enabled: true
 * </pre>
 * 启用后本配置将装配 AgentCard、AgentRunner 与 AgentScopeA2aServer 三个 Bean，
 * 服务端在 {@link BusinessConst#A2A_DEFAULT_PORT} 暴露 A2A JSON-RPC 端点。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(name = "io.a2a.spec.AgentProvider")
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "a2a-enabled", havingValue = "true")
public class A2aConfig {

    private final AgentScopeProperties properties;

    /**
     * DashScope 聊天模型（由 ReactAgentConfig 同源注入，用于构建 A2A 运行器内的 ReActAgent）
     */
    private final DashScopeChatModel dashScopeModel;

    /**
     * 工具容器（已注册全部业务工具，A2A 调用方复用同一套工具能力）
     */
    private final Toolkit toolkit;

    /**
     * A2A AgentCard Bean：Agent 元数据卡片（能力声明）。
     * <p>
     * 声明本 Agent 的名称、描述、版本、输入输出模式与首选传输协议，
     * 供其他 Agent 通过 AgentCard 发现并调用本服务。
     * </p>
     *
     * @return A2A Agent 能力卡片
     */
    @Bean
    public ConfigurableAgentCard a2aAgentCard() {
        try {
            String agentName = properties.getHarnessAgentName();
            ConfigurableAgentCard card = new ConfigurableAgentCard.Builder()
                    .name(agentName)
                    .description(properties.getRootAgentDescription())
                    .version(BusinessConst.A2A_DEFAULT_VERSION)
                    .url("http://localhost:" + BusinessConst.A2A_DEFAULT_PORT + "/")
                    .defaultInputModes(List.of(BusinessConst.A2A_DEFAULT_MODE_TEXT))
                    .defaultOutputModes(List.of(BusinessConst.A2A_DEFAULT_MODE_TEXT))
                    .preferredTransport(BusinessConst.A2A_TRANSPORT_JSON_RPC)
                    .build();
            log.info("[A2aConfig] AgentCard 装配完成: name={}, version={}, transport={}",
                    card.getName(), card.getVersion(), card.getPreferredTransport());
            return card;
        } catch (Exception e) {
            log.error("[A2aConfig] AgentCard 装配失败", e);
            throw new BusinessException(ErrorCode.CONFIG_LOAD_FAILED, "A2A AgentCard 装配失败", e);
        }
    }

    /**
     * A2A AgentRunner Bean：基于 ReActAgent.Builder 的运行器。
     * <p>
     * 每个 A2A 请求由 {@link ReActAgentWithBuilderRunner} 基于 Builder 构建独立 ReActAgent 实例执行，
     * 复用与主 Agent 同源的模型与工具容器，保证 A2A 调用方获得一致的推理与工具能力。
     * </p>
     *
     * @return A2A Agent 运行器
     */
    @Bean
    public AgentRunner a2aAgentRunner() {
        try {
            ReActAgent.Builder agentBuilder = ReActAgent.builder()
                    .name(properties.getHarnessAgentName())
                    .model(dashScopeModel)
                    .toolkit(toolkit)
                    .maxIters(properties.getRootAgentMaxIters());
            AgentRunner runner = ReActAgentWithBuilderRunner.newInstance(agentBuilder);
            log.info("[A2aConfig] AgentRunner 装配完成: agentName={}, maxIters={}",
                    runner.getAgentName(), properties.getRootAgentMaxIters());
            return runner;
        } catch (Exception e) {
            log.error("[A2aConfig] AgentRunner 装配失败", e);
            throw new BusinessException(ErrorCode.CONFIG_LOAD_FAILED, "A2A AgentRunner 装配失败", e);
        }
    }

    /**
     * AgentScopeA2aServer Bean：A2A 服务端。
     * <p>
     * 装配 AgentCard 与 AgentRunner，构建 A2A 服务端并暴露 JSON-RPC 端点。
     * 构建完成后调用 {@link AgentScopeA2aServer#postEndpointReady()} 通知传输层端点就绪，
     * 正式对外提供 A2A 协议服务。
     * </p>
     * <p>
     * 注意：服务端内部通过 {@code AgentScopeAgentCardConverter} 转换卡片并装配 JSON-RPC 传输包装器，
     * 该过程依赖 {@code io.a2a.spec.*} 类型（由 agentscope 主包间接引用）。
     * 若运行时缺失相关依赖，将抛出 {@link BusinessException}（CONFIG_001）以便快速定位。
     * </p>
     *
     * @param a2aAgentRunner A2A Agent 运行器
     * @param a2aAgentCard   A2A Agent 能力卡片
     * @return A2A 服务端实例
     */
    @Bean
    public AgentScopeA2aServer agentScopeA2aServer(AgentRunner a2aAgentRunner, ConfigurableAgentCard a2aAgentCard) {
        try {
            AgentScopeA2aServer server = AgentScopeA2aServer.builder(a2aAgentRunner)
                    .agentCard(a2aAgentCard)
                    .deploymentProperties(BusinessConst.A2A_DEFAULT_PORT)
                    .build();
            server.postEndpointReady();
            log.info("[A2aConfig] AgentScopeA2aServer 装配完成: port={}, agentName={}",
                    BusinessConst.A2A_DEFAULT_PORT, a2aAgentRunner.getAgentName());
            return server;
        } catch (Throwable t) {
            // 捕获 Throwable：服务端构建会触发 io.a2a.spec.* 类型的链接解析，
            // 当 io.a2a SDK 运行时缺失时会抛出 NoClassDefFoundError（Error 非 Exception），
            // 此处统一转换为 BusinessException 以给出清晰可读的错误信息。
            log.error("[A2aConfig] AgentScopeA2aServer 装配失败", t);
            throw new BusinessException(ErrorCode.CONFIG_LOAD_FAILED, "A2A Server 装配失败", t);
        }
    }
}
