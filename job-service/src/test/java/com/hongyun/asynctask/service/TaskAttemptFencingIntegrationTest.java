
package com.hongyun.asynctask.service;

import com.hongyun.asynctask.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskAttemptFencingIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleAttemptMustNotOverwriteNewAttempt() {

        // 1. Create a PENDING task.
        Task task = taskService.createTask(
                "attempt-fencing-test",
                TaskType.REPORT
        );

        Long taskId = task.getId();

        // 2. Worker A claims attempt 0.
        Task attemptA = taskStateService.claimTask(taskId);
        int retryA = attemptA.getRetryCount();

        assertEquals(0, retryA);

        // 3. Simulate attempt 0 timing out.
        taskStateService.transitionTaskStatusWithError(
                taskId,
                TaskStatus.FAILED,
                TaskStateChangeContext.of(
                        TaskStateActor.TIMEOUT_SCANNER,
                        TaskStateChangeReason.RUNNING_TASK_TIMED_OUT
                ),
                "Simulated execution timeout"
        );

        // 4. Retry creates attempt 1.
        taskStateService.retryTask(
                taskId,
                TaskStateChangeContext.of(
                        TaskStateActor.RETRY_SCHEDULER,
                        TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK
                )
        );

        // 5. Worker B claims attempt 1.
        Task attemptB = taskStateService.claimTask(taskId);
        int retryB = attemptB.getRetryCount();

        assertEquals(1, retryB);
        assertEquals(4L, countAudits(taskId));

        // 6. Stale Worker A reports SUCCESS.
        boolean staleSuccess = taskStateService.finishClaimedTask(
                taskId,
                retryA,
                TaskStatus.SUCCESS,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED
                ),
                null
        );

        assertFalse(staleSuccess);

        // 7. Stale Worker A reports FAILED.
        boolean staleFailure = taskStateService.finishClaimedTask(
                taskId,
                retryA,
                TaskStatus.FAILED,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_FAILED
                ),
                "Stale worker error"
        );

        assertFalse(staleFailure);

        // 8. Neither stale result may change the database.
        assertEquals("RUNNING", getStatus(taskId));
        assertEquals(1, getRetryCount(taskId));
        assertEquals(4L, countAudits(taskId));

        // 9. Worker B reports SUCCESS.
        boolean currentSuccess = taskStateService.finishClaimedTask(
                taskId,
                retryB,
                TaskStatus.SUCCESS,
                TaskStateChangeContext.of(
                        TaskStateActor.WORKER,
                        TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED
                ),
                null
        );

        assertTrue(currentSuccess);

        // 10. Verify final state and audit.
        assertEquals("SUCCESS", getStatus(taskId));
        assertEquals(1, getRetryCount(taskId));
        assertEquals(5L, countAudits(taskId));
    }

    private String getStatus(Long taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE id = ?",
                String.class,
                taskId
        );
    }

    private int getRetryCount(Long taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM task WHERE id = ?",
                Integer.class,
                taskId
        );
        return count;
    }

    private long countAudits(Long taskId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_state_audit WHERE task_id = ?",
                Long.class,
                taskId
        );
        return count;
    }
}
