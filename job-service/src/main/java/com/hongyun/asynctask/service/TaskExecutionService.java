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

        Long taskId = task.getId();

        Task claimedTask;

        // 1. Atomically claim the pending task.
        try {
            claimedTask = taskStateService.claimTask(taskId);

        } catch (ConcurrentTaskStateChangeException |
                 InvalidTaskStateTransitionException ex) {

            log.info(
                    "Task claim skipped: taskId={}, reason={}",
                    taskId,
                    ex.getMessage()
            );

            return TaskExecutionResult.skipped(taskId);
        }

        // 2. Capture the attempt identity at claim time.
        // Never re-read the latest retryCount when finishing.
        int expectedRetryCount = claimedTask.getRetryCount();


        long executionStartNanos = System.nanoTime();

        log.info(
                "event=task_execution_started taskId={} type={} attempt={} name={}",
                taskId,
                claimedTask.getType(),
                expectedRetryCount,
                claimedTask.getName()
        );

        // 3. Execute the workload.
        try {
            TaskExecutor executor =
                    taskExecutorRegistry.getExecutor(claimedTask.getType());

            executor.execute(claimedTask);

        } catch (Exception ex) {

            // 4. Only the current attempt may report FAILED.
            boolean finalized = taskStateService.finishClaimedTask(
                    taskId,
                    expectedRetryCount,
                    TaskStatus.FAILED,
                    TaskStateChangeContext.of(
                            TaskStateActor.WORKER,
                            TaskStateChangeReason.WORKER_EXECUTION_FAILED
                    ),
                    ex.getMessage()
            );

            if (!finalized) {
                log.warn(
                        "Stale FAILED result ignored: taskId={}, attempt={}",
                        taskId,
                        expectedRetryCount
                );

                return TaskExecutionResult.skipped(taskId);
            }


            long durationMs =
                    (System.nanoTime() - executionStartNanos) / 1_000_000;

            log.warn(
                    "event=task_execution_failed taskId={} attempt={} durationMs={} reason={}",
                    taskId,
                    expectedRetryCount,
                    durationMs,
                    ex.getMessage(),
                    ex
            );


            return new TaskExecutionResult(taskId, TaskStatus.FAILED);
        }

        // 5. Only the current attempt may report SUCCESS.
        boolean finalized = taskStateService.finishClaimedTask(
                taskId,
                expectedRetryCount,
                TaskStatus.SUCCESS,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED
                ),
                null
        );

        if (!finalized) {
            log.warn(
                    "Stale SUCCESS result ignored: taskId={}, attempt={}",
                    taskId,
                    expectedRetryCount
            );

            return TaskExecutionResult.skipped(taskId);
        }

        long durationMs =
                (System.nanoTime() - executionStartNanos) / 1_000_000;

        log.info(
                "event=task_execution_succeeded taskId={} type={} attempt={} durationMs={} finalStatus={}",
                taskId,
                claimedTask.getType(),
                expectedRetryCount,
                durationMs,
                TaskStatus.SUCCESS
        );


        return new TaskExecutionResult(taskId, TaskStatus.SUCCESS);
    }

}