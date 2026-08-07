package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.constant.BusinessConst;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.agent.scope.framework.constant.BusinessConst.CTX_KEY_SESSION_CONTEXT;

/**
 * AgentScope 2.0 GA 特性四十八：任务队列与异步调度配置
 * <p>
 * 通过内存任务队列实现异步任务调度，支持：
 * <ul>
 *   <li>异步任务提交：Agent 将耗时任务投递到队列，立即返回 taskId</li>
 *   <li>后台任务执行：Worker 线程池消费队列，异步执行任务</li>
 *   <li>任务状态查询：通过 taskId 查询 PENDING/RUNNING/COMPLETED/FAILED</li>
 *   <li>任务结果获取：任务完成后可获取结果或异常信息</li>
 *   <li>削峰填谷：高峰期任务排队，避免过载</li>
 *   <li>Agent 集成：taskType=agent 时通过 HarnessAgent.reply() 执行实际推理</li>
 * </ul>
 * </p>
 * <p>
 * 当前实现为基于 {@link LinkedBlockingQueue} + {@link ThreadPoolExecutor} 的内存队列，
 * 适合单节点场景。生产环境如需跨节点调度，可替换为 RabbitMQ/Kafka 后端
 * （需添加 spring-boot-starter-amqp / spring-kafka 依赖）。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "task-queue-enabled", havingValue = "true")
public class TaskQueueConfig {

    private final AgentScopeProperties properties;

    /**
     * HarnessAgent（由 HarnessAgentConfig 装配，条件注入）。
     * <p>存在时，taskType=agent 的任务将通过 HarnessAgent.reply() 执行实际推理。</p>
     */
    private final Optional<HarnessAgent> harnessAgent;

    /**
     * 任务队列管理器 Bean。
     * <p>
     * 装配基于内存的异步任务队列管理器，使用独立线程池消费任务。
     * 当 HarnessAgent 可用时，注入 Agent 执行回调，使 taskType=agent 的任务
     * 能通过 HarnessAgent.reply() 执行实际推理并返回最终回复。
     * </p>
     *
     * @return 任务队列管理器
     */
    @Bean
    public TaskQueueManager taskQueueManager() {
        TaskQueueManager manager = new TaskQueueManager(4, 100);
        // 注入 Agent 执行回调：taskType=agent 时通过 HarnessAgent.reply() 执行
        harnessAgent.ifPresent(agent -> {
            Function<String, String> agentExecutor = buildAgentExecutor(agent);
            manager.setAgentExecutor(agentExecutor);
            log.info("[TaskQueueConfig] 已注入 Agent 执行回调，taskType=agent 的任务将通过 HarnessAgent 执行");
        });
        log.info("[TaskQueueConfig] 内存任务队列管理器已装配: workerThreads=4, queueCapacity=100, agentReady={}",
                harnessAgent.isPresent());
        return manager;
    }

    /**
     * 构建 Agent 执行回调函数。
     * <p>
     * 将 payload（用户消息文本）通过 HarnessAgent.streamEvents() 执行，
     * 收集所有 {@link TextBlockDeltaEvent} 增量文本拼接为最终回复。
     * </p>
     *
     * @param agent HarnessAgent 实例
     * @return payload → Agent 回复文本 的回调函数
     */
    private Function<String, String> buildAgentExecutor(HarnessAgent agent) {
        return payload -> {
            // 构建用户消息
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(payload)
                    .build();

            // 构建 RuntimeContext（异步任务无需 SSE，使用独立 sessionId）
            String asyncSessionId = "task-" + java.util.UUID.randomUUID().toString();
            SessionContext ctx = SessionContext.builder()
                    .sessionId(asyncSessionId)
                    .build();
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .sessionId(asyncSessionId)
                    .put(CTX_KEY_SESSION_CONTEXT, ctx)
                    .build();

            // 通过 streamEvents 收集文本增量，阻塞等待 Flux 完成后拼接最终回复
            String result = agent.streamEvents(userMsg, runtimeContext)
                    .ofType(TextBlockDeltaEvent.class)
                    .map(TextBlockDeltaEvent::getDelta)
                    .filter(delta -> delta != null && !delta.isEmpty())
                    .reduce(new StringBuilder(), StringBuilder::append)
                    .map(StringBuilder::toString)
                    .block();
            return result != null && !result.isEmpty() ? result : "Agent 返回空回复";
        };
    }

    /**
     * 内存任务队列管理器。
     * <p>
     * 基于 {@link LinkedBlockingQueue} + {@link ThreadPoolExecutor} 实现，
     * 提供异步任务提交、状态查询与结果获取能力。
     * 任务状态通过 {@link ConcurrentHashMap} 维护，线程安全。
     * </p>
     */
    @Slf4j
    public static class TaskQueueManager {

        /** 任务状态枚举 */
        private enum TaskStatus {
            PENDING, RUNNING, COMPLETED, FAILED
        }

        /** 任务记录：taskId → 任务上下文 */
        private final ConcurrentHashMap<String, TaskContext> taskContexts = new ConcurrentHashMap<>();

        /** 任务执行线程池 */
        private final ThreadPoolExecutor executor;

        /** Agent 执行回调（taskType=agent 时使用，为空则回退到 stub） */
        private volatile Function<String, String> agentExecutor;

        /**
         * 构造任务队列管理器。
         *
         * @param workerThreads   工作线程数
         * @param queueCapacity   队列容量
         */
        public TaskQueueManager(int workerThreads, int queueCapacity) {
            this.executor = new ThreadPoolExecutor(
                    workerThreads,
                    workerThreads,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueCapacity),
                    r -> {
                        Thread t = new Thread(r, "task-queue-worker");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
        }

        /**
         * 设置 Agent 执行回调。
         *
         * @param agentExecutor payload → Agent 回复 的回调函数
         */
        public void setAgentExecutor(Function<String, String> agentExecutor) {
            this.agentExecutor = agentExecutor;
        }

        /**
         * 提交异步任务到队列。
         *
         * @param taskType 任务类型（agent 表示通过 Agent 执行，其他类型原样记录）
         * @param payload  任务负载（taskType=agent 时为用户消息文本）
         * @return 任务 ID
         */
        public String submitTask(String taskType, String payload) {
            String taskId = java.util.UUID.randomUUID().toString();
            TaskContext ctx = new TaskContext(taskId, taskType, payload);
            taskContexts.put(taskId, ctx);

            executor.submit(() -> executeTask(ctx));

            log.info("[TaskQueue] 提交异步任务: taskId={}, type={}", taskId, taskType);
            return taskId;
        }

        /**
         * 查询任务状态。
         *
         * @param taskId 任务 ID
         * @return 任务状态（PENDING/RUNNING/COMPLETED/FAILED），未知任务返回 UNKNOWN
         */
        public String getTaskStatus(String taskId) {
            TaskContext ctx = taskContexts.get(taskId);
            return ctx != null ? ctx.status.name() : "UNKNOWN";
        }

        /**
         * 获取任务结果（仅在 COMPLETED 状态时有值）。
         *
         * @param taskId 任务 ID
         * @return 任务结果字符串，任务未完成或不存在时返回 null
         */
        public String getTaskResult(String taskId) {
            TaskContext ctx = taskContexts.get(taskId);
            if (ctx != null && ctx.status == TaskStatus.COMPLETED) {
                return ctx.result;
            }
            return null;
        }

        /**
         * 获取任务错误信息（仅在 FAILED 状态时有值）。
         *
         * @param taskId 任务 ID
         * @return 错误信息字符串，任务未失败或不存在时返回 null
         */
        public String getTaskError(String taskId) {
            TaskContext ctx = taskContexts.get(taskId);
            if (ctx != null && ctx.status == TaskStatus.FAILED) {
                return ctx.error;
            }
            return null;
        }

        /**
         * 执行任务（工作线程调用）。
         * <p>
         * taskType=agent 且 agentExecutor 已注入时，通过 HarnessAgent.reply() 执行实际推理；
         * 其他 taskType 直接记录 payload 作为结果。
         * </p>
         *
         * @param ctx 任务上下文
         */
        private void executeTask(TaskContext ctx) {
            ctx.status = TaskStatus.RUNNING;
            ctx.startTime = System.currentTimeMillis();
            try {
                log.info("[TaskQueue] 开始执行任务: taskId={}, type={}", ctx.taskId, ctx.taskType);

                if (BusinessConst.TASK_TYPE_AGENT.equals(ctx.taskType) && agentExecutor != null) {
                    // Agent 任务：通过 HarnessAgent.reply() 执行推理
                    ctx.result = agentExecutor.apply(ctx.payload);
                } else {
                    // 通用任务：直接记录 payload
                    ctx.result = "Task executed: type=" + ctx.taskType + ", payload=" + ctx.payload;
                }
                ctx.status = TaskStatus.COMPLETED;
                log.info("[TaskQueue] 任务执行完成: taskId={}, durationMs={}",
                        ctx.taskId, System.currentTimeMillis() - ctx.startTime);
            } catch (Exception e) {
                ctx.error = e.getMessage();
                ctx.status = TaskStatus.FAILED;
                log.error("[TaskQueue] 任务执行失败: taskId={}", ctx.taskId, e);
            } finally {
                ctx.endTime = System.currentTimeMillis();
            }
        }

        /**
         * 销毁时关闭线程池。
         */
        @PreDestroy
        public void shutdown() {
            log.info("[TaskQueue] 关闭任务队列线程池");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        /** 任务上下文（内部状态容器） */
        private static class TaskContext {
            final String taskId;
            final String taskType;
            final String payload;
            volatile TaskStatus status = TaskStatus.PENDING;
            volatile String result;
            volatile String error;
            volatile long startTime;
            volatile long endTime;

            TaskContext(String taskId, String taskType, String payload) {
                this.taskId = taskId;
                this.taskType = taskType;
                this.payload = payload;
            }
        }
    }
}
