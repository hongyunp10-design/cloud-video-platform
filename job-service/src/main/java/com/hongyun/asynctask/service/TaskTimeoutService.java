package com.hongyun.asynctask.service;

import com.hongyun.asynctask.dto.response.RunningTaskTimeoutResponse;
import com.hongyun.asynctask.exception.ConcurrentTaskStateChangeException;
import com.hongyun.asynctask.exception.InvalidTaskStateTransitionException;
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

    public RunningTaskTimeoutResponse markTimedOutRunningTasksAsFailed(long timeoutSeconds) {
        List<Task> timeOutTasks = taskMapper.selectTimedOutRunningTasks(timeoutSeconds);

        List<Long> timeOutTaskIds = new ArrayList<>();

        for (Task task : timeOutTasks) {
            try {
                Task failedTask = taskStateService.transitionTaskStatusWithError(
                        task.getId(),
                        TaskStatus.FAILED,
                        TaskStateChangeContext.of(
                                TaskStateActor.TIMEOUT_SCANNER,
                                TaskStateChangeReason.RUNNING_TASK_TIMED_OUT
                        ),
                        buildTimeoutMessage(task, timeoutSeconds)
                );

                timeOutTaskIds.add(failedTask.getId());
            }catch (InvalidTaskStateTransitionException | ConcurrentTaskStateChangeException ex) {
                // Task was changed by worker or another scanner after being selected.
                // Safe to skip this task in current scan.
                log.info(
                        "Skip timed out task because state changed concurrently: taskId={}, message={}",
                        task.getId(),
                        ex.getMessage()
                );
            }
        }

        return new RunningTaskTimeoutResponse(
                timeOutTasks.size(),
                timeOutTaskIds.size(),
                timeOutTaskIds
        );
    }

    private String buildTimeoutMessage(Task task, long timeoutSeconds) {
        return "Task execution timed out after "
                + timeoutSeconds
                + " seconds. startedAt="
                + task.getStartedAt();
    }
}