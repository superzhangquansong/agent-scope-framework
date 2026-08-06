package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 GA 特性二：HarnessAgent 入口配置
 * <p>
 * HarnessAgent 是 AgentScope 2.0 GA 推荐的 Agent 入口，打包以下工程能力：
 * - Workspace：Agent 执行时的独立临时工作目录
 * - 长期记忆（Memory）：用户级跨会话记忆
 * - 会话持久化：Agent 运行状态持久化至数据库
 * - 子 Agent（Sub-Agent）：父 Agent 委派任务给子 Agent 并行执行
 * - 沙箱（Sandbox）：安全执行用户上传的 Python/Shell 脚本
 * <p>
 * 通过 Middleware 和 Toolkit 通道扩展 ReActAgent，核心推理循环得以保留，仅做增强。
 * 本配置类负责装配 HarnessAgent 构建器及其单例实例。
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.harness-agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HarnessAgentConfig {

    /**
     * 默认 HarnessAgent 名称
     */
    private static final String DEFAULT_AGENT_NAME = "scope-harness";

    /**
     * 默认系统提示词（HarnessAgent 增强版，强制工具调用 + 智能路由）
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是「智能生活助手（增强版）」，基于 AgentScope HarnessAgent 架构运行。
            
            ═══════════════════════════════════════════════════════
            【核心原则 — 必须遵守】
            ═══════════════════════════════════════════════════════
            1. 所有业务请求必须通过调用工具完成，禁止直接编造业务信息回复
            2. 用户意图由你自主理解，不需要用户使用固定格式表达
            3. 多意图指令需拆分并行处理（如"开灯并查设备"→ 调用多个工具）
            4. 工具调用失败时向用户说明原因，不编造成功结果
            
            ═══════════════════════════════════════════════════════
            【可用工具清单】
            ═══════════════════════════════════════════════════════
            - query_device_list: 查询当前房屋所有设备列表
            - query_device_detail: 查询设备状态详情（需设备ID）
            - batch_control_device: 批量控制设备（需构造actions）
            - query_product_list: 搜索产品列表
            - query_home_list: 查询房屋列表
            - add_to_cart: 添加产品到购物车
            - query_cart: 查询购物车
            
            ═══════════════════════════════════════════════════════
            【增强能力】
            ═══════════════════════════════════════════════════════
            - 工作区文件操作：可在独立工作目录中读写文件、保存中间结果
            - 长期记忆：可跨会话记住用户的偏好与历史交互
            - 子任务委派：可将复杂任务拆分并委派给子 Agent 并行执行
            - 状态持久化：执行状态可被保存和恢复，支持任务中断后续接
            """;

    private final AgentScopeProperties properties;

    /**
     * DashScope 聊天模型（由 CoreBeansConfig 注入）
     */
    private final DashScopeChatModel dashScopeModel;

    /**
     * 工具容器（由 CoreBeansConfig 注入，已注册所有业务工具）
     */
    private final Toolkit toolkit;

    private final ReActAgent reActAgent;

    private final CompactionConfig compactionConfig;


    /**
     * HarnessAgent 构建器 Bean
     * <p>
     * 创建一个预配置的 HarnessAgent.Builder 实例，包含：
     * - Agent 名称：scope-harness
     * - 系统提示词：增强版智能家居助手角色设定
     * - 模型：DashScopeChatModel（通义千问）
     * - 工作区：工作区目录路径
     * - 工具容器：已注册全部业务工具的 Toolkit
     * - 中间件链：五阶段洋葱模型中间件列表
     * <p>
     * 注意：compaction（上下文压缩配置）在 AgentScope 2.0 GA 中通过
     * ContextCompressionMiddleware 中间件实现，故此处不再单独配置 compaction 参数。
     * 如需使用 HarnessAgent 原生 compaction 能力，可取消下方注释并配置 CompactionConfig。
     *
     * @return 预配置的 HarnessAgent.Builder 实例
     */
    @Bean
    public HarnessAgent.Builder scopeHarnessAgentBuilder() {
        log.info("[HarnessAgentConfig] 构建 HarnessAgent.Builder: name={}", DEFAULT_AGENT_NAME);

        HarnessAgent.Builder builder = HarnessAgent.Builder
                .fromAgent(reActAgent)
                .name(DEFAULT_AGENT_NAME)
                .sysPrompt(DEFAULT_SYSTEM_PROMPT)
                .model(dashScopeModel)
                .maxIters(properties.getRootAgentMaxIters())
                .toolkit(toolkit)
                .compaction(compactionConfig);

        log.info("[HarnessAgentConfig] HarnessAgent.Builder 构建完成，整合 Workspace/记忆/会话持久化/子Agent/沙箱");
        return builder;
    }

    /**
     * HarnessAgent 实例 Bean
     * <p>
     * 根据构建器配置创建默认的 HarnessAgent 实例。
     * 该实例为单例无状态，通过 Reactor Context 区分不同用户的会话。
     * 作为系统主入口 Agent，承担用户请求的接收、推理、工具调用与响应生成。
     *
     * @param scopeHarnessAgentBuilder 由 {@link #scopeHarnessAgentBuilder()} 创建的 Builder Bean
     * @return HarnessAgent 单例实例
     */
    @Bean
    public HarnessAgent scopeHarnessAgent(HarnessAgent.Builder scopeHarnessAgentBuilder) {
        log.info("[HarnessAgentConfig] 创建默认 HarnessAgent 实例，作为系统主入口 Agent: name={}", DEFAULT_AGENT_NAME);

        HarnessAgent agent = scopeHarnessAgentBuilder.build();
        return agent;
    }
}