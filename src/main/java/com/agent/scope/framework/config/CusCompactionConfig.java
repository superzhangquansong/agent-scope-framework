package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zqs
 * @title: CompactionConfig
 * @projectName agent-scope-framework
 * @description:
 * @date 2026/8/6 09:17
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.compaction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CusCompactionConfig {

    private final AgentScopeProperties properties;

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

        // 工具参数截断（maxArgLength > 0 时启用）
        if (memory.getMaxArgLength() > 0) {
            CompactionConfig.TruncateArgsConfig truncateArgsConfig = CompactionConfig.TruncateArgsConfig.builder()
                    .maxArgLength(memory.getMaxArgLength())
                    .truncationText(memory.getTruncationText())
                    .build();
            builder.truncateArgs(truncateArgsConfig);
        }

        log.info("[AgentScopeConfig] CompactionConfig: triggerMessages={}, triggerTokens={}, keepMessages={}",
                memory.getTriggerMessages(), memory.getTriggerTokens(), memory.getKeepMessages());
        return builder.build();
    }
}