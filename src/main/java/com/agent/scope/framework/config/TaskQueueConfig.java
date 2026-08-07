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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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
 * 当前实现为基于 Redis List（LPUSH 入队 + BRPOP 阻塞出队）的持久化任务队列（P1-9），
 * 任务元数据存储在 Redis Hash 中，支持失败重试与死信标记，应用重启后未消费任务可继续处理。
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
     * StringRedisTemplate（用于 Redis List 持久化任务队列，P1-9）。
     */
    private final StringRedisTemplate stringRedisTemplate;

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
        TaskQueueManager manager = new TaskQueueManager(4, 100, stringRedisTemplate);
        // 注入 Agent 执行回调：taskType=agent 时通过 HarnessAgent.reply() 执行
        harnessAgent.ifPresent(agent -> {
            Function<String, String> agentExecutor = buildAgentExecutor(agent);
            manager.setAgentExecutor(agentExecutor);
            log.info("[TaskQueueConfig] 已注入 Agent 执行回调，taskType=agent 的任务将通过 HarnessAgent 执行");
        });
        log.info("[TaskQueueConfig] Redis 持久化任务队列管理器已装配: workerThreads=4, queueCapacity=100, agentReady={}",
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
     * 基于 Redis List 的任务队列管理器（P1-9 持久化增强）。
     * <p>
     * 使用 Redis List（LPUSH 入队 + BRPOP 阻塞出队）实现持久化任务队列，
     * 任务元数据存储在 Redis Hash 中，支持失败重试与死信（DEAD）标记。
     * 应用重启后未消费的任务可继续处理。
     * </p>
     * <ul>
     *   <li>队列 Key：{@link #QUEUE_KEY}（Redis List，FIFO：LPUSH 入队 + BRPOP 出队）</li>
     *   <li>任务元数据：{@link #META_KEY_PREFIX} + taskId（Redis Hash）</li>
     *   <li>死信队列：{@link #DEAD_KEY}（超过 {@link #MAX_RETRY} 的任务）</li>
     *   <li>失败重试：retry_count 从 Redis Hash 读取，超过 max_retry 则标记为 DEAD</li>
     * </ul>
     */
    @Slf4j
    public static class TaskQueueManager {

        /** 任务队列 Redis List Key：LPUSH 入队，BRPOP 出队（FIFO） */
        private static final String QUEUE_KEY = "agent:task:queue";

        /** 死信队列 Redis List Key（超过最大重试次数的任务） */
        private static final String DEAD_KEY = "agent:task:dead";

        /** 任务元数据 Redis Hash Key 前缀 */
        private static final String META_KEY_PREFIX = "agent:task:meta:";

        /** 任务元数据 TTL（天），过期自动清理，避免 Redis 数据无限增长 */
        private static final long META_TTL_DAYS = 1;

        /** 最大重试次数（超过则标记为 DEAD） */
        private static final int MAX_RETRY = 3;

        /** 任务状态枚举 */
        private enum TaskStatus {
            PENDING, RUNNING, COMPLETED, FAILED, DEAD
        }

        /** StringRedisTemplate（Redis 操作） */
        private final StringRedisTemplate redis;

        /** 任务执行线程池 */
        private final ThreadPoolExecutor executor;

        /** Agent 执行回调（taskType=agent 时使用，为空则回退到 stub） */
        private volatile Function<String, String> agentExecutor;

        /** 运行标志（控制 worker 循环退出） */
        private volatile boolean running = true;

        /** 正在执行中的任务（taskId → TaskContext），用于 @PreDestroy 刷回 Redis */
        private final ConcurrentHashMap<String, TaskContext> inFlightTasks = new ConcurrentHashMap<>();

        /**
         * 构造任务队列管理器。
         *
         * @param workerThreads   工作线程数
         * @param queueCapacity   队列容量（预留，Redis List 无界）
         * @param redis           StringRedisTemplate
         */
        public TaskQueueManager(int workerThreads, int queueCapacity, StringRedisTemplate redis) {
            this.redis = redis;
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
            // 启动 worker 线程循环消费 Redis 队列（BRPOP 阻塞出队）
            for (int i = 0; i < workerThreads; i++) {
                executor.submit(this::workerLoop);
            }
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
         * 提交异步任务到 Redis 队列。
         * <p>将任务元数据写入 Redis Hash，并将 taskId 通过 LPUSH 入队。</p>
         *
         * @param taskType 任务类型（agent 表示通过 Agent 执行，其他类型原样记录）
         * @param payload  任务负载（taskType=agent 时为用户消息文本）
         * @return 任务 ID
         */
        public String submitTask(String taskType, String payload) {
            String taskId = java.util.UUID.randomUUID().toString();
            TaskContext ctx = new TaskContext(taskId, taskType, payload);
            // 任务元数据持久化到 Redis Hash
            saveTaskToRedis(ctx);
            // LPUSH 入队（左端入队，BRPOP 右端出队 → FIFO 顺序）
            redis.opsForList().leftPush(QUEUE_KEY, taskId);

            log.info("[TaskQueue] 提交异步任务到 Redis: taskId={}, type={}", taskId, taskType);
            return taskId;
        }

        /**
         * 查询任务状态（从 Redis Hash 读取）。
         *
         * @param taskId 任务 ID
         * @return 任务状态（PENDING/RUNNING/COMPLETED/FAILED/DEAD），未知任务返回 UNKNOWN
         */
        public String getTaskStatus(String taskId) {
            Object status = redis.opsForHash().get(META_KEY_PREFIX + taskId, "status");
            return status != null ? status.toString() : "UNKNOWN";
        }

        /**
         * 获取任务结果（仅在 COMPLETED 状态时有值）。
         *
         * @param taskId 任务 ID
         * @return 任务结果字符串，任务未完成或不存在时返回 null
         */
        public String getTaskResult(String taskId) {
            Object result = redis.opsForHash().get(META_KEY_PREFIX + taskId, "result");
            return result != null && !result.toString().isEmpty() ? result.toString() : null;
        }

        /**
         * 获取任务错误信息（仅在 FAILED/DEAD 状态时有值）。
         *
         * @param taskId 任务 ID
         * @return 错误信息字符串，任务未失败或不存在时返回 null
         */
        public String getTaskError(String taskId) {
            Object error = redis.opsForHash().get(META_KEY_PREFIX + taskId, "error");
            return error != null && !error.toString().isEmpty() ? error.toString() : null;
        }

        /**
         * Worker 循环：BRPOP 阻塞出队并执行任务。
         * <p>每个 worker 线程持续从 Redis List 右端阻塞弹出 taskId，
         * 加载任务元数据后同步执行。BRPOP 设置 5 秒超时，确保 {@code running=false} 时能及时退出。</p>
         */
        private void workerLoop() {
            while (running) {
                try {
                    // BRPOP 阻塞 5 秒出队，避免无限阻塞影响应用关闭
                    String taskId = redis.opsForList().rightPop(QUEUE_KEY, Duration.ofSeconds(5));
                    if (taskId == null) {
                        continue;
                    }
                    TaskContext ctx = loadTaskFromRedis(taskId);
                    if (ctx == null) {
                        log.warn("[TaskQueue] 任务元数据不存在，跳过: taskId={}", taskId);
                        continue;
                    }
                    inFlightTasks.put(taskId, ctx);
                    try {
                        executeTask(ctx);
                    } finally {
                        inFlightTasks.remove(taskId);
                    }
                } catch (Exception e) {
                    if (running) {
                        log.error("[TaskQueue] worker 循环异常", e);
                    }
                }
            }
        }

        /**
         * 执行任务（工作线程调用）。
         * <p>
         * taskType=agent 且 agentExecutor 已注入时，通过 HarnessAgent.reply() 执行实际推理；
         * 其他 taskType 直接记录 payload 作为结果。
         * 执行失败时根据 retry_count 决定重试或标记为 DEAD。
         * </p>
         *
         * @param ctx 任务上下文
         */
        private void executeTask(TaskContext ctx) {
            ctx.status = TaskStatus.RUNNING;
            ctx.startTime = System.currentTimeMillis();
            saveTaskToRedis(ctx);
            try {
                log.info("[TaskQueue] 开始执行任务: taskId={}, type={}, retryCount={}",
                        ctx.taskId, ctx.taskType, ctx.retryCount);

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
                ctx.retryCount++;
                log.error("[TaskQueue] 任务执行失败: taskId={}, retryCount={}/{}",
                        ctx.taskId, ctx.retryCount, MAX_RETRY, e);
                if (ctx.retryCount >= MAX_RETRY) {
                    // 超过最大重试次数，标记为 DEAD 并写入死信队列
                    // TODO: 待 task_queue_record 表 Mapper 建立后，将 DEAD 任务落库到 task_queue_record 表
                    ctx.status = TaskStatus.DEAD;
                    redis.opsForList().leftPush(DEAD_KEY, ctx.taskId);
                    log.warn("[TaskQueue] 任务超过最大重试次数({}), 标记为 DEAD: taskId={}",
                            MAX_RETRY, ctx.taskId);
                } else {
                    // 未超过最大重试次数，重新入队等待重试
                    ctx.status = TaskStatus.PENDING;
                    redis.opsForList().leftPush(QUEUE_KEY, ctx.taskId);
                    log.info("[TaskQueue] 任务重新入队等待重试: taskId={}, retryCount={}/{}",
                            ctx.taskId, ctx.retryCount, MAX_RETRY);
                }
            } finally {
                ctx.endTime = System.currentTimeMillis();
                saveTaskToRedis(ctx);
            }
        }

        /**
         * 将任务上下文保存到 Redis Hash。
         *
         * @param ctx 任务上下文
         */
        private void saveTaskToRedis(TaskContext ctx) {
            try {
                String key = META_KEY_PREFIX + ctx.taskId;
                Map<String, String> hash = new HashMap<>();
                hash.put("taskId", ctx.taskId);
                hash.put("taskType", ctx.taskType);
                hash.put("payload", ctx.payload != null ? ctx.payload : "");
                hash.put("status", ctx.status.name());
                hash.put("result", ctx.result != null ? ctx.result : "");
                hash.put("error", ctx.error != null ? ctx.error : "");
                hash.put("retryCount", String.valueOf(ctx.retryCount));
                hash.put("startTime", String.valueOf(ctx.startTime));
                hash.put("endTime", String.valueOf(ctx.endTime));
                redis.opsForHash().putAll(key, hash);
                // 设置元数据 TTL，避免 Redis 数据无限增长
                redis.expire(key, Duration.ofDays(META_TTL_DAYS));
            } catch (Exception e) {
                log.error("[TaskQueue] 保存任务到 Redis 失败: taskId={}", ctx.taskId, e);
            }
        }

        /**
         * 从 Redis Hash 加载任务上下文。
         *
         * @param taskId 任务 ID
         * @return 任务上下文，不存在或加载失败时返回 null
         */
        private TaskContext loadTaskFromRedis(String taskId) {
            try {
                String key = META_KEY_PREFIX + taskId;
                Map<Object, Object> hash = redis.opsForHash().entries(key);
                if (hash == null || hash.isEmpty()) {
                    return null;
                }
                TaskContext ctx = new TaskContext(taskId, getStr(hash, "taskType"), getStr(hash, "payload"));
                ctx.status = TaskStatus.valueOf(getStr(hash, "status"));
                ctx.result = getStr(hash, "result");
                ctx.error = getStr(hash, "error");
                ctx.retryCount = Integer.parseInt(getStr(hash, "retryCount"));
                ctx.startTime = Long.parseLong(getStr(hash, "startTime"));
                ctx.endTime = Long.parseLong(getStr(hash, "endTime"));
                return ctx;
            } catch (Exception e) {
                log.error("[TaskQueue] 从 Redis 加载任务失败: taskId={}", taskId, e);
                return null;
            }
        }

        /** 从 Redis Hash 中安全获取字符串值 */
        private String getStr(Map<Object, Object> hash, String field) {
            Object v = hash.get(field);
            return v != null ? v.toString() : "";
        }

        /**
         * 销毁时关闭线程池并将内存中未完成的任务刷入 Redis（P1-9）。
         * <p>正在执行中但未完成的任务会被重新标记为 PENDING 并入队，等待下次启动后继续处理。</p>
         */
        @PreDestroy
        public void shutdown() {
            log.info("[TaskQueue] 关闭任务队列，刷入内存中未完成任务到 Redis");
            running = false;
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            // 将正在执行中但未完成的任务重新入队，等待下次启动后继续处理
            for (Map.Entry<String, TaskContext> entry : inFlightTasks.entrySet()) {
                String taskId = entry.getKey();
                TaskContext ctx = entry.getValue();
                if (ctx.status != TaskStatus.COMPLETED && ctx.status != TaskStatus.DEAD) {
                    ctx.status = TaskStatus.PENDING;
                    saveTaskToRedis(ctx);
                    redis.opsForList().leftPush(QUEUE_KEY, taskId);
                    log.info("[TaskQueue] 未完成任务已刷入 Redis: taskId={}", taskId);
                }
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
            /** 失败重试次数（从 Redis Hash 读取，超过 MAX_RETRY 标记为 DEAD） */
            volatile int retryCount;
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
