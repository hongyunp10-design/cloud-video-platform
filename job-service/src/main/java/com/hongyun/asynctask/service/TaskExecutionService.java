package com.hongyun.asynctask.service;

import com.hongyun.asynctask.exception.ConcurrentTaskStateChangeException;
import com.hongyun.asynctask.exception.InvalidTaskStateTransitionException;
import com.hongyun.asynctask.executor.TaskExecutor;
import com.hongyun.asynctask.executor.TaskExecutorRegistry;
import com.hongyun.asynctask.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final TaskStateService taskStateService;

    private final TaskExecutorRegistry taskExecutorRegistry;

    public TaskExecutionService(TaskStateService taskStateService, TaskExecutorRegistry taskExecutorRegistry) {

        this.taskStateService = taskStateService;
        this.taskExecutorRegistry = taskExecutorRegistry;
    }

    public TaskExecutionResult executeSingleTask(Task task) {
        log.info(
                "Task execution started: taskId={}, type={}, name={}",
                task.getId(),
                task.getType(),
                task.getName()
        );

        Task claimedTask;
        try {
            claimedTask = taskStateService.claimTask(task.getId());
        }catch (ConcurrentTaskStateChangeException | InvalidTaskStateTransitionException ex) {
            log.info(
                    "Task execution skipped because claim failed: taskId={}, reason={}",
                    task.getId(),
                    ex.getMessage()
            );
            return TaskExecutionResult.skipped(task.getId());
        }

        try {
            TaskExecutor taskExecutor = taskExecutorRegistry.getExecutor(claimedTask.getType());
            taskExecutor.execute(claimedTask);
        }catch (Exception ex) {
            Task failedTask = taskStateService.transitionTaskStatusWithError(
                    claimedTask.getId(),
                    TaskStatus.FAILED,
                    TaskStateChangeContext.of(
                            TaskStateActor.WORKER,
                            TaskStateChangeReason.WORKER_EXECUTION_FAILED
                    ),
                    ex.getMessage()
            );
            log.warn(
                    "Task execution failed: task={}, type={}, finalStatus={}, reason={}",
                    failedTask.getId(),
                    failedTask.getType(),
                    failedTask.getStatus(),
                    failedTask.getLastErrorMessage(),
                    ex
            );
            return new TaskExecutionResult(claimedTask.getId(), TaskStatus.FAILED);
        }

        taskStateService.transitionTaskStatus(
                claimedTask.getId(),
                TaskStatus.SUCCESS,
                TaskStateChangeContext.of(
                TaskStateActor.WORKER,
                TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED
                )
        );

        log.info(
                "Task execution completed: taskId={}, type={}, finalStatus={}",
                claimedTask.getId(),
                claimedTask.getType(),
                TaskStatus.SUCCESS
        );
        return  new TaskExecutionResult(claimedTask.getId(), TaskStatus.SUCCESS);
    }
}