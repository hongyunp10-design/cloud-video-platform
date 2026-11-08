
package com.hongyun.asynctask.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.task.execution-worker.enabled=false",
        "app.task.retry-scheduler.enabled=false",
        "app.task.timeout-scheduler.enabled=false"
})
@ActiveProfiles("test")
class MediaTaskConcurrentSubmissionIntegrationTest {

    @Autowired
    private MediaTaskService mediaTaskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String nameA;
    private String nameB;

    @Test
    void concurrentSubmissionsShouldReturnSameTask() throws Exception {

        // 1. Two clients submit the SAME videoId.
        String videoId = UUID.randomUUID().toString();

        nameA = "idempotency-race-A-" + videoId;
        nameB = "idempotency-race-B-" + videoId;

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // 2. Each thread calls the real MediaTaskService.
        Callable<MediaTaskService.MediaTaskCreationResult> requestA =
                () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Start timeout");
                    }
                    return mediaTaskService.createMediaTaskWithResult(
                            nameA, videoId
                    );
                };

        Callable<MediaTaskService.MediaTaskCreationResult> requestB =
                () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Start timeout");
                    }
                    return mediaTaskService.createMediaTaskWithResult(
                            nameB, videoId
                    );
                };

        try {
            Future<MediaTaskService.MediaTaskCreationResult> futureA =
                    executor.submit(requestA);

            Future<MediaTaskService.MediaTaskCreationResult> futureB =
                    executor.submit(requestB);

            // 3. Start both requests at approximately the same time.
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            var resultA = futureA.get(10, TimeUnit.SECONDS);
            var resultB = futureB.get(10, TimeUnit.SECONDS);

            // 4. Both clients must receive the SAME task ID.
            Long taskIdA = resultA.task().getId();
            Long taskIdB = resultB.task().getId();

            assertNotNull(taskIdA);
            assertEquals(taskIdA, taskIdB);

            // 5. Exactly one request created the task.
            int createdCount =
                    (resultA.created() ? 1 : 0)
                            + (resultB.created() ? 1 : 0);

            assertEquals(1, createdCount);

            // 6. Exactly one media_task row exists.
            Long mediaCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM media_task WHERE video_id = ?",
                    Long.class,
                    videoId
            );

            assertEquals(1L, mediaCount);

            // 7. Exactly one task was persisted.
            // A failed competing INSERT must be rolled back.
            Long taskCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task WHERE name IN (?, ?)",
                    Long.class,
                    nameA,
                    nameB
            );

            assertEquals(1L, taskCount);

            // 8. The media association points to the returned task.
            Long storedTaskId = jdbcTemplate.queryForObject(
                    "SELECT task_id FROM media_task WHERE video_id = ?",
                    Long.class,
                    videoId
            );

            assertEquals(taskIdA, storedTaskId);

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Worker threads should terminate"
            );
        }
    }

    @AfterEach
    void cleanup() {
        if (nameA == null || nameB == null) {
            return;
        }

        // This test is not @Transactional.
        // Remove any committed test tasks, even if assertions fail.
        List<Long> taskIds = jdbcTemplate.queryForList(
                "SELECT id FROM task WHERE name IN (?, ?)",
                Long.class,
                nameA,
                nameB
        );

        for (Long taskId : taskIds) {
            jdbcTemplate.update(
                    "DELETE FROM task_state_audit WHERE task_id = ?",
                    taskId
            );

            // media_task is deleted by ON DELETE CASCADE.
            jdbcTemplate.update(
                    "DELETE FROM task WHERE id = ?",
                    taskId
            );
        }
    }
}
