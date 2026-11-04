package com.hongyun.asynctask.scheduler;

import com.hongyun.asynctask.config.TaskTimeoutSchedulerProperties;
import com.hongyun.asynctask.dto.response.RunningTaskTimeoutResponse;
import com.hongyun.asynctask.service.TaskTimeoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskTimeoutScheduler  {

    private static final Logger log = LoggerFactory.getLogger(TaskTimeoutScheduler.class);

    private final TaskTimeoutService taskTimeoutService;
    private final TaskTimeoutSchedulerProperties properties;

    public TaskTimeoutScheduler(
            TaskTimeoutService taskTimeoutService,
            TaskTimeoutSchedulerProperties properties
    ) {
        this.taskTimeoutService = taskTimeoutService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${app.task.timeout-scheduler.fixed-delay-ms:60000}",
            initialDelayString = "${app.task.timeout-scheduler.initial-delay-ms:60000}"
    )
    public void scanTimedOutRunningTasks() {
        if (!properties.isEnabled()) {
            log.debug("Running task timeout scheduler is disabled");
            return;
        }

        RunningTaskTimeoutResponse response =
                taskTimeoutService.markTimedOutRunningTasksAsFailed(properties.getTimeoutSeconds());

        log.info(
                "Running task timeout scan completed: scannedCount={}, timeOutCount={}, timeOuttaskIds={}",
                response.getScannedCount(),
                response.getTimedOutCount(),
                response.getTimedOutTaskIds()
         );
    }
}
