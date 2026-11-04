package com.hongyun.asynctask.service;

import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TaskStateServiceIntegrationTest{

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private TaskMapper taskMapper;

    @SpyBean
    private TaskStateAuditMapper taskStateAuditMapper;

    private final List<Long> createdTaskIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long createdTaskId : createdTaskIds) {
            // 先删audit, 再删task, 避免残留测试数据
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(createdTaskId);
            taskMapper.deleteTaskById(createdTaskId);
        }

        createdTaskIds.clear();
    }

    @Test
    void transitionTaskStatus_shouldUpdateTaskAndInsertAudit_whenTransitionIsValid() {
        Task task = createPendingTask("test-normal-transition-task");

        Task updatedTask = taskStateService.transitionTaskStatus(
                task.getId(),
                TaskStatus.RUNNING,
                TaskStateChangeContext.of(
                        TaskStateActor.MANUAL,
                        TaskStateChangeReason.MANUAL_STATUS_UPDATE
                )
        );

        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.RUNNING);

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.RUNNING);

        List<TaskStateAudit> audits =
                taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                        task.getId(),
                        null,
                        null
                );

        assertThat(audits).hasSize(1);

        TaskStateAudit audit = audits.get(0);
        assertThat(audit.getTaskId()).isEqualTo(task.getId());
        assertThat(audit.getFromStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(audit.getToStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audit.getActor()).isEqualTo(TaskStateActor.MANUAL);
        assertThat(audit.getReason()).isEqualTo(TaskStateChangeReason.MANUAL_STATUS_UPDATE);
        assertThat(audit.getRetryCount()).isEqualTo(0);
        assertThat(audit.getMaxRetryCount()).isEqualTo(2);
    }

    @Test
    void transitionTaskStatus_shouldRollBackTaskUpdate_whenAuditInsertFails() {
        Task task = createPendingTask("test-rollback-transition-task");

        Mockito.doThrow(new RuntimeException("Simulated audit insert failure"))
                        .when(taskStateAuditMapper)
                .insertTaskStateAudit(Mockito.any(TaskStateAudit.class));

        assertThatThrownBy( () ->
                taskStateService.transitionTaskStatus(
                        task.getId(),
                        TaskStatus.RUNNING,
                        TaskStateChangeContext.of(
                                TaskStateActor.MANUAL,
                                TaskStateChangeReason.MANUAL_STATUS_UPDATE
                        )
                )
        ).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated audit insert failure");

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.PENDING);

        List<TaskStateAudit> audits =
                taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                        task.getId(),
                        null,
                        null
                );
        assertThat(audits).isEmpty();
    }

    @Test
    void claimTask_shouldTransitionPendingTaskToRunningAndInsertAudit() {
        Task task = insertTask(
                "state-service-claim-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        Task claimedTask = taskStateService.claimTask(task.getId());

        assertThat(claimedTask.getId()).isEqualTo(task.getId());
        assertThat(claimedTask.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(claimedTask.getRetryCount()).isEqualTo(0);
        assertThat(claimedTask.getMaxRetryCount()).isEqualTo(2);

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
        );

        assertThat(audits).hasSize(1);

        TaskStateAudit audit = audits.get(0);
        assertThat(audit.getFromStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(audit.getToStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audit.getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audit.getReason()).isEqualTo(TaskStateChangeReason.WORKER_PICKED_PENDING_TASK);
        assertThat(audit.getRetryCount()).isEqualTo(0);
        assertThat(audit.getMaxRetryCount()).isEqualTo(2);
    }

    @Test
    void transitionTaskStatusWithError_shouldPersistLastErrorMessage_whenTaskFails() {
        Task task = insertTask(
                "state-service-failed-with-error-test-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2
        );

        Task updatedTask = taskStateService.transitionTaskStatusWithError(
                task.getId(),
                TaskStatus.FAILED,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_FAILED
                ),
                "Simulated execution failure"
        );

        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(updatedTask.getLastErrorMessage()).isEqualTo("Simulated execution failure");

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskInDb.getLastErrorMessage()).isEqualTo("Simulated execution failure");

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
                );
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audits.get(0).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(0).getReason()).isEqualTo(TaskStateChangeReason.WORKER_EXECUTION_FAILED);
    }


    @Test
    void transitionTaskStatus_shouldClearLastErrorMessage_whenTaskSucceeds() {
            Task task = insertTask(
                    "state-service-success-clear-error-last-task",
                    TaskType.MEDIA,
                    TaskStatus.RUNNING,
                    0,
                    2,
                    "old error message"
            );

            Task updatedTask = taskStateService.transitionTaskStatus(
                    task.getId(),
                    TaskStatus.SUCCESS,
                    TaskStateChangeContext.of(
                            TaskStateActor.WORKER,
                            TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED
                    )
            );

            assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(updatedTask.getLastErrorMessage()).isNull();

            Task taskInDb = taskMapper.selectTaskById(task.getId());

            assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(taskInDb.getLastErrorMessage()).isNull();
    }



    @Test
    void retryTask_shouldClearLastErrorMessage_whenFailedTaskIsRetried() {
        Task task = insertTask(
                "state-service=retry-clear-error-test-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                1,
                3,
                "previous execution failure"
        );

        Task updatedTask = taskStateService.retryTask(
                task.getId(),
                TaskStateChangeContext.of(
                        TaskStateActor.RETRY_SCHEDULER,
                        TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK
                )
        );

        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(updatedTask.getRetryCount()).isEqualTo(2);
        assertThat(updatedTask.getLastErrorMessage()).isNull();

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(taskInDb.getRetryCount()).isEqualTo(2);
        assertThat(taskInDb.getLastErrorMessage()).isNull();
    }

    @Test
    void transitionTaskStatusWithError_shouldUseDefaultErrorMessage_whenErrorMessageIsBlank() {
        Task task = insertTask(
                "state-service-blank-error-message-test-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2
        );

        Task updatedTask = taskStateService.transitionTaskStatusWithError(
                task.getId(),
                TaskStatus.FAILED,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_FAILED
                ),
                "   "
        );

        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(updatedTask.getLastErrorMessage())
                .isEqualTo("Task execution failed without error message");

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskInDb.getLastErrorMessage())
                .isEqualTo("Task execution failed without error message");

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
        );

        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audits.get(0).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(0).getReason()).isEqualTo(TaskStateChangeReason.WORKER_EXECUTION_FAILED);
    }

    @Test
    void transitionTaskStatusWithError_shouldTruncateErrorMessage_whenErrorMessageIsTooLong() {
        Task task = insertTask(
                "state-service-long-error-message-test-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2
        );

        String longErrorMessage = "x".repeat(1100);

        Task updatedTask = taskStateService.transitionTaskStatusWithError(
                task.getId(),
                TaskStatus.FAILED,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_FAILED
                ),
                longErrorMessage
        );

        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(updatedTask.getLastErrorMessage()).hasSize(1024);
        assertThat(updatedTask.getLastErrorMessage()).isEqualTo("x".repeat(1024));

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskInDb.getLastErrorMessage()).hasSize(1024);
        assertThat(taskInDb.getLastErrorMessage()).isEqualTo("x".repeat(1024));

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
        );

        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audits.get(0).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(0).getReason()).isEqualTo(TaskStateChangeReason.WORKER_EXECUTION_FAILED);
    }


    @Test
    void claimTask_shouldSetStartedAtAndClearLastErrorMessage() {
        Task task = insertTask(
                "state-service-claim-started-at-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2,
                "old error message"
        );
        Task claimedTask = taskStateService.claimTask(task.getId());

        assertThat(claimedTask.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(claimedTask.getStartedAt()).isNotNull();
        assertThat(claimedTask.getLastErrorMessage()).isNull();

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(taskInDb.getStartedAt()).isNotNull();
        assertThat(taskInDb.getLastErrorMessage()).isNull();

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
        );

        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(0).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(0).getReason()).isEqualTo(TaskStateChangeReason.WORKER_PICKED_PENDING_TASK);
    }

    private Task createPendingTask(String name) {
        Task task = new Task(
                null,
                name,
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        taskMapper.insertTask(task);
        createdTaskIds.add(task.getId());

        return  task;
    }

    private Task insertTask(
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount
    ) {
        return insertTask(name, type, status, retryCount ,maxRetryCount, null);
    }

    private Task insertTask(
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount,
            String lastErrorMessage
    ) {
        Task task = new Task(
                null,
                name,
                type,
                status,
                retryCount,
                maxRetryCount,
                lastErrorMessage
        );

        taskMapper.insertTask(task);
        createdTaskIds.add(task.getId());

        return task;
    }
}