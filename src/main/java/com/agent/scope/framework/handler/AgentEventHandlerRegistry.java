package com.agent.scope.framework.handler;

import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.core.event.AgentEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 事件处理器注册表。
 * <p>Spring 启动时自动收集所有 {@link AgentEventHandler} Bean，按 {@link #getEventType()}
 * 建立 Class→Handler 映射。{@link #dispatch} 方法 O(1) 查找对应处理器并执行。</p>
 * <p>替代 ChatService.forwardAgentEvent 中的 16 个 instanceof if-else 分支。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventHandlerRegistry {

    private final List<AgentEventHandler<?>> handlers;

    /** Class→Handler 映射表 */
    private final Map<Class<?>, AgentEventHandler<?>> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (AgentEventHandler<?> handler : handlers) {
            Class<?> eventType = handler.getEventType();
            registry.put(eventType, handler);
            log.info("[HandlerRegistry] 注册事件处理器: eventType={}, handler={}",
                    eventType.getSimpleName(), handler.getClass().getSimpleName());
        }
        log.info("[HandlerRegistry] 事件处理器注册完成: count={}", registry.size());
    }

    /**
     * 分发事件到对应处理器。
     *
     * @param ctx   事件处理上下文
     * @param event 待处理事件
     * @return true=已找到处理器并执行；false=无匹配处理器（调用方走兜底逻辑）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean dispatch(EventContext ctx, AgentEvent event) {
        AgentEventHandler handler = registry.get(event.getClass());
        if (handler == null) {
            return false;
        }
        try {
            handler.handle(ctx, event);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SSE_EVENT_FORWARD_FAILED,
                    "事件处理失败: eventType=" + event.getClass().getSimpleName(), e);
        }
        return true;
    }
}
