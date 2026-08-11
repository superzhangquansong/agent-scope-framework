package com.agent.scope.framework.middleware;

import com.agent.scope.framework.config.PromptTemplateConfig.PromptTemplateHolder;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.service.DeviceContextService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 系统提示词热更新中间件（特性44 增强）+ 设备列表上下文注入。
 * <p>
 * 通过 {@link MiddlewareBase#onSystemPrompt} 钩子，在每次组装 system prompt 时：
 * <ol>
 *   <li>实时从 {@link PromptTemplateHolder} 读取最新提示词（Nacos 热更新）</li>
 *   <li>从 Redis 读取缓存的设备列表，追加到提示词末尾</li>
 * </ol>
 * </p>
 * <p>
 * <b>设备列表注入</b>：首次调用 query_device_list 后，设备列表被缓存到 Redis。
 * 后续推理时，本中间件将缓存的设备列表注入系统提示词，LLM 可直接使用 deviceId/gatewayId
 * 调用 batch_control_device，无需再调用 query_device_list，每轮节省约 2 秒。
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

    /** 设备列表上下文缓存服务（从 Redis 读取缓存的设备列表） */
    private final DeviceContextService deviceContextService;

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        // 1. 从 holder 读取最新提示词（Nacos 热更新）
        String basePrompt = promptTemplateHolder.getDefaultTemplate();
        if (basePrompt == null || basePrompt.isBlank()) {
            basePrompt = currentPrompt;
        }

        // 2. 从 Redis 读取缓存的设备列表，追加到提示词末尾
        String deviceContext = extractDeviceContext(ctx);
        if (deviceContext != null && !deviceContext.isBlank()) {
            log.debug("[PromptRefresh] 注入设备列表上下文: sessionId={}, promptLen={}",
                    ctx.getSessionId(), basePrompt.length() + deviceContext.length());
            return Mono.just(basePrompt + deviceContext);
        }

        log.debug("[PromptRefresh] 应用系统提示词（无设备缓存）: sessionId={}, length={}",
                ctx.getSessionId(), basePrompt.length());
        return Mono.just(basePrompt);
    }

    /**
     * 从 RuntimeContext 提取 SessionContext，再从 Redis 获取缓存的设备列表。
     *
     * @param ctx 运行时上下文
     * @return 设备列表上下文文本，或 null（缓存未命中）
     */
    private String extractDeviceContext(RuntimeContext ctx) {
        try {
            SessionContext sessionContext = ctx.get(BusinessConst.CTX_KEY_SESSION_CONTEXT);
            if (sessionContext == null || sessionContext.getHouseId() == null) {
                return null;
            }
            return deviceContextService.getCachedDeviceContext(sessionContext.getHouseId());
        } catch (Exception e) {
            log.debug("[PromptRefresh] 读取设备上下文失败: sessionId={}, error={}",
                    ctx.getSessionId(), e.getMessage());
            return null;
        }
    }
}
