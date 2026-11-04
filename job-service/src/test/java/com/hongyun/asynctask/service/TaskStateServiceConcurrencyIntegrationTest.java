package com.hongyun.asynctask.service;

import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStateActor;
import com.hongyun.asynctask.model.TaskStateAudit;
import com.hongyun.asynctask.model.TaskStateChangeContext;
import com.hongyun.asynctask.model.TaskStateChangeReason;
import com.hongyun.asynctask.model.TaskStatus;
import com.hongyun.asynctask.model.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.task.execution-worker.enabled=false",
        "app.task.retry-scheduler.enabled=false"
})
@ActiveProfiles("test")
class TaskStateServiceConcurrencyIntegrationTest {

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper taskStateAuditMapper;

    private Long createdTaskId;

    @AfterEach
    void tearDown() {
        if (createdTaskId != null) {
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(createdTaskId);
            taskMapper.deleteTaskById(createdTaskId);
        }
    }

    @Test
    void transitionTaskStatus_shouldNotInsertDuplicateAudit_whenOldStatusUpdateFails() {
        Task task = insertTask(
                "service-concurrency-dead-test-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                2,
                2
        );

        Task updatedTask = taskStateService.transitionTaskStatus(
                task.getId(),
                TaskStatus.DEAD,
                TaskStateChangeContext.of(
                        TaskStateActor.RETRY_SCHEDULER,
                        TaskStateChangeReason.RETRY_LIMIT_REACHED
                )
        );

        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.DEAD);

        int updatedRows = taskMapper.updateTaskStatusIfCurrentStatus(
                task.getId(),
                TaskStatus.FAILED,
                TaskStatus.DEAD
        );

        assertThat(updatedRows).isEqualTo(0);

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.DEAD);
        assertThat(taskInDb.getRetryCount()).isEqualTo(2);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
        );

        assertThat(audits).hasSize(1);

        TaskStateAudit audit = audits.get(0);
        assertThat(audit.getFromStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audit.getToStatus()).isEqualTo(TaskStatus.DEAD);
        assertThat(audit.getActor()).isEqualTo(TaskStateActor.RETRY_SCHEDULER);
        assertThat(audit.getReason()).isEqualTo(TaskStateChangeReason.RETRY_LIMIT_REACHED);
        assertThat(audit.getRetryCount()).isEqualTo(2);
        assertThat(audit.getMaxRetryCount()).isEqualTo(2);
    }

    @Test
    void retryTask_shouldNotInsertDuplicateAudit_whenOldRetryCountUpdateFails() {
        Task task = insertTask(
                "service-concurrency-retry-test-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                0,
                2
        );

        Task retriedTask = taskStateService.retryTask(
                task.getId(),
                TaskStateChangeContext.of(
                        TaskStateActor.RETRY_SCHEDULER,
                        TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK
                )
        );

        assertThat(retriedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(retriedTask.getRetryCount()).isEqualTo(1);
        assertThat(retriedTask.getMaxRetryCount()).isEqualTo(2);

        int updatedRows = taskMapper.retryTaskIfCurrentStatusAndRetryCount(
                task.getId(),
                TaskStatus.FAILED,
                0,
                TaskStatus.PENDING,
                1
        );

        assertThat(updatedRows).isEqualTo(0);

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(taskInDb.getRetryCount()).isEqualTo(1);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
        );

        assertThat(audits).hasSize(1);

        TaskStateAudit audit = audits.get(0);
        assertThat(audit.getFromStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audit.getToStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(audit.getActor()).isEqualTo(TaskStateActor.RETRY_SCHEDULER);
        assertThat(audit.getReason()).isEqualTo(TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK);
        assertThat(audit.getRetryCount()).isEqualTo(1);
        assertThat(audit.getMaxRetryCount()).isEqualTo(2);
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
        createdTaskId = task.getId();

        return task;
    }
}