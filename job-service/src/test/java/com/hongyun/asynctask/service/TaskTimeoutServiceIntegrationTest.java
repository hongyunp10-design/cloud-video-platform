package com.hongyun.asynctask.service;

import com.hongyun.asynctask.dto.response.RunningTaskTimeoutResponse;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStateActor;
import com.hongyun.asynctask.model.TaskStateAudit;
import com.hongyun.asynctask.model.TaskStateChangeReason;
import com.hongyun.asynctask.model.TaskStatus;
import com.hongyun.asynctask.model.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.task.execution-worker.enabled=false",
        "app.task.retry-scheduler.enabled=false"
})
@ActiveProfiles("test")
class TaskTimeoutServiceIntegrationTest {

    @Autowired
    private TaskTimeoutService taskTimeoutService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper taskStateAuditMapper;

    private final List<Long> createdTaskIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long taskId : createdTaskIds) {
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(taskId);
            taskMapper.deleteTaskById(taskId);
        }

        createdTaskIds.clear();
    }

    @Test
    void markTimedOutRunningTasksAsFailed_shouldFailOnlyTimedOutRunningTasks() {
        Task timedOutRunningTask = insertTask(
                "timeout-running-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2,
                -600L
        );

        Task freshRunningTask = insertTask(
                "fresh-running-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2,
                0L
        );

        Task pendingTask = insertTask(
                "timeout-pending-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2,
                null
        );

        RunningTaskTimeoutResponse response =
                taskTimeoutService.markTimedOutRunningTasksAsFailed(300);

        assertThat(response.getScannedCount()).isEqualTo(1);
        assertThat(response.getTimedOutCount()).isEqualTo(1);
        assertThat(response.getTimedOutTaskIds()).containsExactly(timedOutRunningTask.getId());

        Task timedOutTaskInDb = taskMapper.selectTaskById(timedOutRunningTask.getId());
        Task freshRunningTaskInDb = taskMapper.selectTaskById(freshRunningTask.getId());
        Task pendingTaskInDb = taskMapper.selectTaskById(pendingTask.getId());

        assertThat(timedOutTaskInDb.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(timedOutTaskInDb.getLastErrorMessage())
                .contains("Task execution timed out after 300 seconds");

        assertThat(freshRunningTaskInDb.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(pendingTaskInDb.getStatus()).isEqualTo(TaskStatus.PENDING);

        List<TaskStateAudit> audits =
                taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                        timedOutRunningTask.getId(),
                        null,
                        null
                );

        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audits.get(0).getActor()).isEqualTo(TaskStateActor.TIMEOUT_SCANNER);
        assertThat(audits.get(0).getReason()).isEqualTo(TaskStateChangeReason.RUNNING_TASK_TIMED_OUT);
    }

    private Task insertTask(
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount,
            Long startedAtOffsetSeconds
    ) {
        Task task = new Task(
                null,
                name,
                type,
                status,
                retryCount,
                maxRetryCount
        );

        taskMapper.insertTask(task);
        createdTaskIds.add(task.getId());

        if (startedAtOffsetSeconds != null) {
            taskMapper.updateTaskStartedAtByOffsetSeconds(
                    task.getId(),
                    startedAtOffsetSeconds
            );
        }

        return taskMapper.selectTaskById(task.getId());
    }
}