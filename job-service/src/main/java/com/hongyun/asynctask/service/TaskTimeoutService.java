package com.hongyun.asynctask.service;

import com.hongyun.asynctask.dto.response.RunningTaskTimeoutResponse;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class TaskTimeoutService {
    private static final Logger log = LoggerFactory.getLogger(TaskTimeoutService.class);

    private static final long DEFAULT_RUNNING_TIMEOUT_SECONDS = 300;

    private final TaskMapper taskMapper;
    private final TaskStateService taskStateService;

    public TaskTimeoutService(
            TaskMapper taskMapper,
            TaskStateService taskStateService
    ) {
        this.taskMapper = taskMapper;
        this.taskStateService = taskStateService;
    }

    public RunningTaskTimeoutResponse markTimedOutRunningTasksAsFailed() {
        return markTimedOutRunningTasksAsFailed(DEFAULT_RUNNING_TIMEOUT_SECONDS);
    }

    public RunningTaskTimeoutResponse markTimedOutRunningTasksAsFailed(
            long timeoutSeconds
    ) {
        // 1. Find RUNNING tasks that exceeded the timeout.
        List<Task> timedOutTasks =
                taskMapper.selectTimedOutRunningTasks(timeoutSeconds);

        List<Long> timedOutTaskIds = new ArrayList<>();

        // 2. Process each originally scanned task snapshot.
        for (Task scannedTask : timedOutTasks) {

            TaskStateChangeContext context =
                    TaskStateChangeContext.of(
                            TaskStateActor.TIMEOUT_SCANNER,
                            TaskStateChangeReason.RUNNING_TASK_TIMED_OUT
                    );

            // 3. Atomically verify the attempt and timeout condition.
            // A stale scan must not fail a newer execution attempt.
            boolean updated = taskStateService.failTimedOutRunningAttempt(
                    scannedTask,
                    timeoutSeconds,
                    context,
                    buildTimeoutMessage(scannedTask, timeoutSeconds)
            );

            // 4. Count only successfully failed tasks.
            if (updated) {
                timedOutTaskIds.add(scannedTask.getId());
            }
        }

        return new RunningTaskTimeoutResponse(
                timedOutTasks.size(),
                timedOutTaskIds.size(),
                timedOutTaskIds
        );
    }


    private String buildTimeoutMessage(Task task, long timeoutSeconds) {
        return "Task execution timed out after "
                + timeoutSeconds
                + " seconds. startedAt="
                + task.getStartedAt();
    }
}