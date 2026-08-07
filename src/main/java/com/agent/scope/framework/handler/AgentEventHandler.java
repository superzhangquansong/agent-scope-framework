package com.agent.scope.framework.handler;

import io.agentscope.core.event.AgentEvent;

/**
 * Agent 事件处理器策略接口。
 * <p>每个具体处理器负责一种 {@link AgentEvent} 子类的转发与落库逻辑。
 * 通过 {@link AgentEventHandlerRegistry} 注册表自动收集，消除 ChatService 中的 if-else 链。</p>
 *
 * @param <T> 该处理器能处理的 AgentEvent 子类型
 * @author zqs
 * @since 2.0.0
 */
public interface AgentEventHandler<T extends AgentEvent> {

    /**
     * 返回该处理器能处理的事件类型（用于注册表建立 Class→Handler 映射）。
     *
     * @return 事件 Class
     */
    Class<T> getEventType();

    /**
     * 处理事件：构造 SSE BO、发送到前端、累积到 recorder。
     *
     * @param ctx   事件处理上下文
     * @param event 待处理事件（已由注册表按类型匹配）
     * @throws Exception 处理过程中的异常（IO/序列化等）
     */
    void handle(EventContext ctx, T event) throws Exception;
}
