package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 GA 特性二十五：A2A 协议（Agent2Agent）配置
 * <p>
 * A2A 协议实现 Agent 间标准化互调用，支持跨服务、跨语言协作：
 * <ul>
 *   <li>服务端：将本 Agent 导出为 A2A 服务，供其他 Agent 调用</li>
 *   <li>客户端：{@code A2aAgent} 调用远程 A2A 服务</li>
 *   <li>服务发现：集成 Nacos，自动发现远程 Agent 卡片（AgentCard）</li>
 *   <li>传输层：JSON-RPC over HTTP</li>
 * </ul>
 * </p>
 * <p>
 * A2A 协议核心概念：
 * <ul>
 *   <li>{@code AgentCard}：Agent 元数据卡片（能力声明）</li>
 *   <li>{@code AgentScopeA2aServer}：A2A 服务端，导出 Agent 为服务</li>
 *   <li>{@code A2aAgent}：A2A 客户端，调用远程 Agent</li>
 *   <li>{@code NacosAgentCardResolver}：基于 Nacos 的 AgentCard 解析</li>
 * </ul>
 * </p>
 * <p>
 * 注意：当前依赖的 AgentScope 版本暂未提供 {@code io.agentscope.core.a2a.*} API，
 * 本配置类作为占位保留，启用后仅输出日志，待官方 API 落地后再补充装配。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "a2a-enabled", havingValue = "true")
public class A2aConfig {

    private final AgentScopeProperties properties;

    static {
        log.info("[A2aConfig] A2A 协议配置已启用（占位模式：当前 agentscope 版本未提供 a2a API，暂不装配服务端）");
    }
}
