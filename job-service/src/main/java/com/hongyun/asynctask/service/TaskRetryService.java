package com.hongyun.asynctask.service;

import com.hongyun.asynctask.exception.TaskNotFoundException;
import com.hongyun.asynctask.exception.TaskRetryLimitExceededException;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.model.*;
import org.springframework.stereotype.Service;

@Service
public class TaskRetryService {
    private final TaskMapper taskMapper;
    private final TaskStateService taskStateService;

    public TaskRetryService(TaskMapper taskMapper, TaskStateService taskStateService) {
        this.taskMapper = taskMapper;
        this.taskStateService = taskStateService;
    }

    public void retrySingleFailedTask(Long id) {
        Task task = taskMapper.selectTaskById(id);
        if (task == null) {
            throw new TaskNotFoundException(id);
        }

        if (task.getStatus() != TaskStatus.FAILED) {
            return;
        }

        if (task.getRetryCount() >= task.getMaxRetryCount()) {
            throw new TaskRetryLimitExceededException(id, task.getMaxRetryCount());
        }

        taskStateService.retryTask(id, TaskStateChangeContext.of(
                TaskStateActor.RETRY_SCHEDULER,
                TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK
        ));
    }

    public void markTaskDead(Long id) {
        Task task = taskMapper.selectTaskById(id);

        if (task == null) {
            throw new TaskNotFoundException(id);
        }
        if (task.getStatus() != TaskStatus.FAILED) {
            return;
        }

        taskStateService.transitionTaskStatus(id, TaskStatus.DEAD, TaskStateChangeContext.of(
                TaskStateActor.RETRY_SCHEDULER,
                TaskStateChangeReason.RETRY_LIMIT_REACHED
        ));
    }
}