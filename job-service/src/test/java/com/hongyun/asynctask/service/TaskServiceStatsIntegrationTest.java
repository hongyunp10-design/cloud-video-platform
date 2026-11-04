package com.hongyun.asynctask.service;

import com.hongyun.asynctask.dto.response.TaskStatsResponse;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStatus;
import com.hongyun.asynctask.model.TaskType;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "app.task.execution-worker.enabled=false",
                "app.tasl.retry-scheduler.enabled=false"
        }
)

@ActiveProfiles("test")
class TaskServiceStatsIntegrationTest {

    @Autowired
    private TaskService taskService;

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
    void getTaskStats_shouldReturnCountByStatus() {
        TaskStatsResponse before = taskService.getTaskStats();

        insertTask("stats-pending-1", TaskType.MEDIA, TaskStatus.PENDING, 0, 2);
        insertTask("stats-pending-2", TaskType.EMAIL, TaskStatus.PENDING, 0, 5);
        insertTask("stats-running-1", TaskType.MEDIA, TaskStatus.RUNNING, 0, 2);
        insertTask("stats-success-1", TaskType.REPORT, TaskStatus.SUCCESS, 0, 3);
        insertTask("stats-failed-1", TaskType.MEDIA, TaskStatus.FAILED, 1, 2);
        insertTask("stats-dead-1", TaskType.EMAIL, TaskStatus.DEAD, 5, 5);

        TaskStatsResponse after = taskService.getTaskStats();

        assertThat(after.getPendingCount() - before.getPendingCount()).isEqualTo(2);
        assertThat(after.getRunningCount() - before.getRunningCount()).isEqualTo(1);
        assertThat(after.getSuccessCount() - before.getSuccessCount()).isEqualTo(1);
        assertThat(after.getFailedCount() - before.getFailedCount()).isEqualTo(1);
        assertThat(after.getDeadCount() - before.getDeadCount()).isEqualTo(1);
        assertThat(after.getTotalCount() - before.getTotalCount()).isEqualTo(6);
    }

    private Task insertTask(
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount
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

        return task;
    }
}