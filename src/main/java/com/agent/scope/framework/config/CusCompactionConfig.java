package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.config.properties.DashScopeProperties;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 上下文压缩配置。
 * <p>
 * 核心问题：压缩默认复用主模型（qwen-plus），每次摘要调用耗时 10-15 秒，
 * 严重阻塞 ReAct 循环。通过为压缩指定独立的快速模型（qwen-turbo），
 * 将摘要耗时降至 2-4 秒。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.compaction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CusCompactionConfig {

    private final AgentScopeProperties properties;
    private final DashScopeProperties dashScopeProperties;

    @Bean
    public CompactionConfig compactionConfig() {
        AgentScopeProperties.Memory memory = properties.getMemory();
        CompactionConfig.Builder builder = CompactionConfig.builder()
                .triggerMessages(memory.getTriggerMessages())
                .triggerTokens(memory.getTriggerTokens())
                .keepMessages(memory.getKeepMessages())
                .keepTokens(memory.getKeepTokens())
                .flushBeforeCompact(memory.isFlushBeforeCompact())
                .offloadBeforeCompact(memory.isOffloadBeforeCompact());

        // 为压缩配置独立的快速模型，避免复用主模型（qwen-plus）导致 10-15 秒延迟
        String compactionModelName = memory.getCompactionModel();
        if (compactionModelName != null && !compactionModelName.isBlank()) {
            DashScopeChatModel compactionModel = DashScopeChatModel.builder()
                    .apiKey(dashScopeProperties.getApiKey())
                    .modelName(compactionModelName)
                    .stream(false) // 压缩摘要无需流式，直接等待完整响应
                    .formatter(new DashScopeChatFormatter())
                    .build();
            builder.model(compactionModel);
            log.info("[CompactionConfig] 已装配独立压缩模型: {}", compactionModelName);
        }

        // 工具参数截断（maxArgLength > 0 时启用）
        if (memory.getMaxArgLength() > 0) {
            CompactionConfig.TruncateArgsConfig truncateArgsConfig = CompactionConfig.TruncateArgsConfig.builder()
                    .maxArgLength(memory.getMaxArgLength())
                    .truncationText(memory.getTruncationText())
                    .build();
            builder.truncateArgs(truncateArgsConfig);
        }

        log.info("[CompactionConfig] triggerMessages={}, triggerTokens={}, keepMessages={}, compactionModel={}",
                memory.getTriggerMessages(), memory.getTriggerTokens(), memory.getKeepMessages(),
                compactionModelName != null ? compactionModelName : "(default=主模型)");
        return builder.build();
    }
}
