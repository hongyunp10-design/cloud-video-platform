package com.hongyun.asynctask.controller;

import com.hongyun.asynctask.common.ApiResponse;
import com.hongyun.asynctask.dto.RetryTasksResponse;
import com.hongyun.asynctask.dto.UpdateTaskMetadataRequest;
import com.hongyun.asynctask.dto.UpdateTaskStatusRequest;
import com.hongyun.asynctask.dto.response.TaskStatsResponse;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStateActor;
import com.hongyun.asynctask.model.TaskStateAudit;
import com.hongyun.asynctask.model.TaskStateChangeReason;
import com.hongyun.asynctask.service.TaskService;
import com.hongyun.asynctask.dto.CreateTaskRequest;
import com.hongyun.asynctask.service.TaskStateAuditService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    private final TaskService taskService;
    private final TaskStateAuditService taskStateAuditService;

    public TaskController(TaskService taskService, TaskStateAuditService taskStateAuditService) {
        this.taskService = taskService;
        this.taskStateAuditService = taskStateAuditService;
    }

    @PostMapping
    public ApiResponse<Task> createTask(@Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.createTask(request.getName(), request.getType());
        return ApiResponse.success(task);
    }

    @GetMapping("/stats")
    public ApiResponse<TaskStatsResponse> getTaskStats() {
        return ApiResponse.success(taskService.getTaskStats());
    }

    @GetMapping("/{id}")
    public ApiResponse<Task> getTaskById(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTaskById(id));
    }

    @GetMapping
    public ApiResponse<List<Task>> getAllTasks() {
        return ApiResponse.success(taskService.getAllTasks());
    }

    @PutMapping("/{id}/metadata")
    public ApiResponse<Task> updateTaskMetadata(@PathVariable Long id, @Valid @RequestBody UpdateTaskMetadataRequest request) {
        Task task = taskService.updateTaskMetadata(
                id,
                request.getName(),
                request.getType()
        );

        return ApiResponse.success(task);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Task> updateTaskStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest request
    ) {
        Task updatedTask = taskService.updateTaskStatus(id, request);
        return ApiResponse.success(updatedTask);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ApiResponse.success("Task deleted successfully", null);

    }

    @PostMapping("/retry")
    public ApiResponse<RetryTasksResponse> retryFailedTasks() {
        log.info("Manual retry scan triggered");

        RetryTasksResponse response = taskService.retryFailedTasks();

        log.info("Manual retry scan completed: scannedCount={}, retriedCount={}, deadCount={}, retriedTaskIds={}, deadTaskIds={}",
                response.getScannedCount(),
                response.getRetriedCount(),
                response.getDeadCount(),
                response.getRetriedTaskIds(),
                response.getDeadTaskIds()
        );

        return ApiResponse.success(
                "Retry scan completed",
                response
        );
    }

    @GetMapping("/{id}/audits")
    public ApiResponse<List<TaskStateAudit>> getTaskStateAudits(
            @PathVariable Long id,
            @RequestParam(required = false) TaskStateActor actor,
            @RequestParam(required = false)TaskStateChangeReason reason
            ){
        List<TaskStateAudit> audits = taskStateAuditService.getTaskStateAuditByTaskId(
                id,
                actor,
                reason
                );

        return ApiResponse.success(audits);
    }
}
