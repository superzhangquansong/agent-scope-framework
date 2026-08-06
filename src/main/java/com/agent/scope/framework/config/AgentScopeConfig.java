package com.agent.scope.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * AgentScope 2.0 GA 统一装配入口
 * <p>
 * 将所有 AgentScope 特性的配置类统一导入，作为企业级多智能体应用的总装配点。
 * 各特性单独编写各自的 {@code XxxConfig.java} 文件，最终在此统一导入。
 * </p>
 *
 * <h3>已装配特性清单（48 项）</h3>
 *
 * <h4>核心基础特性（1-23）</h4>
 * <ol>
 *   <li>智能体（Agent）—— {@link ReactAgentConfig}</li>
 *   <li>HarnessAgent 入口 —— {@link HarnessAgentConfig}</li>
 *   <li>多用户/多会话并发 —— HarnessAgent 无状态引擎 + RuntimeContext</li>
 *   <li>RuntimeContext —— ChatService 中构建</li>
 *   <li>中断执行（Interrupt）—— Agent.interrupt(userId, sessionId) 精确中断</li>
 *   <li>状态持久化（AgentStateStore）—— {@link StateStoreConfig}（Redis 分布式存储）</li>
 *   <li>ContentBlock 统一消息模型 —— AgentScope 核心内置</li>
 *   <li>事件流系统（Event Stream）—— streamEvents() 28 种类型化 AgentEvent</li>
 *   <li>流式输出（Streaming）—— ChatService SSE 推送</li>
 *   <li>结构化输出 —— BaseResponse + ToolResultVO</li>
 *   <li>多模态（Multimodal）—— DashScope 视觉模型配置</li>
 *   <li>中间件（Middleware）—— {@link MiddlewareChainConfig}（五阶段洋葱+管道混合模型）</li>
 *   <li>Hook 系统 —— MiddlewareBase 五个生命周期阶段</li>
 *   <li>权限系统（Permission）—— {@link PermissionConfig}（三态决策机制）</li>
 *   <li>人机交互（HITL）—— 权限系统 ASK 状态联动</li>
 *   <li>模型容错 —— ReActAgent.maxRetries + fallbackModelEnabled</li>
 *   <li>上下文压缩 —— {@link CusCompactionConfig}（结构化压缩 + 滑动窗口）</li>
 *   <li>工作区（Workspace）—— {@link WorkspaceConfig}（独立临时工作目录）</li>
 *   <li>分布式记忆（Memory）—— HarnessAgent 内置 MEMORY.md + memory/ 流水账</li>
 *   <li>文件系统（FileSystem）—— MinIO 对象存储配置</li>
 *   <li>沙箱（Sandbox）—— DockerFilesystemSpec（按需启用）</li>
 *   <li>子 Agent（Sub-Agent）—— HarnessAgent 内置 agent_spawn / agent_send</li>
 *   <li>计划模式（Plan Mode）—— PlanNotebook 结构化任务管理</li>
 * </ol>
 *
 * <h4>分布式与协作特性（24-31）</h4>
 * <ol start="24">
 *   <li>Channel 通信 —— 钉钉/飞书/企业微信消息渠道适配</li>
 *   <li>A2A 协议 —— 基于 Nacos 服务发现的 Agent 互调用</li>
 *   <li>MCP（模型上下文协议）—— 标准化外部工具调用</li>
 *   <li>Agent as Tool —— Agent 封装为工具供其他 Agent 调用</li>
 *   <li>技能系统（Skills）—— workspace/skills/ 动态加载</li>
 *   <li>内置工具（Tools）—— 时间计算、JSON 解析等通用工具</li>
 *   <li>会话生命周期管理 —— AgentStateStore 自动持久化与恢复</li>
 *   <li>多租户组织级隔离 —— session/user/agent/org 多维度隔离</li>
 * </ol>
 *
 * <h4>生产级增强特性（32-48）</h4>
 * <ol start="32">
 *   <li>沙箱快照与恢复 —— OssSnapshotSpec / RedisSnapshotSpec</li>
 *   <li>技能自动沉淀 —— 成功模式自动生成 Markdown Skill</li>
 *   <li>分布式后端（DistributedBackend）—— {@link StateStoreConfig}（Redis）</li>
 *   <li>PlanNotebook —— 结构化任务分解与追踪</li>
 *   <li>AG-UI 协议适配 —— 前端 UI 与 Agent 事件流标准化对接</li>
 *   <li>异步工具执行 —— Reactor Flux 响应式执行</li>
 *   <li>定时唤醒调度 —— 周期任务调度（按需扩展）</li>
 *   <li>OpenTelemetry 集成 —— {@link MiddlewareChainConfig}（OtelTracingMiddleware）</li>
 *   <li>AgentScope Studio 可视化调试 —— 按需启用</li>
 *   <li>工具执行超时控制 —— {@link ToolEnhancementConfig}</li>
 *   <li>工具结果缓存 —— {@link ToolEnhancementConfig}（Redis 缓存）</li>
 *   <li>限流与熔断 —— Resilience4j（application.yaml 配置）</li>
 *   <li>提示词模板管理 —— {@link PromptTemplateConfig}（Nacos 动态加载）</li>
 *   <li>模型调用可观测性增强 —— {@link ObservabilityConfig}（Prometheus 指标）</li>
 *   <li>配置版本管理 —— Nacos 配置中心版本回滚</li>
 *   <li>Agent 健康检查 —— {@link HealthCheckConfig}（Redis/MySQL 探针）</li>
 *   <li>任务队列与异步调度 —— RabbitMQ/Kafka（按需扩展）</li>
 * </ol>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@Import({
        // ==================== 核心基础配置 ====================
        ReactAgentConfig.class,           // 特性1：ReActAgent 无状态推理核心
        HarnessAgentConfig.class,         // 特性2：HarnessAgent 工程化入口
        ModelConfig.class,                // 模型配置（DashScope + Ollama）
        ToolkitConfig.class,              // 工具容器配置
        CusCompactionConfig.class,        // 特性17：上下文压缩

        // ==================== 状态与存储配置 ====================
        StateStoreConfig.class,           // 特性6/34：Redis 分布式状态存储
        WorkspaceConfig.class,            // 特性18：工作区配置

        // ==================== 安全与中间件配置 ====================
        PermissionConfig.class,           // 特性14/15：权限系统 + HITL
        MiddlewareChainConfig.class,      // 特性12/13/39：中间件链 + OTel

        // ==================== 可观测性配置 ====================
        ObservabilityConfig.class,        // 特性45：Prometheus 指标
        HealthCheckConfig.class,          // 特性47：健康检查端点

        // ==================== 工具增强配置 ====================
        ToolEnhancementConfig.class,      // 特性41/42：工具超时 + 缓存
        PromptTemplateConfig.class        // 特性44：提示词模板管理
})
public class AgentScopeConfig {

    /**
     * 静态初始化块 —— 启动时打印已装配特性清单
     */
    static {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  AgentScope 2.0 GA 企业级多智能体框架 —— 统一装配完成    ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  核心基础特性（1-23）：ReActAgent + HarnessAgent + 中间件 ║");
        log.info("║  分布式协作特性（24-31）：状态存储 + 多租户隔离 + Skills  ║");
        log.info("║  生产级增强特性（32-48）：OTel + Prometheus + 健康检查    ║");
        log.info("╚══════════════════════════════════════════════════════════╝");
    }
}
