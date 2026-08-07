package com.agent.scope.framework.middleware;

import com.agent.scope.framework.config.PromptTemplateConfig.PromptTemplateHolder;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 系统提示词热更新中间件（特性44 增强）。
 * <p>
 * 通过 {@link MiddlewareBase#onSystemPrompt} 钩子，在每次组装 system prompt 时
 * 实时从 {@link PromptTemplateHolder} 读取最新提示词，实现 Nacos 配置变更后
 * <b>无需重启服务、无需重建 Agent</b> 即可生效。
 * </p>
 * <p>
 * <b>工作原理</b>：
 * <ol>
 *   <li>Nacos 配置变更 → {@code @RefreshScope} 重建 {@link PromptTemplateHolder} Bean</li>
 *   <li>下次 Agent 推理时，{@code onSystemPrompt} 钩子被触发</li>
 *   <li>本中间件从 holder 读取最新模板，覆盖原 sysPrompt</li>
 *   <li>LLM 使用新提示词推理，热更新完成</li>
 * </ol>
 * </p>
 * <p>
 * <b>降级策略</b>：holder 为空或模板为空时，透传原 sysPrompt（由 HarnessAgent.Builder
 * 构建时传入的内置 DEFAULT_SYSTEM_PROMPT 兜底）。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class PromptRefreshMiddleware implements MiddlewareBase {

    /** 提示词模板持有器（@RefreshScope Bean，Nacos 变更后自动重建） */
    private final PromptTemplateHolder promptTemplateHolder;

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        // 实时从 holder 读取最新提示词，覆盖原 sysPrompt
        String latestPrompt = promptTemplateHolder.getDefaultTemplate();
        if (latestPrompt != null && !latestPrompt.isBlank()) {
            log.debug("[PromptRefresh] 应用 Nacos 热更新提示词: sessionId={}, length={}",
                    ctx.getSessionId(), latestPrompt.length());
            return Mono.just(latestPrompt);
        }
        // holder 为空或模板为空时透传原 sysPrompt（内置 DEFAULT_SYSTEM_PROMPT 兜底）
        return Mono.just(currentPrompt);
    }
}
