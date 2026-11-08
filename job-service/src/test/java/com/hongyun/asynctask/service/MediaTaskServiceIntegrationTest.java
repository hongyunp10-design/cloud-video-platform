
package com.hongyun.asynctask.service;

import com.hongyun.asynctask.mapper.MediaTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MediaTaskServiceIntegrationTest {

    @Autowired
    private MediaTaskService mediaTaskService;

    @Autowired
    private MediaTaskMapper mediaTaskMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void duplicateMediaTaskShouldReturnExistingTask() {
        String videoId = UUID.randomUUID().toString();

        // 1. First submission creates a new task.
        var first = mediaTaskService.createMediaTaskWithResult(
                "first-submission",
                videoId
        );

        assertTrue(first.created());

        Long firstTaskId = first.task().getId();

        // 2. Record task count before retrying.
        Long taskCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task",
                Long.class
        );

        // 3. Retry the same videoId.
        var second = mediaTaskService.createMediaTaskWithResult(
                "duplicate-submission",
                videoId
        );

        // 4. Idempotent response: same task, not newly created.
        assertFalse(second.created());
        assertEquals(firstTaskId, second.task().getId());

        // 5. No additional task was created.
        Long taskCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task",
                Long.class
        );

        assertEquals(taskCountBefore, taskCountAfter);

        // 6. Only one media_task association exists.
        Long mediaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_task WHERE video_id = ?",
                Long.class,
                videoId
        );

        assertEquals(1L, mediaCount);
    }


    private Integer countRows(String table) {
        // Only called with fixed test-controlled table names.
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class
        );
    }
}
