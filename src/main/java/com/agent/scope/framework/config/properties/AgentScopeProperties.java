package com.agent.scope.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 配置属性
 * <p>
 * 绑定 {@code scope.agentscope.*} 前缀的配置项，支持 Nacos 动态刷新。
 * </p>
 *
 * @author scope-core
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "scope.agentscope")
public class AgentScopeProperties {

    /** 模型供应商：dashscope / ollama */
    private String modelProvider = "dashscope";

    /** 主模型名称（qwen-plus 文本对话与工具调用） */
    private String modelName = "qwen-plus";

    /** 视觉模型名称（qwen-vl-max 图像理解） */
    private String visionModelName = "qwen-vl-max";

    /** 是否启用降级兜底 */
    private boolean fallbackEnabled = true;

    /** Agent 最大迭代次数（ReAct 循环最大次数） */
    private int maxIters = 10;

    /** 上下文压缩阈值（消息条数） */
    private int compactionThreshold = 20;

    /** Root Agent 名称 */
    private String rootAgentName = "root";

    /** Root Agent 描述 */
    private String rootAgentDescription = "智能家居主 Agent，负责 ReAct 推理、工具调度与结果聚合";

    /** Root Agent 最大迭代次数 */
    private int rootAgentMaxIters = 10;

    /** 模型调用重试次数 */
    private int maxRetries = 2;

    /** 是否启用备用模型回退 */
    private boolean fallbackModelEnabled = false;

    /** DashScope 模型配置 */
    private DashScope dashscope = new DashScope();

    /** Ollama 模型配置 */
    private Ollama ollama = new Ollama();

    /** Memory 配置 */
    private Memory memory = new Memory();

    /** Advanced 高级特性配置 */
    private Advanced advanced = new Advanced();

    /** StateStore 分布式状态存储配置 */
    private StateStore stateStore = new StateStore();

    /** Workspace 工作区配置 */
    private Workspace workspace = new Workspace();

    /** Middleware 中间件链配置 */
    private Middleware middleware = new Middleware();

    /** Permission 权限系统配置 */
    private Permission permission = new Permission();

    /** PromptTemplates 提示词模板配置 */
    private PromptTemplates promptTemplates = new PromptTemplates();

    /** Tool 工具增强配置 */
    private Tool tool = new Tool();

    /** Observability 可观测性配置 */
    private Observability observability = new Observability();

    /** HealthCheck 健康检查配置 */
    private HealthCheck healthCheck = new HealthCheck();

    /**
     * 子 Agent 声明列表（支持 Nacos 热加载）。
     *
     * <p>在 Nacos application-config.yml 中配置：
     * <pre>
     * scope:
     *   agentscope:
     *     subagents:
     *       - name: vision
     *         description: 视觉理解子 Agent，处理图像识别与描述
     *       - name: knowledge
     *         description: 知识库查询子 Agent，处理技术参数/规格/说明书
     * </pre>
     * 配置变更后，@RefreshScope 自动重建 Bean，onRefresh 事件触发 HarnessAgent 重建。
     * </p>
     */
    private List<SubagentConfig> subagents = new ArrayList<>();

    /**
     * DashScope 配置内部类。
     */
    @Data
    public static class DashScope {
        /** DashScope API Key（通义千问） */
        private String apiKey = "";

        /** DashScope baseUrl（为空使用默认 endpoint） */
        private String baseUrl = "";

        /** 是否启用流式输出 */
        private boolean stream = true;

        /** 是否启用思考模式（仅 thinking 系列模型） */
        private boolean enableThinking = false;

        /** 思考预算 token 数 */
        private int thinkingBudget = 4096;

        /** 温度参数（工具调用场景使用低温度确保确定性） */
        private double temperature = 0.3;

        /** 是否启用并行工具调用 */
        private boolean parallelToolCalls = false;

        /** 是否启用视觉模型 */
        private boolean visionEnabled = false;
    }

    /**
     * Ollama 配置内部类。
     */
    @Data
    public static class Ollama {
        /** Ollama 服务基址 */
        private String baseUrl = "http://127.0.0.1:11434";

        /** 对话模型名称 */
        private String chatModel = "qwen2.5:1.5b";

        /** 温度参数 */
        private double temperature = 0.3;

        /** 上下文窗口大小 */
        private int numCtx = 32768;

        /** 最大生成 token 数 */
        private int numPredict = 2048;
    }

    /**
     * Memory 记忆配置内部类（特性 18/20：上下文压缩 + 分布式记忆）。
     */
    @Data
    public static class Memory {
        /** 触发压缩的历史消息条数阈值 */
        private int triggerMessages = 20;

        /** 触发压缩的历史 token 阈值 */
        private int triggerTokens = 30000;

        /** 压缩后保留最近的原文消息数 */
        private int keepMessages = 5;

        /** 压缩后保留最近的 token 数 */
        private int keepTokens = 5000;

        /** 压缩前把新事实写入日流水账 */
        private boolean flushBeforeCompact = true;

        /** 压缩前把原始消息另存为日志 */
        private boolean offloadBeforeCompact = true;

        /** 工具参数最大长度（超过则截断） */
        private int maxArgLength = 5000;

        /** 截断提示文本 */
        private String truncationText = "[truncated]";

        /** 大工具结果卸载阈值（字符数） */
        private int evictionMaxResultChars = 20000;

        /** 卸载预览字符数 */
        private int evictionPreviewChars = 500;

        /** 卸载路径 */
        private String evictionPath = "memory/evicted/";
    }

    /**
     * Advanced 高级特性配置内部类。
     */
    @Data
    public static class Advanced {
        /** 是否启用 Plan Mode 中间件 */
        private boolean planModeEnabled = false;

        /** 是否启用 TaskList */
        private boolean taskListEnabled = false;

        /** 是否启用 Memory Tools */
        private boolean memoryToolsEnabled = false;

        /** 是否启用 Skill Repository */
        private boolean skillRepositoryEnabled = false;

        /** 是否启用 OTEL 追踪 */
        private boolean otelTracingEnabled = false;

        /** 是否启用 Permission 系统 */
        private boolean permissionEnabled = false;

        /** 权限询问工具列表 */
        private String permissionAskTools = "";
    }

    /**
     * 子 Agent 配置内部类（Nacos 热加载支持）。
     */
    @Data
    public static class SubagentConfig {
        /** 子 Agent 名称（唯一标识） */
        private String name;

        /** 子 Agent 描述（供 root Agent 调度参考） */
        private String description;
    }

    /**
     * StateStore 分布式状态存储配置内部类（特性 6/34）。
     */
    @Data
    public static class StateStore {
        /** 状态存储类型：redis / memory / json-file */
        private String type = "redis";
    }

    /**
     * Workspace 工作区配置内部类（特性 18）。
     */
    @Data
    public static class Workspace {
        /** 是否启用工作区 */
        private boolean enabled = true;

        /** 工作区目录路径（严禁使用 .agentscope 目录） */
        private String path = "/tmp/agentscope-workspace";
    }

    /**
     * Middleware 中间件链配置内部类（特性 12/13/39）。
     */
    @Data
    public static class Middleware {
        /** 是否启用中间件链 */
        private boolean enabled = true;
    }

    /**
     * Permission 权限系统配置内部类（特性 14/15）。
     */
    @Data
    public static class Permission {
        /** 是否启用权限系统（HITL 人机交互） */
        private boolean enabled = false;

        /** 需人工审批的敏感工具名称列表 */
        private List<String> askTools = new ArrayList<>();
    }

    /**
     * PromptTemplates 提示词模板配置内部类（特性 44）。
     */
    @Data
    public static class PromptTemplates {
        /** 是否启用提示词模板管理 */
        private boolean enabled = true;

        /** 默认提示词模板内容（留空则使用内置模板） */
        private String defaultPrompt = "";
    }

    /**
     * Tool 工具增强配置内部类（特性 41/42）。
     */
    @Data
    public static class Tool {
        /** 是否启用工具增强（超时控制 + 结果缓存） */
        private boolean enhanced = true;

        /** 工具执行超时时间（毫秒） */
        private long timeoutMs = 30000L;

        /** 工具结果缓存 TTL（秒） */
        private long cacheTtlSeconds = 300L;
    }

    /**
     * Observability 可观测性配置内部类（特性 45）。
     */
    @Data
    public static class Observability {
        /** 是否启用可观测性（Prometheus 指标收集） */
        private boolean enabled = true;
    }

    /**
     * HealthCheck 健康检查配置内部类（特性 47）。
     */
    @Data
    public static class HealthCheck {
        /** 是否启用 Agent 健康检查端点 */
        private boolean enabled = true;
    }
}
