package com.hongyun.asynctask.scheduler;

import com.hongyun.asynctask.dto.RetryTasksResponse;
import com.hongyun.asynctask.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.task.retry-scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TaskRetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskRetryScheduler.class);

    private final TaskService taskService;

    public TaskRetryScheduler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(
            fixedDelayString = "${app.task.retry-scheduler.fixed-delay-ms}",
            initialDelayString = "${app.task.retry-scheduler.initial-delay-ms:10000}"
    )
    public void retryFailedTaskOnScheduler() {
        RetryTasksResponse response = taskService.retryFailedTasks();
        log.info(
                "Retry scan completed: scannedCount={}, retriedCount={}, deadCount={}, retriedTaskIds={}, deadTaskIds={}",
                response.getScannedCount(),
                response.getRetriedCount(),
                response.getDeadCount(),
                response.getRetriedTaskIds(),
                response.getDeadTaskIds()
        );
    }
}
