
package com.hongyun.asynctask.service;

import com.hongyun.asynctask.exception.ConcurrentTaskStateChangeException;
import com.hongyun.asynctask.exception.InvalidTaskStateTransitionException;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.task.execution-worker.enabled=false",
        "app.task.retry-scheduler.enabled=false",
        "app.task.timeout-scheduler.enabled=false"
})
@ActiveProfiles("test")
class TaskConcurrentClaimIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskStateService taskStateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long createdTaskId;

    @AfterEach
    void cleanup() {
        // No @Transactional on this test:
        // worker threads use independent DB transactions.
        if (createdTaskId != null) {
            jdbcTemplate.update(
                    "DELETE FROM task_state_audit WHERE task_id = ?",
                    createdTaskId
            );

            jdbcTemplate.update(
                    "DELETE FROM task WHERE id = ?",
                    createdTaskId
            );
        }
    }

    @Test
    void onlyOneWorkerCanClaimSamePendingTask() throws Exception {

        // 1. Create a committed PENDING task.
        Task task = taskService.createTask(
                "concurrent-claim-test",
                TaskType.REPORT
        );

        Long taskId = task.getId();
        createdTaskId = taskId;

        // 2. Prepare two independent threads.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Both threads signal when they are ready.
        CountDownLatch ready = new CountDownLatch(2);

        // Both threads start claiming after this signal.
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> claimAction = () -> {

            ready.countDown();

            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for claim start"
                );
            }

            try {
                taskStateService.claimTask(taskId);
                return true;

            } catch (ConcurrentTaskStateChangeException |
                     InvalidTaskStateTransitionException ex) {

                // Expected for the losing worker.
                return false;
            }
        };

        try {
            // 3. Submit both worker actions.
            Future<Boolean> workerA = executor.submit(claimAction);
            Future<Boolean> workerB = executor.submit(claimAction);

            // 4. Ensure both threads are ready.
            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "Both workers should be ready"
            );

            // 5. Release both workers simultaneously.
            start.countDown();

            // 6. Collect results.
            boolean resultA = workerA.get(10, TimeUnit.SECONDS);
            boolean resultB = workerB.get(10, TimeUnit.SECONDS);

            int successfulClaims =
                    (resultA ? 1 : 0) + (resultB ? 1 : 0);

            // Exactly one worker can claim this task.
            assertEquals(1, successfulClaims);

            // 7. Verify final database state.
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM task WHERE id = ?",
                    String.class,
                    taskId
            );

            assertEquals("RUNNING", status);

            // Claim should set started_at.
            Integer startedAtCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task " +
                            "WHERE id = ? AND started_at IS NOT NULL",
                    Integer.class,
                    taskId
            );

            assertEquals(1, startedAtCount);

            // 8. Exactly one PENDING -> RUNNING audit.
            Long auditCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task_state_audit " +
                            "WHERE task_id = ? " +
                            "AND from_status = 'PENDING' " +
                            "AND to_status = 'RUNNING'",
                    Long.class,
                    taskId
            );

            assertEquals(1L, auditCount);

        } finally {
            // Always release waiting workers and stop the pool.
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Worker threads should terminate"
            );
        }
    }
}
