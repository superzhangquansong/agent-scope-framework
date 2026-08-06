package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
     * 默认系统提示词（HarnessAgent 增强版，强制工具调用 + 智能路由）
     * <p>
     * 当 Nacos 未配置 scope.agentscope.prompt-templates.default-prompt 时使用此内置模板。
     * 建议在 Nacos 中配置自定义提示词以实现热更新。
     * </p>
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是「智能生活助手（增强版）」，基于 AgentScope HarnessAgent 架构运行。

            【核心原则 — 必须遵守】
            1. 所有业务请求必须通过调用工具完成，禁止直接编造业务信息回复
            2. 用户意图由你自主理解，不需要用户使用固定格式表达
            3. 多意图指令需拆分并行处理（如"开灯并查设备"→ 调用多个工具）
            4. 工具调用失败时向用户说明原因，不编造成功结果

            【可用工具清单】
            - query_device_list: 查询当前房屋所有设备列表
            - query_device_detail: 查询设备状态详情（需设备ID）
            - batch_control_device: 批量控制设备（需构造actions）
            - search_product: 搜索产品列表
            - query_home_list: 查询房屋列表

            【增强能力】
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
     * 分布式状态存储（由 StateStoreConfig 注入，Redis 分布式后端）
     * 替代默认的 JsonFileAgentStateStore，支持跨节点会话恢复
     */
    private final Optional<AgentStateStore> agentStateStore;

    /**
     * 工作区路径（由 WorkspaceConfig 注入）
     * 确保不生成 .agentscope 本地文件夹
     */
    private final Optional<Path> agentWorkspacePath;

    /**
     * 权限上下文（由 PermissionConfig 注入，条件装配）
     * 启用后拦截敏感工具调用，支持三态决策（允许/审批/拒绝）
     */
    private final Optional<PermissionContextState> permissionContextState;

    /**
     * 中间件链（由 MiddlewareChainConfig 注入）
     * 五阶段洋葱+管道混合模型：onAgent/onReasoning/onActing/onModelCall/onSystemPrompt
     */
    private final Optional<List<MiddlewareBase>> middlewareChain;

    /**
     * 文件系统规范（由 FilesystemConfig 注入，特性20）
     * 提供 Agent 文件读写能力（read_file/write_file/edit_file/grep/glob/ls）
     * 注意：LocalFilesystemSpec 非直接 AbstractFilesystem 子类，此处仅记录就绪状态，
     * 不直接装配到 Builder（避免类型不匹配）。
     */
    private final Optional<LocalFilesystemSpec> filesystemSpec;

    /**
     * 技能仓库（由 SkillRepositoryConfig 注入，特性28/33）
     * 支持 Markdown 技能沉淀与动态加载
     */
    private final Optional<AgentSkillRepository> skillRepository;

    /**
     * 提示词模板持有器（由 PromptTemplateConfig 注入，特性44）
     * 从 Nacos 动态加载系统提示词，支持热更新
     */
    private final Optional<PromptTemplateConfig.PromptTemplateHolder> promptTemplateHolder;

    /**
     * 子 Agent 声明列表（由 SubagentConfig 注入，特性22）
     * 父 Agent 委派任务给子 Agent 并行执行，最后汇总结果
     */
    private final Optional<List<SubagentDeclaration>> subagentDeclarations;


    /**
     * HarnessAgent 构建器 Bean
     * <p>
     * 创建一个预配置的 HarnessAgent.Builder 实例，整合以下工程能力：
     * - Agent 名称：scope-harness
     * - 系统提示词：增强版智能家居助手角色设定
     * - 模型：DashScopeChatModel（通义千问）
     * - 工具容器：已注册全部业务工具的 Toolkit
     * - 上下文压缩：CompactionConfig（结构化压缩 + 滑动窗口）
     * - 分布式状态存储：RedisAgentStateStore（跨节点会话恢复）
     * - 工作区：独立临时工作目录（严禁 .agentscope 文件夹）
     * - 中间件链：五阶段洋葱模型（OtelTracing + 自定义）
     * - 权限系统：三态决策（ALLOW/ASK/DENY，条件装配）
     * </p>
     *
     * @return 预配置的 HarnessAgent.Builder 实例
     */
    @Bean
    public HarnessAgent.Builder scopeHarnessAgentBuilder() {
        // 特性44：优先从 Nacos 动态加载系统提示词，回退到内置默认模板
        String systemPrompt = promptTemplateHolder
                .map(PromptTemplateConfig.PromptTemplateHolder::getDefaultTemplate)
                .filter(p -> p != null && !p.isBlank())
                .orElse(DEFAULT_SYSTEM_PROMPT);
        // Agent 名称从 Nacos 配置读取，避免硬编码
        String agentName = properties.getHarnessAgentName();
        log.info("[HarnessAgentConfig] 构建 HarnessAgent.Builder: name={}, promptSource={}",
                agentName,
                promptTemplateHolder.map(h -> "nacos").orElse("builtin"));

        HarnessAgent.Builder builder = HarnessAgent.Builder
                .fromAgent(reActAgent)
                .name(agentName)
                .sysPrompt(systemPrompt)
                .model(dashScopeModel)
                .maxIters(properties.getRootAgentMaxIters())
                .toolkit(toolkit)
                .compaction(compactionConfig);

        // 特性6/34：分布式状态存储（Redis），替代默认 JsonFileAgentStateStore
        agentStateStore.ifPresent(store -> {
            builder.stateStore(store);
            log.info("[HarnessAgentConfig] 已装配分布式状态存储: {}", store.getClass().getSimpleName());
        });

        // 特性18：工作区路径配置（严禁 .agentscope 文件夹）
        agentWorkspacePath.ifPresent(path -> {
            builder.workspace(path);
            log.info("[HarnessAgentConfig] 已装配工作区: path={}", path.toAbsolutePath());
        });

        // 特性12/13/23/35/39：中间件链（五阶段洋葱+管道混合模型）
        // 注意：PlanModeMiddleware 实现的是 HarnessRuntimeMiddleware 而非 MiddlewareBase，
        // 无法并入此 middlewares 列表，故不在此处装配。
        List<MiddlewareBase> effectiveMiddlewares = new java.util.ArrayList<>();
        middlewareChain.ifPresent(effectiveMiddlewares::addAll);
        if (!effectiveMiddlewares.isEmpty()) {
            builder.middlewares(effectiveMiddlewares);
            log.info("[HarnessAgentConfig] 已装配中间件链: count={}", effectiveMiddlewares.size());
        }

        // 特性14/15：权限系统（三态决策，条件装配）
        permissionContextState.ifPresent(permCtx -> {
            builder.permissionContext(permCtx);
            log.info("[HarnessAgentConfig] 已装配权限系统: mode={}", permCtx.getClass().getSimpleName());
        });

        // 特性20：文件系统规范已就绪，暂不直接装配到 Builder（类型不匹配，留待后续扩展）
        filesystemSpec.ifPresent(fs -> {
            log.info("[HarnessAgentConfig] 文件系统规范已就绪（暂未装配到 Builder）: {}", fs.getClass().getSimpleName());
        });

        // 特性28/33：技能仓库（Markdown 技能沉淀与动态加载）
        skillRepository.ifPresent(repo -> {
            builder.skillRepository(repo);
            log.info("[HarnessAgentConfig] 已装配技能仓库: skills={}", repo.getAllSkillNames());
        });

        // 特性22：子 Agent 声明（父 Agent 委派任务给子 Agent 并行执行）
        subagentDeclarations.ifPresent(declarations -> {
            if (!declarations.isEmpty()) {
                builder.subagents(declarations);
                log.info("[HarnessAgentConfig] 已装配子 Agent: count={}, names={}",
                        declarations.size(),
                        declarations.stream().map(SubagentDeclaration::getName).toList());
            }
        });

        log.info("[HarnessAgentConfig] HarnessAgent.Builder 构建完成，整合 Workspace/记忆/会话持久化/权限/中间件/技能仓库/子Agent");
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
    @Primary
    public HarnessAgent scopeHarnessAgent(HarnessAgent.Builder scopeHarnessAgentBuilder) {
        log.info("[HarnessAgentConfig] 创建默认 HarnessAgent 实例，作为系统主入口 Agent: name={}", properties.getHarnessAgentName());

        HarnessAgent agent = scopeHarnessAgentBuilder.build();
        return agent;
    }
}
