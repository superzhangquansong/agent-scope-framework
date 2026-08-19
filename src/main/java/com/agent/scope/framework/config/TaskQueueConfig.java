package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.entity.TaskQueueRecord;
import com.agent.scope.framework.mapper.TaskQueueRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.agent.scope.framework.constant.BusinessConst.CTX_KEY_SESSION_CONTEXT;

/**
 * AgentScope 2.0 GA 特性四十八：任务队列与异步调度配置
 * <p>
 * 通过 MySQL 任务表实现异步任务调度，支持：
 * <ul>
 *   <li>异步任务提交：Agent 将耗时任务落库，立即返回 taskId</li>
 *   <li>后台任务执行：Worker 线程池轮询消费任务，异步执行</li>
 *   <li>任务状态查询：通过 taskId 查询 PENDING/RUNNING/COMPLETED/FAILED/DEAD</li>
 *   <li>任务结果获取：任务完成后可获取结果或异常信息</li>
 *   <li>削峰填谷：高峰期任务排队，避免过载</li>
 *   <li>Agent 集成：taskType=agent 时通过 HarnessAgent.reply() 执行实际推理</li>
 * </ul>
 * </p>
 * <p>
 * <b>存储方案（P1-9 改造）</b>：以 MySQL {@code task_queue_record} 表替代原 Redis List + Hash 方案。
 * 相比 Redis 队列，MySQL 任务表提供 ACK 级别的可靠性：
 * <ul>
 *   <li><b>原子抢占</b>：worker 通过 {@code UPDATE ... WHERE status='PENDING'} 原子抢占任务，
 *       多副本并发消费不会重复执行（Redis BRPOP 无此保证，worker 崩溃会丢任务）</li>
 *   <li><b>崩溃恢复</b>：任务落库后即使应用崩溃，重启时把 RUNNING 残留重置为 PENDING 继续执行</li>
 *   <li><b>审计可追溯</b>：任务全生命周期（重试次数/结果/错误）沉淀 MySQL，便于排查</li>
 * </ul>
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
     * 任务队列记录 Mapper（MySQL 持久化）。
     */
    private final TaskQueueRecordMapper taskQueueRecordMapper;

    /**
     * HarnessAgent（由 HarnessAgentConfig 装配，条件注入）。
     * <p>存在时，taskType=agent 的任务将通过 HarnessAgent.reply() 执行实际推理。</p>
     */
    private final Optional<HarnessAgent> harnessAgent;

    /**
     * 任务队列管理器 Bean。
     * <p>
     * 装配基于 MySQL 任务表的异步任务队列管理器，使用独立线程池消费任务。
     * 当 HarnessAgent 可用时，注入 Agent 执行回调，使 taskType=agent 的任务
     * 能通过 HarnessAgent.reply() 执行实际推理并返回最终回复。
     * </p>
     *
     * @return 任务队列管理器
     */
    @Bean
    public TaskQueueManager taskQueueManager() {
        TaskQueueManager manager = new TaskQueueManager(4, 100, taskQueueRecordMapper);
        // 注入 Agent 执行回调：taskType=agent 时通过 HarnessAgent.reply() 执行
        harnessAgent.ifPresent(agent -> {
            Function<String, String> agentExecutor = buildAgentExecutor(agent);
            manager.setAgentExecutor(agentExecutor);
            log.info("[TaskQueueConfig] 已注入 Agent 执行回调，taskType=agent 的任务将通过 HarnessAgent 执行");
        });
        log.info("[TaskQueueConfig] MySQL 任务队列管理器已装配: workerThreads=4, queueCapacity=100, agentReady={}",
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
     * 基于 MySQL 任务表 {@code task_queue_record} 的任务队列管理器。
     * <p>
     * 任务全生命周期持久化到 MySQL，Worker 通过「原子抢占」消费任务：
     * {@code UPDATE ... SET status='RUNNING' WHERE task_id=? AND status='PENDING'}，
     * 仅 affected rows = 1 的 worker 获得执行权，多副本并发消费不会重复执行。
     * </p>
     * <ul>
     *   <li>提交：INSERT PENDING 记录</li>
     *   <li>消费：轮询 PENDING 任务（按创建时间升序）→ 原子抢占 RUNNING → 执行 → 更新</li>
     *   <li>重试：失败 retry_count+1，超过 max_retry 标记 DEAD，否则回 PENDING</li>
     *   <li>恢复：启动时把 RUNNING 残留重置为 PENDING，应用崩溃后任务不丢失</li>
     * </ul>
     */
    @Slf4j
    public static class TaskQueueManager {

        /** 任务状态枚举（与 task_queue_record.status 字段一致） */
        private enum TaskStatus {
            PENDING, RUNNING, COMPLETED, FAILED, DEAD
        }

        /** 最大重试次数（超过则标记为 DEAD） */
        private static final int MAX_RETRY = 3;

        /** 队列轮询间隔（毫秒）：Worker 空闲时每隔该时间查询一次 PENDING 任务 */
        private static final long POLL_INTERVAL_MS = 500L;

        /** 单次轮询批量拉取的任务数 */
        private static final int BATCH_SIZE = 10;

        /** 任务记录 Mapper（MySQL 持久化） */
        private final TaskQueueRecordMapper mapper;

        /** 任务执行线程池 */
        private final ThreadPoolExecutor executor;

        /** Agent 执行回调（taskType=agent 时使用，为空则回退到 stub） */
        private volatile Function<String, String> agentExecutor;

        /** 运行标志（控制 worker 循环退出） */
        private volatile boolean running = true;

        /** 正在执行中的任务（taskId → TaskRecord），用于 @PreDestroy 刷回 DB */
        private final ConcurrentHashMap<String, TaskQueueRecord> inFlightTasks = new ConcurrentHashMap<>();

        /**
         * 构造任务队列管理器。
         *
         * @param workerThreads   工作线程数
         * @param queueCapacity   队列容量（内存队列上限）
         * @param mapper          TaskQueueRecordMapper
         */
        public TaskQueueManager(int workerThreads, int queueCapacity, TaskQueueRecordMapper mapper) {
            this.mapper = mapper;
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
            // 启动前把上次崩溃遗留的 RUNNING 任务重置为 PENDING，保证任务不丢失
            recoverInterruptedTasks();
            // 启动 worker 线程循环轮询 MySQL 队列
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
         * 提交异步任务到 MySQL 任务表。
         * <p>插入一条 PENDING 记录，worker 轮询到后执行。</p>
         *
         * @param taskType 任务类型（agent 表示通过 Agent 执行，其他类型原样记录）
         * @param payload  任务负载（taskType=agent 时为用户消息文本）
         * @return 任务 ID
         */
        public String submitTask(String taskType, String payload) {
            String taskId = java.util.UUID.randomUUID().toString();
            TaskQueueRecord record = TaskQueueRecord.builder()
                    .taskId(taskId)
                    .taskType(taskType)
                    .payload(payload)
                    .status(TaskStatus.PENDING.name())
                    .retryCount(0)
                    .maxRetry(MAX_RETRY)
                    .createTime(LocalDateTime.now())
                    .build();
            try {
                mapper.insert(record);
                log.info("[TaskQueue] 提交异步任务到 MySQL: taskId={}, type={}", taskId, taskType);
            } catch (Exception e) {
                log.error("[TaskQueue] 任务落库失败: type={}, error={}", taskType, e.getMessage(), e);
                throw new RuntimeException("任务落库失败: " + e.getMessage(), e);
            }
            return taskId;
        }

        /**
         * 查询任务状态（从 MySQL 读取）。
         *
         * @param taskId 任务 ID
         * @return 任务状态（PENDING/RUNNING/COMPLETED/FAILED/DEAD），未知任务返回 UNKNOWN
         */
        public String getTaskStatus(String taskId) {
            TaskQueueRecord record = findByTaskId(taskId);
            return record != null ? record.getStatus() : "UNKNOWN";
        }

        /**
         * 获取任务结果（仅在 COMPLETED 状态时有值）。
         *
         * @param taskId 任务 ID
         * @return 任务结果字符串，任务未完成或不存在时返回 null
         */
        public String getTaskResult(String taskId) {
            TaskQueueRecord record = findByTaskId(taskId);
            return record != null && record.getResult() != null
                    && !record.getResult().isEmpty() ? record.getResult() : null;
        }

        /**
         * 获取任务错误信息（仅在 FAILED/DEAD 状态时有值）。
         *
         * @param taskId 任务 ID
         * @return 错误信息字符串，任务未失败或不存在时返回 null
         */
        public String getTaskError(String taskId) {
            TaskQueueRecord record = findByTaskId(taskId);
            return record != null && record.getErrorMessage() != null
                    && !record.getErrorMessage().isEmpty() ? record.getErrorMessage() : null;
        }

        // ==================== Worker 消费逻辑 ====================

        /**
         * Worker 循环：轮询 MySQL 中的 PENDING 任务并执行。
         * <p>
         * 每 {@link #POLL_INTERVAL_MS} 毫秒查询一批 PENDING 任务，
         * 对每个任务执行「原子抢占」（UPDATE status='RUNNING' WHERE status='PENDING'），
         * 仅抢占成功的 worker 执行该任务，避免多副本重复消费。
         * </p>
         */
        private void workerLoop() {
            while (running) {
                try {
                    // 批量拉取 PENDING 任务（按创建时间升序）
                    java.util.List<TaskQueueRecord> pendingList = mapper.selectList(
                            new LambdaQueryWrapper<TaskQueueRecord>()
                                    .eq(TaskQueueRecord::getStatus, TaskStatus.PENDING.name())
                                    .orderByAsc(TaskQueueRecord::getCreateTime)
                                    .last("LIMIT " + BATCH_SIZE));
                    if (pendingList.isEmpty()) {
                        // 无任务，休眠后继续轮询
                        sleepQuietly(POLL_INTERVAL_MS);
                        continue;
                    }
                    for (TaskQueueRecord record : pendingList) {
                        if (!running) {
                            break;
                        }
                        // 原子抢占：仅 affected rows = 1 的 worker 获得执行权
                        if (tryAcquire(record.getTaskId())) {
                            TaskQueueRecord ctx = record;
                            ctx.setStatus(TaskStatus.RUNNING.name());
                            ctx.setUpdateTime(LocalDateTime.now());
                            inFlightTasks.put(ctx.getTaskId(), ctx);
                            try {
                                executeTask(ctx);
                            } finally {
                                inFlightTasks.remove(ctx.getTaskId());
                            }
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        log.error("[TaskQueue] worker 循环异常", e);
                        sleepQuietly(POLL_INTERVAL_MS);
                    }
                }
            }
        }

        /**
         * 原子抢占任务：将 PENDING 状态更新为 RUNNING。
         * <p>
         * 利用 {@code UPDATE ... WHERE status='PENDING'} 的条件更新保证原子性，
         * 返回受影响行数 = 1 表示抢占成功（该任务归当前 worker 执行）。
         * </p>
         *
         * @param taskId 任务 ID
         * @return true=抢占成功
         */
        private boolean tryAcquire(String taskId) {
            int affected = mapper.update(null, new LambdaUpdateWrapper<TaskQueueRecord>()
                    .eq(TaskQueueRecord::getTaskId, taskId)
                    .eq(TaskQueueRecord::getStatus, TaskStatus.PENDING.name())
                    .set(TaskQueueRecord::getStatus, TaskStatus.RUNNING.name())
                    .set(TaskQueueRecord::getUpdateTime, LocalDateTime.now()));
            return affected == 1;
        }

        /**
         * 执行任务（工作线程调用）。
         * <p>
         * taskType=agent 且 agentExecutor 已注入时，通过 HarnessAgent.reply() 执行实际推理；
         * 其他 taskType 直接记录 payload 作为结果。
         * 执行失败时根据 retryCount 决定重试或标记为 DEAD。
         * </p>
         *
         * @param ctx 任务记录（状态已置为 RUNNING）
         */
        private void executeTask(TaskQueueRecord ctx) {
            try {
                log.info("[TaskQueue] 开始执行任务: taskId={}, type={}, retryCount={}",
                        ctx.getTaskId(), ctx.getTaskType(), ctx.getRetryCount());

                if (BusinessConst.TASK_TYPE_AGENT.equals(ctx.getTaskType()) && agentExecutor != null) {
                    // Agent 任务：通过 HarnessAgent.reply() 执行推理
                    ctx.setResult(agentExecutor.apply(ctx.getPayload()));
                } else {
                    // 通用任务：直接记录 payload
                    ctx.setResult("Task executed: type=" + ctx.getTaskType() + ", payload=" + ctx.getPayload());
                }
                ctx.setStatus(TaskStatus.COMPLETED.name());
                log.info("[TaskQueue] 任务执行完成: taskId={}", ctx.getTaskId());
            } catch (Exception e) {
                ctx.setErrorMessage(e.getMessage());
                ctx.setRetryCount(ctx.getRetryCount() == null ? 1 : ctx.getRetryCount() + 1);
                log.error("[TaskQueue] 任务执行失败: taskId={}, retryCount={}/{}",
                        ctx.getTaskId(), ctx.getRetryCount(), MAX_RETRY, e);
                if (ctx.getRetryCount() >= MAX_RETRY) {
                    // 超过最大重试次数，标记为 DEAD
                    ctx.setStatus(TaskStatus.DEAD.name());
                    log.warn("[TaskQueue] 任务超过最大重试次数({}), 标记为 DEAD: taskId={}",
                            MAX_RETRY, ctx.getTaskId());
                } else {
                    // 未超过最大重试次数，回 PENDING 等待下次轮询重试
                    ctx.setStatus(TaskStatus.PENDING.name());
                    log.info("[TaskQueue] 任务回 PENDING 等待重试: taskId={}, retryCount={}/{}",
                            ctx.getTaskId(), ctx.getRetryCount(), MAX_RETRY);
                }
            } finally {
                ctx.setUpdateTime(LocalDateTime.now());
                persistTask(ctx);
            }
        }

        /**
         * 持久化任务状态到 MySQL。
         *
         * @param record 任务记录
         */
        private void persistTask(TaskQueueRecord record) {
            try {
                mapper.update(null, new LambdaUpdateWrapper<TaskQueueRecord>()
                        .eq(TaskQueueRecord::getTaskId, record.getTaskId())
                        .set(TaskQueueRecord::getStatus, record.getStatus())
                        .set(TaskQueueRecord::getResult, record.getResult())
                        .set(TaskQueueRecord::getErrorMessage, record.getErrorMessage())
                        .set(TaskQueueRecord::getRetryCount, record.getRetryCount())
                        .set(TaskQueueRecord::getUpdateTime, LocalDateTime.now()));
            } catch (Exception e) {
                log.error("[TaskQueue] 持久化任务状态失败: taskId={}", record.getTaskId(), e);
            }
        }

        /**
         * 按 taskId 查询任务记录。
         *
         * @param taskId 任务 ID
         * @return 任务记录，不存在返回 null
         */
        private TaskQueueRecord findByTaskId(String taskId) {
            try {
                return mapper.selectOne(new LambdaQueryWrapper<TaskQueueRecord>()
                        .eq(TaskQueueRecord::getTaskId, taskId)
                        .last("LIMIT 1"));
            } catch (Exception e) {
                log.error("[TaskQueue] 查询任务失败: taskId={}, error={}", taskId, e.getMessage());
                return null;
            }
        }

        /**
         * 恢复上次崩溃遗留的任务：把 RUNNING 状态重置为 PENDING。
         * <p>
         * 应用异常退出时，正在执行的任务停留在 RUNNING 状态。
         * 启动时将其重置为 PENDING，等待 worker 重新消费，保证任务不丢失。
         * </p>
         */
        private void recoverInterruptedTasks() {
            try {
                int affected = mapper.update(null, new LambdaUpdateWrapper<TaskQueueRecord>()
                        .eq(TaskQueueRecord::getStatus, TaskStatus.RUNNING.name())
                        .set(TaskQueueRecord::getStatus, TaskStatus.PENDING.name())
                        .set(TaskQueueRecord::getUpdateTime, LocalDateTime.now()));
                if (affected > 0) {
                    log.info("[TaskQueue] 恢复上次崩溃遗留的任务: count={}, RUNNING → PENDING", affected);
                }
            } catch (Exception e) {
                log.warn("[TaskQueue] 恢复遗留任务失败（不影响启动）: error={}", e.getMessage());
            }
        }

        /**
         * 静默休眠（忽略中断异常）。
         *
         * @param millis 休眠毫秒数
         */
        private void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * 销毁时关闭线程池并将内存中未完成任务刷回 MySQL。
         * <p>
         * 正在执行中但未完成的任务被重置为 PENDING，下次启动的
         * {@link #recoverInterruptedTasks()} 会再次确认状态，保证任务不丢失。
         * </p>
         */
        @PreDestroy
        public void shutdown() {
            log.info("[TaskQueue] 关闭任务队列，刷回内存中未完成任务到 MySQL");
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
            // 将正在执行中但未完成的任务重置为 PENDING，等待下次启动继续处理
            for (TaskQueueRecord record : inFlightTasks.values()) {
                if (!TaskStatus.COMPLETED.name().equals(record.getStatus())
                        && !TaskStatus.DEAD.name().equals(record.getStatus())) {
                    record.setStatus(TaskStatus.PENDING.name());
                    persistTask(record);
                    log.info("[TaskQueue] 未完成任务已刷回 MySQL: taskId={}", record.getTaskId());
                }
            }
            inFlightTasks.clear();
        }
    }
}
