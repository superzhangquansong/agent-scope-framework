package com.agent.scope.framework.config;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.RequestStopEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope 2.0 GA 特性五：中断执行（Interrupt）配置
 * <p>
 * 支持运行时中断 Agent 执行及基于状态存储的断点恢复。通过 {@link Agent#interrupt()}
 * 可精确中断指定 (userId, sessionId) 的执行，常用于：
 * <ul>
 *   <li>用户主动取消正在进行的对话（"停下"）</li>
 *   <li>超时强制中断长耗时任务</li>
 *   <li>内容审核触发中断（敏感词命中）</li>
 * </ul>
 * </p>
 * <p>
 * 中断信号通过 {@link io.agentscope.core.interruption.InterruptControl} 传播，
 * ReAct 循环在每次迭代开始时检查中断状态，安全终止推理流程。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.interrupt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InterruptConfig {

    /**
     * 中断信号注册表 Bean。
     * <p>
     * 维护 (userId:sessionId) → 中断原因 的映射，供 Controller 层调用 {@link Agent#interrupt()}
     * 时定位目标 Agent 实例。运行时态信号不持久化，重启后清空。
     * </p>
     *
     * @return 中断信号注册表（线程安全 ConcurrentHashMap）
     */
    @Bean("interruptSignalRegistry")
    public Map<String, String> interruptSignalRegistry() {
        log.info("[InterruptConfig] 中断信号注册表已创建");
        return new ConcurrentHashMap<>();
    }

    /**
     * 请求停止事件工厂 Bean。
     * <p>
     * 中间件可发射 {@link RequestStopEvent} 终止推理，常用于成本控制（Token 超限）
     * 与内容审核（敏感词命中）场景。
     * </p>
     *
     * @return RequestStopEvent 工厂（函数式接口）
     */
    @Bean("requestStopEventFactory")
    public java.util.function.Function<String, RequestStopEvent> requestStopEventFactory() {
        return reason -> {
            log.info("[InterruptConfig] 发射 RequestStopEvent: reason={}", reason);
            return new RequestStopEvent(reason);
        };
    }
}
