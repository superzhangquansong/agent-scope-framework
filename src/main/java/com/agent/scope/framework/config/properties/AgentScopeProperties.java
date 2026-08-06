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
}
