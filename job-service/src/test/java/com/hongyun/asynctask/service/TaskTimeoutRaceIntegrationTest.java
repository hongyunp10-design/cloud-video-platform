
package com.hongyun.asynctask.service;

import com.hongyun.asynctask.dto.response.RunningTaskTimeoutResponse;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.task.execution-worker.enabled=false",
        "app.task.retry-scheduler.enabled=false",
        "app.task.timeout-scheduler.enabled=false"
})
@ActiveProfiles("test")
@Transactional
class TaskTimeoutRaceIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private TaskTimeoutService taskTimeoutService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TIMEOUT_SECONDS = 300;

    @Test
    void currentTimedOutAttemptShouldFailAndCreateAudit() {

        // 1. Create and claim a task.
        Task task = taskService.createTask(
                "timeout-valid-test",
                TaskType.REPORT
        );

        Long taskId = task.getId();

        taskStateService.claimTask(taskId);

        // 2. Simulate a task running for 600 seconds.
        makeTaskOld(taskId);

        // 3. Execute the real timeout scan.
        RunningTaskTimeoutResponse response =
                taskTimeoutService.markTimedOutRunningTasksAsFailed(
                        TIMEOUT_SECONDS
                );

        // 4. Verify that our task was actually timed out.
        assertTrue(
                response.getTimedOutTaskIds().contains(taskId)
        );

        assertEquals("FAILED", getStatus(taskId));

        // PENDING -> RUNNING -> FAILED
        assertEquals(2L, countAudits(taskId));

        // Verify that the timeout reason was audited.
        assertEquals(
                1L,
                countTimeoutAudits(taskId)
        );
    }

    @Test
    void staleScanMustNotFailNewRunningAttempt() {

        // 1. Worker A claims attempt 0.
        Task task = taskService.createTask(
                "timeout-stale-test",
                TaskType.REPORT
        );

        Long taskId = task.getId();

        taskStateService.claimTask(taskId);
        makeTaskOld(taskId);

        // 2. Simulate the actual timeout scanner query.
        // This uses the same query as TaskTimeoutService.
        Task scannedTask = taskMapper
                .selectTimedOutRunningTasks(TIMEOUT_SECONDS)
                .stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Timed-out task not found: " + taskId
                ));

        assertEquals(TaskStatus.RUNNING, scannedTask.getStatus());
        assertEquals(0, scannedTask.getRetryCount());
        assertNotNull(scannedTask.getStartedAt());

        // 3. Simulate another path failing attempt 0.
        taskStateService.transitionTaskStatusWithError(
                taskId,
                TaskStatus.FAILED,
                timeoutContext(),
                "Attempt 0 timed out"
        );

        // 4. Retry and claim a NEW attempt.
        taskStateService.retryTask(
                taskId,
                TaskStateChangeContext.of(
                        TaskStateActor.RETRY_SCHEDULER,
                        TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK
                )
        );

        Task attemptB = taskStateService.claimTask(taskId);

        assertEquals(1, attemptB.getRetryCount());
        assertEquals(4L, countAudits(taskId));

        // 5. Old scanner tries to fail attempt 0.
        boolean updated =
                taskStateService.failTimedOutRunningAttempt(
                        scannedTask,
                        TIMEOUT_SECONDS,
                        timeoutContext(),
                        "Stale timeout result"
                );

        // 6. Old scan must be rejected.
        assertFalse(updated);

        // New attempt must remain RUNNING.
        assertEquals("RUNNING", getStatus(taskId));
        assertEquals(1, getRetryCount(taskId));

        // No extra audit from the stale scan.
        assertEquals(4L, countAudits(taskId));
    }

    private TaskStateChangeContext timeoutContext() {
        return TaskStateChangeContext.of(
                TaskStateActor.TIMEOUT_SCANNER,
                TaskStateChangeReason.RUNNING_TASK_TIMED_OUT
        );
    }

    private void makeTaskOld(Long taskId) {
        jdbcTemplate.update(
                "UPDATE task " +
                        "SET started_at = TIMESTAMPADD(" +
                        "SECOND, -600, CURRENT_TIMESTAMP) " +
                        "WHERE id = ?",
                taskId
        );
    }

    private String getStatus(Long taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE id = ?",
                String.class,
                taskId
        );
    }

    private int getRetryCount(Long taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT retry_count FROM task WHERE id = ?",
                Integer.class,
                taskId
        );
    }

    private long countAudits(Long taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_state_audit " +
                        "WHERE task_id = ?",
                Long.class,
                taskId
        );
    }

    private long countTimeoutAudits(Long taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_state_audit " +
                        "WHERE task_id = ? " +
                        "AND reason = 'RUNNING_TASK_TIMED_OUT'",
                Long.class,
                taskId
        );
    }
}
