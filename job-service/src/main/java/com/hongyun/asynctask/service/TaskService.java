package com.hongyun.asynctask.service;

import com.hongyun.asynctask.config.TaskRetryProperties;
import com.hongyun.asynctask.dto.RetryTasksResponse;
import com.hongyun.asynctask.dto.UpdateTaskStatusRequest;
import com.hongyun.asynctask.dto.response.TaskStatsResponse;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.model.*;
import com.hongyun.asynctask.exception.TaskNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskRetryProperties taskRetryProperties;
    private final TaskRetryService taskRetryService;
    private final TaskStateService taskStateService;

    public TaskService(TaskMapper taskMapper, TaskRetryProperties taskRetryProperties, TaskRetryService taskRetryService, TaskStateService taskStateService) {

        this.taskMapper = taskMapper;
        this.taskRetryProperties = taskRetryProperties;
        this.taskRetryService = taskRetryService;
        this.taskStateService = taskStateService;
    }

    private Integer resolveMaxRetryCount(TaskType type) {
        return taskRetryProperties.getPerType().getOrDefault(
                type,
                taskRetryProperties.getDefaultMaxRetryCount());
    }

    public Task createTask(String name, TaskType type) {
        Task task = new Task(null, name, type, TaskStatus.PENDING, 0, resolveMaxRetryCount(type));
        taskMapper.insertTask(task);
        return task;
    }

    @Cacheable(value = "taskCache", key = "#id")
    public Task getTaskById(Long id) {
        Task task = taskMapper.selectTaskById(id);

        if (task == null) {
            throw new TaskNotFoundException(id);
        }
        return task;
    }

    @CacheEvict(value = "taskCache", key = "#id")
    public void deleteTask(Long id) {
        int deleteRows = taskMapper.deleteTaskById(id);
        if (deleteRows == 0) {
            throw new TaskNotFoundException(id);
        }
    }

    public List<Task> getAllTasks() {
        return taskMapper.selectAllTasks();
    }

    @CacheEvict(value = "taskCache", key = "#id")
    public Task updateTaskMetadata(Long id, String name, TaskType type) {
        Task existingTask = taskMapper.selectTaskById(id);
        if (existingTask == null) {
            throw new TaskNotFoundException(id);
        }

        Task updateTask = new Task(
                id,
                name,
                type,
                existingTask.getStatus(),
                existingTask.getRetryCount(),
                existingTask.getMaxRetryCount()
        );

        taskMapper.updateTask(updateTask);
        return taskMapper.selectTaskById(id);
    }

    public Task updateTaskStatus(Long id, UpdateTaskStatusRequest request) {
        return taskStateService.transitionTaskStatus(
                id,
                request.getStatus(),
                TaskStateChangeContext.of(
                        TaskStateActor.MANUAL,
                        TaskStateChangeReason.MANUAL_STATUS_UPDATE
                )
        );
    }

    public RetryTasksResponse retryFailedTasks() {
        List<Task> failedTasks = taskMapper.selectFailedTasksForRetry();
        List<Long> retriedTasksIds = new ArrayList<>();
        List<Long> deadTaskIds = new ArrayList<>();

        for (Task task : failedTasks) {
            if (task.getRetryCount() < task.getMaxRetryCount()) {
                taskRetryService.retrySingleFailedTask(task.getId());
                retriedTasksIds.add(task.getId());
            } else {
                taskRetryService.markTaskDead(task.getId());
                deadTaskIds.add(task.getId());
            }
        }

        return new RetryTasksResponse(
                failedTasks.size(),
                retriedTasksIds.size(),
                deadTaskIds.size(),
                retriedTasksIds,
                deadTaskIds
        );
    }

    public TaskStatsResponse getTaskStats() {
        long pendingCount = taskMapper.countTasksByStatus(TaskStatus.PENDING);
        long runningCount = taskMapper.countTasksByStatus(TaskStatus.RUNNING);
        long successCount = taskMapper.countTasksByStatus(TaskStatus.SUCCESS);
        long failedCount = taskMapper.countTasksByStatus(TaskStatus.FAILED);
        long deadCount = taskMapper.countTasksByStatus(TaskStatus.DEAD);

        return new TaskStatsResponse(
                pendingCount,
                runningCount,
                successCount,
                failedCount,
                deadCount
        );
    }
}