package com.agent.scope.framework.controller;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import com.agent.scope.framework.vo.Response;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.extensions.scheduler.AgentScheduler;
import io.agentscope.extensions.scheduler.BaseScheduleAgentTask;
import io.agentscope.extensions.scheduler.ScheduleAgentTask;
import io.agentscope.extensions.scheduler.config.AgentConfig;
import io.agentscope.extensions.scheduler.config.DashScopeModelConfig;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时唤醒调度控制器（特性38）。
 * <p>
 * 基于 {@link AgentScheduler} 暴露定时唤醒 Agent 的 REST 能力，支持：
 * <ul>
 *   <li>POST /api/scheduler/schedule - 注册定时唤醒任务（CRON / 固定频率 / 固定延迟）</li>
 *   <li>GET  /api/scheduler/list     - 列举当前所有调度任务</li>
 *   <li>POST /api/scheduler/{taskId}/cancel - 取消指定调度任务</li>
 * </ul>
 * </p>
 * <p>
 * 被唤醒的 Agent 模型配置复用服务端 {@link AgentScopeProperties} 中的 DashScope 配置，
 * 调用方只需提供任务名称、调度模式与触发消息，无需在请求中传递模型凭证。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "scheduler-enabled", havingValue = "true")
public class SchedulerController {

    /** Agent 调度器（由 SchedulerConfig 装配） */
    private final AgentScheduler agentScheduler;

    /** AgentScope 配置属性（提供被唤醒 Agent 的模型凭证） */
    private final AgentScopeProperties properties;

    /**
     * 注册定时唤醒任务请求体。
     *
     * @param name        调度任务名称（必填）
     * @param cron         CRON 表达式（与 fixedRate / fixedDelay 三选一）
     * @param fixedRate   固定频率（毫秒）
     * @param fixedDelay  固定延迟（毫秒）
     * @param message     触发时投递给 Agent 的唤醒消息（可选，默认定时唤醒执行）
     * @param sysPrompt    被唤醒 Agent 的系统提示词（可选，使用默认值）
     */
    public record ScheduleRequest(
            String name,
            String cron,
            Long fixedRate,
            Long fixedDelay,
            String message,
            String sysPrompt
    ) {
    }

    /**
     * 注册定时唤醒任务。
     * <p>
     * 调度模式三选一：{@code cron}（CRON 表达式）、{@code fixedRate}（固定频率毫秒）、
     * {@code fixedDelay}（固定延迟毫秒）。到点后调度器以 {@code message} 唤醒一个独立 Agent 执行。
     * </p>
     *
     * @param request 调度请求
     * @return 注册结果（taskId、name）
     */
    @PostMapping("/schedule")
    public Response<Map<String, Object>> schedule(@RequestBody ScheduleRequest request) {
        log.info("[Scheduler] 注册调度任务: name={}, cron={}, fixedRate={}, fixedDelay={}",
                request.name(), request.cron(), request.fixedRate(), request.fixedDelay());

        boolean hasCron = request.cron() != null && !request.cron().isBlank();
        boolean hasFixedRate = request.fixedRate() != null;
        boolean hasFixedDelay = request.fixedDelay() != null;
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.SCHEDULER_PARAM_INVALID, "调度任务名称 name 不能为空");
        }
        int modeCount = (hasCron ? 1 : 0) + (hasFixedRate ? 1 : 0) + (hasFixedDelay ? 1 : 0);
        if (modeCount != 1) {
            throw new BusinessException(ErrorCode.SCHEDULER_PARAM_INVALID,
                    "调度模式需三选一：cron / fixedRate / fixedDelay");
        }

        try {
            ScheduleConfig scheduleConfig = buildScheduleConfig(request, hasCron, hasFixedRate, hasFixedDelay);
            AgentConfig agentConfig = buildAgentConfig(request);
            Msg wakeMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(resolveMessage(request.message()))
                    .build();

            ScheduleAgentTask task = agentScheduler.schedule(agentConfig, scheduleConfig, wakeMsg);

            Map<String, Object> data = new HashMap<>(4);
            data.put("taskId", task.getId());
            data.put("name", task.getName());
            return Response.success(data);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Scheduler] 注册调度任务失败: name={}", request.name(), e);
            throw new BusinessException(ErrorCode.SCHEDULER_SCHEDULE_FAILED,
                    "注册调度任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列举当前所有调度任务。
     *
     * @return 任务列表（taskId、name、调度配置、执行次数、取消状态）
     */
    @GetMapping("/list")
    public Response<Map<String, Object>> list() {
        log.info("[Scheduler] 查询调度任务列表");
        List<ScheduleAgentTask> tasks = agentScheduler.getAllScheduleAgentTasks();
        List<Map<String, Object>> taskViews = tasks.stream()
                .map(this::toTaskView)
                .toList();
        Map<String, Object> data = new HashMap<>(2);
        data.put("total", taskViews.size());
        data.put("tasks", taskViews);
        return Response.success(data);
    }

    /**
     * 取消指定调度任务。
     *
     * @param taskId 任务 ID
     * @return 取消结果
     */
    @PostMapping("/{taskId}/cancel")
    public Response<Map<String, Object>> cancel(@PathVariable String taskId) {
        log.info("[Scheduler] 取消调度任务: taskId={}", taskId);
        boolean cancelled = agentScheduler.cancel(taskId);
        if (!cancelled) {
            throw new BusinessException(ErrorCode.SCHEDULER_TASK_NOT_FOUND,
                    "调度任务不存在或已取消: " + taskId);
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("taskId", taskId);
        data.put("cancelled", true);
        return Response.success(data);
    }

    /**
     * 根据请求构建调度配置。
     */
    private ScheduleConfig buildScheduleConfig(ScheduleRequest request,
                                               boolean hasCron, boolean hasFixedRate, boolean hasFixedDelay) {
        ScheduleConfig.Builder builder = ScheduleConfig.builder();
        if (hasCron) {
            builder.cron(request.cron());
        } else if (hasFixedRate) {
            builder.fixedRate(request.fixedRate());
        } else if (hasFixedDelay) {
            builder.fixedDelay(request.fixedDelay());
        }
        return builder.build();
    }

    /**
     * 基于服务端 DashScope 配置构建被唤醒 Agent 的配置。
     * <p>模型凭证复用 {@link AgentScopeProperties}，避免在请求中传递敏感信息。</p>
     */
    private AgentConfig buildAgentConfig(ScheduleRequest request) {
        AgentScopeProperties.DashScope ds = properties.getDashscope();
        DashScopeModelConfig modelConfig = DashScopeModelConfig.builder()
                .apiKey(ds.getApiKey())
                .modelName(properties.getModelName())
                .stream(ds.isStream())
                .enableThinking(ds.isEnableThinking())
                .baseUrl(ds.getBaseUrl())
                .build();
        String sysPrompt = (request.sysPrompt() != null && !request.sysPrompt().isBlank())
                ? request.sysPrompt()
                : BusinessConst.SCHEDULER_DEFAULT_SYS_PROMPT;
        return AgentConfig.builder()
                .name(request.name())
                .modelConfig(modelConfig)
                .sysPrompt(sysPrompt)
                .build();
    }

    /**
     * 解析唤醒消息，为空时使用默认值。
     */
    private String resolveMessage(String message) {
        return (message != null && !message.isBlank())
                ? message
                : BusinessConst.SCHEDULER_DEFAULT_WAKEUP_MESSAGE;
    }

    /**
     * 将调度任务转换为视图对象。
     */
    @SuppressWarnings("rawtypes")
    private Map<String, Object> toTaskView(ScheduleAgentTask task) {
        Map<String, Object> view = new HashMap<>(8);
        view.put("taskId", task.getId());
        view.put("name", task.getName());
        if (task instanceof BaseScheduleAgentTask base) {
            view.put("executionCount", base.getExecutionCount());
            view.put("cancelled", base.isCancelled());
            ScheduleConfig sc = base.getScheduleConfig();
            if (sc != null) {
                view.put("scheduleMode", sc.getScheduleMode());
                view.put("cron", sc.getCronExpression());
                view.put("fixedRate", sc.getFixedRate());
                view.put("fixedDelay", sc.getFixedDelay());
            }
        }
        return view;
    }
}
