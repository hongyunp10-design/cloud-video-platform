package com.hongyun.asynctask.worker;

import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskSubmissionResult;
import com.hongyun.asynctask.service.TaskExecutionAsyncService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.task.execution-worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TaskExecutionWorker {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionWorker.class);

    private final TaskMapper taskMapper;
    private final TaskExecutionAsyncService taskExecutionAsyncService;

    @Value("${app.task.execution-worker.batch-size:5}")
    private Integer batchSize;

    public TaskExecutionWorker(
            TaskMapper taskMapper,
            TaskExecutionAsyncService taskExecutionAsyncService,
            @Value("${app.task.execution-worker.batch-size:5}") int batchSize
            ) {
        this.taskMapper = taskMapper;
        this.taskExecutionAsyncService = taskExecutionAsyncService;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.task.execution-worker.fixed-delay-ms}",
            initialDelayString = "${app.task.execution-worker.initial-delay-ms:10000}"
    )
    public void executePendingTask() {
        List<Task> pendingTasks = taskMapper.selectPendingTasks(batchSize);

        if (pendingTasks.isEmpty()) {
            log.info("Task execution scan completed: pendingCount=0");
            return;
        }

        log.info("Task execution scan started: pendingCount={}", pendingTasks.size());

        int submittedCount = 0;
        int submitFailedCount = 0;

        List<Long> submittedTaskIds = new ArrayList<>();
        List<Long> submitFailedTaskIds = new ArrayList<>();

        for (Task task : pendingTasks) {
            TaskSubmissionResult taskSubmissionResult = taskExecutionAsyncService.submitTask(task);

            if (taskSubmissionResult.isSubmitted()) {
                submittedCount++;
                submittedTaskIds.add(taskSubmissionResult.getTaskId());
            }else {
                submitFailedCount++;
                submitFailedTaskIds.add(taskSubmissionResult.getTaskId());

                log.error(
                        "Task submission failed: taskId={}, failureMessage={}",
                        taskSubmissionResult.getTaskId(),
                        taskSubmissionResult.getFailureMessage()
                );
            }
        }

        log.info("Task execution scan completed: scanCount={}, submittedCount={}, submitFailedCount={}, submittedTaskIds={}, submitFailedTaskIds={}",
                pendingTasks.size(),
                submittedCount,
                submitFailedCount,
                submittedTaskIds,
                submitFailedTaskIds
        );
    }
}