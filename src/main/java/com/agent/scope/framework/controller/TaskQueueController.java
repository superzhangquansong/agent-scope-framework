package com.agent.scope.framework.controller;

import com.agent.scope.framework.config.TaskQueueConfig.TaskQueueManager;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import com.agent.scope.framework.vo.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务队列控制器（特性48）。
 * <p>
 * 基于内存任务队列 {@link TaskQueueManager} 暴露异步任务投递与查询能力，支持：
 * <ul>
 *   <li>POST /api/task/submit          - 提交异步任务（taskType, payload）</li>
 *   <li>GET  /api/task/{taskId}/status - 查询任务状态（PENDING/RUNNING/COMPLETED/FAILED）</li>
 *   <li>GET  /api/task/{taskId}/result - 获取任务结果</li>
 * </ul>
 * </p>
 * <p>
 * 任务异步执行，提交后立即返回 taskId，客户端通过轮询 status / result 获取最终产物。
 * 适合耗时工具调用、批量数据处理等削峰填谷场景。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/task")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "task-queue-enabled", havingValue = "true")
public class TaskQueueController {

    /** 任务队列管理器（由 TaskQueueConfig 装配） */
    private final TaskQueueManager taskQueueManager;

    /**
     * 任务提交请求体。
     *
     * @param taskType 任务类型
     * @param payload  任务负载（JSON 字符串）
     */
    public record SubmitRequest(String taskType, String payload) {
    }

    /**
     * 提交异步任务到队列。
     *
     * @param request 提交请求
     * @return 任务 ID
     */
    @PostMapping("/submit")
    public Response<Map<String, Object>> submit(@RequestBody SubmitRequest request) {
        log.info("[TaskQueue] 提交任务: taskType={}", request.taskType());
        if (request.taskType() == null || request.taskType().isBlank()
                || request.payload() == null || request.payload().isBlank()) {
            throw new BusinessException(ErrorCode.TASK_PARAM_INVALID);
        }
        String taskId = taskQueueManager.submitTask(request.taskType(), request.payload());
        Map<String, Object> data = new HashMap<>(2);
        data.put("taskId", taskId);
        data.put("taskType", request.taskType());
        return Response.success(data);
    }

    /**
     * 查询任务状态。
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    @GetMapping("/{taskId}/status")
    public Response<Map<String, Object>> status(@PathVariable String taskId) {
        log.info("[TaskQueue] 查询任务状态: taskId={}", taskId);
        String status = taskQueueManager.getTaskStatus(taskId);
        if (BusinessConst.TASK_STATUS_UNKNOWN.equals(status)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("taskId", taskId);
        data.put("status", status);
        return Response.success(data);
    }

    /**
     * 获取任务结果。
     * <p>仅 COMPLETED 状态返回结果；FAILED 返回错误信息；其它状态抛出"结果未就绪"。</p>
     *
     * @param taskId 任务 ID
     * @return 任务结果
     */
    @GetMapping("/{taskId}/result")
    public Response<Map<String, Object>> result(@PathVariable String taskId) {
        log.info("[TaskQueue] 获取任务结果: taskId={}", taskId);
        String status = taskQueueManager.getTaskStatus(taskId);
        if (BusinessConst.TASK_STATUS_UNKNOWN.equals(status)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        String error = taskQueueManager.getTaskError(taskId);
        if (error != null) {
            throw new BusinessException(ErrorCode.TASK_RESULT_NOT_READY, "任务执行失败: " + error);
        }
        String result = taskQueueManager.getTaskResult(taskId);
        if (result == null) {
            throw new BusinessException(ErrorCode.TASK_RESULT_NOT_READY,
                    "任务结果未就绪，当前状态: " + status);
        }
        Map<String, Object> data = new HashMap<>(2);
        data.put("taskId", taskId);
        data.put("result", result);
        return Response.success(data);
    }
}
