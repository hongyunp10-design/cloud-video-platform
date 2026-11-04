package com.hongyun.asynctask.cache;

import com.hongyun.asynctask.dto.UpdateTaskMetadataRequest;
import com.hongyun.asynctask.dto.UpdateTaskRequest;
import com.hongyun.asynctask.dto.UpdateTaskStatusRequest;
import com.hongyun.asynctask.exception.TaskNotFoundException;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.*;
import com.hongyun.asynctask.service.TaskService;
import com.hongyun.asynctask.service.TaskStateService;
import com.hongyun.asynctask.service.TaskTimeoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        properties = {
                "app.task.execution-worker.enabled=false",
                "app.task.retry-scheduler.enabled=false",
                "app.task.timeout-scheduler.enabled=false"
        }
)

@ActiveProfiles("test")
class TaskCacheConsistencyIntegrationTest{
    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private TaskTimeoutService taskTimeoutService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper taskStateAuditMapper;

    @Autowired
    private CacheManager cacheManager;

    private final List<Long> createdTaskIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        Cache cache = cacheManager.getCache("taskCache");
        if (cache != null) {
            cache.clear();
        }

        for (Long taskId : createdTaskIds) {
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(taskId);
            taskMapper.deleteTaskById(taskId);
        }

        createdTaskIds.clear();
    }

    @Test
    void getTaskById_shouldReturnRunningTaskAfterClaim_whenTaskWasCachedAsPending() {        Task task = insertTask(
                "cache-success-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2,
                "old error"
        );

        Task cachedBefore =taskService.getTaskById(task.getId());
        assertThat(cachedBefore.getStatus()).isEqualTo(TaskStatus.PENDING);

        taskStateService.claimTask(task.getId());

        Task taskAfterClaim = taskService.getTaskById(task.getId());

        assertThat(taskAfterClaim.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(taskAfterClaim.getStartedAt()).isNotNull();
    }


    @Test
    void getTaskById_shouldReturnSuccessTaskAfterTransition_whenTaskWasCachedAsRunning() {
        Task task = insertTask(
                "cache-success-test-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2,
                "old error"
        );

        Task cachedBefore = taskService.getTaskById(task.getId());
        assertThat(cachedBefore.getStatus()).isEqualTo(TaskStatus.RUNNING);

        taskStateService.transitionTaskStatus(
                task.getId(),
                TaskStatus.SUCCESS,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED
                )
        );

        Task taskAfterSuccess = taskService.getTaskById(task.getId());

        assertThat(taskAfterSuccess.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(taskAfterSuccess.getLastErrorMessage()).isNull();
    }


    @Test
    void getTaskById_shouldReturnFailedTaskWithErrorAfterFailure_whenTaskWasCachedAsRunning() {
        Task task = insertTask(
                "cache-failed-error-test-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2
        );

        Task cachedBefore = taskService.getTaskById(task.getId());
        assertThat(cachedBefore.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(cachedBefore.getLastErrorMessage()).isNull();

        taskStateService.transitionTaskStatusWithError(
                task.getId(),
                TaskStatus.FAILED,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_FAILED
                ),
                "cache failure test error"
        );

        Task taskAfterFailure = taskService.getTaskById(task.getId());

        assertThat(taskAfterFailure.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskAfterFailure.getLastErrorMessage())
                .isEqualTo("cache failure test error");
    }

    @Test
    void getTaskById_shouldReturnPendingTaskAfterRetry_whenTaskWasCachedAsFailed() {
        Task task = insertTask(
                "cache-retry-test-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                1,
                3,
                "previous error"
        );

        Task cachedBefore = taskService.getTaskById(task.getId());
        assertThat(cachedBefore.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(cachedBefore.getLastErrorMessage()).isEqualTo("previous error");

        taskStateService.retryTask(
                task.getId(),
                TaskStateChangeContext.of(
                        TaskStateActor.RETRY_SCHEDULER,
                        TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK
                )
        );

        Task taskAfterRetry = taskService.getTaskById(task.getId());

        assertThat(taskAfterRetry.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(taskAfterRetry.getRetryCount()).isEqualTo(2);
        assertThat(taskAfterRetry.getLastErrorMessage()).isNull();
    }

    @Test
    void getTaskById_shouldReturnDeadTaskAfterTransition_whenTaskWasCachedAsFailed() {
        Task task = insertTask(
                "cache-dead-test-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                3,
                3,
                "final failure"
        );

        Task cachedBefore = taskService.getTaskById(task.getId());
        assertThat(cachedBefore.getStatus()).isEqualTo(TaskStatus.FAILED);

        taskStateService.transitionTaskStatus(
                task.getId(),
                TaskStatus.DEAD,
                TaskStateChangeContext.of(
                        TaskStateActor.RETRY_SCHEDULER,
                        TaskStateChangeReason.RETRY_LIMIT_REACHED
                )
        );

        Task taskAfterDead = taskService.getTaskById(task.getId());

        assertThat(taskAfterDead.getStatus()).isEqualTo(TaskStatus.DEAD);
        assertThat(taskAfterDead.getLastErrorMessage()).isEqualTo("final failure");
    }


    @Test
    void getTaskById_shouldReturnFailedTaskAfterTimeoutScan_whenTaskWasCachedAsRunning() {
        Task task = insertTask(
                "cache-timeout-test-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2
        );

        taskMapper.updateTaskStartedAtByOffsetSeconds(task.getId(), -600L);

        Task cachedBefore = taskService.getTaskById(task.getId());
        assertThat(cachedBefore.getStatus()).isEqualTo(TaskStatus.RUNNING);

        taskTimeoutService.markTimedOutRunningTasksAsFailed(300);

        Task taskAfterTimeout = taskService.getTaskById(task.getId());

        assertThat(taskAfterTimeout.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskAfterTimeout.getLastErrorMessage())
                .contains("Task execution timed out after 300 seconds");
    }


    @Test
    void getTaskById_shouldReturnUpdatedTaskAfterUpdate_whenTaskWasCached() {
        Task task = insertTask(
                "cache-update-old-name",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        Task cachedBefore = taskService.getTaskById(task.getId());
        assertThat(cachedBefore.getName()).isEqualTo("cache-update-old-name");

        UpdateTaskMetadataRequest request = new UpdateTaskMetadataRequest();
        request.setName("cache-update-new-name");
        request.setType(TaskType.EMAIL);

        taskService.updateTaskMetadata(task.getId(), request.getName(), request.getType());

        Task taskAfterUpdate = taskService.getTaskById(task.getId());

        assertThat(taskAfterUpdate.getName()).isEqualTo("cache-update-new-name");
        assertThat(taskAfterUpdate.getType()).isEqualTo(TaskType.EMAIL);
    }


    @Test
    void getTaskById_shouldThrowTaskNotFoundAfterDelete_whenTaskWasCached() {
        Task task = insertTask(
                "cache-delete-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        Task cachedBefore = taskService.getTaskById(task.getId());
        assertThat(cachedBefore.getStatus()).isEqualTo(TaskStatus.PENDING);

        taskService.deleteTask(task.getId());

        assertThatThrownBy(() -> taskService.getTaskById(task.getId()))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task not found: " + task.getId());

        createdTaskIds.remove(task.getId());
    }

    private Task insertTask(
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount
    ) {
        return insertTask(name, type, status, retryCount, maxRetryCount, null);
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

        return taskMapper.selectTaskById(task.getId());
    }
}