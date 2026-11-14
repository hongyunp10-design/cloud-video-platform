
package com.hongyun.asynctask.service;

import com.hongyun.asynctask.executor.MediaTaskExecutor;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.task.execution-worker.enabled=false",
        "app.task.retry-scheduler.enabled=false",
        "app.task.timeout-scheduler.enabled=false"
})
@ActiveProfiles("test")
class WorkerCrashRecoveryIntegrationTest {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper auditMapper;

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private TaskExecutionService taskExecutionService;

    @SpyBean
    private MediaTaskExecutor mediaTaskExecutor;

    private Long taskId;

    @AfterEach
    void tearDown() {
        if (taskId != null) {
            auditMapper.deleteTaskStateAuditsByTaskId(taskId);
            taskMapper.deleteTaskById(taskId);
        }
    }

    @Test
    void crashedWorker_shouldRecoverAndCompleteOnNextAttempt() {

        // 1. Create a persistent PENDING task.
        Task task = new Task(
                null,
                "worker-crash-recovery-test",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        taskMapper.insertTask(task);
        taskId = task.getId();

        // 2. Worker A claims the task.
        Task firstAttempt = taskStateService.claimTask(taskId);

        assertThat(firstAttempt.getStatus())
                .isEqualTo(TaskStatus.RUNNING);
        assertThat(firstAttempt.getRetryCount())
                .isEqualTo(0);

        // 3. Simulate Worker A crashing 600 seconds ago.
        // The database still contains a RUNNING task.
        taskMapper.updateTaskStartedAtByOffsetSeconds(
                taskId, -600L
        );

        // 4. Find the expired snapshot without modifying
        // unrelated tasks in the shared test database.
        Task expiredSnapshot =
                taskMapper.selectTimedOutRunningTasks(300L)
                        .stream()
                        .filter(t -> taskId.equals(t.getId()))
                        .findFirst()
                        .orElseThrow();

        boolean timedOut =
                taskStateService.failTimedOutRunningAttempt(
                        expiredSnapshot,
                        300L,
                        TaskStateChangeContext.of(
                                TaskStateActor.TIMEOUT_SCANNER,
                                TaskStateChangeReason.RUNNING_TASK_TIMED_OUT
                        ),
                        "Simulated worker crash timeout"
                );

        assertThat(timedOut).isTrue();

        assertThat(taskMapper.selectTaskById(taskId).getStatus())
                .isEqualTo(TaskStatus.FAILED);

        // 5. Retry the failed task: attempt 0 -> attempt 1.
        Task retried = taskStateService.retryTask(
                taskId,
                TaskStateChangeContext.of(
                        TaskStateActor.RETRY_SCHEDULER,
                        TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK
                )
        );

        assertThat(retried.getStatus())
                .isEqualTo(TaskStatus.PENDING);
        assertThat(retried.getRetryCount())
                .isEqualTo(1);

        // 6. Worker B executes the new attempt.
        // Mock only the external Python/Ray workload.
        Mockito.doNothing()
                .when(mediaTaskExecutor)
                .execute(Mockito.any(Task.class));

        TaskExecutionResult result =
                taskExecutionService.executeSingleTask(
                        taskMapper.selectTaskById(taskId)
                );

        assertThat(result.isSuccess()).isTrue();

        Task finalTask = taskMapper.selectTaskById(taskId);

        assertThat(finalTask.getStatus())
                .isEqualTo(TaskStatus.SUCCESS);
        assertThat(finalTask.getRetryCount())
                .isEqualTo(1);
        assertThat(finalTask.getLastErrorMessage())
                .isNull();

        // 7. An old execution result cannot overwrite
        // the recovered successful task.
        boolean staleFinalized =
                taskStateService.finishClaimedTask(
                        taskId,
                        firstAttempt.getRetryCount(),
                        TaskStatus.FAILED,
                        TaskStateChangeContext.of(
                                TaskStateActor.WORKER,
                                TaskStateChangeReason.WORKER_EXECUTION_FAILED
                        ),
                        "Old worker result"
                );

        assertThat(staleFinalized).isFalse();

        // 8. Verify the complete persistent audit history.
        List<TaskStateAudit> audits =
                auditMapper.selectTaskStateAuditsByTaskId(
                        taskId, null, null
                );

        assertThat(audits).hasSize(5);

        assertThat(audits)
                .extracting(TaskStateAudit::getToStatus)
                .containsExactly(
                        TaskStatus.RUNNING,
                        TaskStatus.FAILED,
                        TaskStatus.PENDING,
                        TaskStatus.RUNNING,
                        TaskStatus.SUCCESS
                );

        assertThat(audits)
                .extracting(TaskStateAudit::getRetryCount)
                .containsExactly(0, 0, 1, 1, 1);
    }
}
