
package com.hongyun.asynctask.service;

import com.hongyun.asynctask.mapper.MediaTaskMapper;
import com.hongyun.asynctask.model.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
    void shouldCommitAndRollbackMediaTaskTransaction() {

        String videoId = UUID.randomUUID().toString();

        // 1. Successful transaction.
        Task task = mediaTaskService.createMediaTask(
                "media-transaction-test",
                videoId
        );

        try {
            assertNotNull(task.getId());

            assertEquals(
                    videoId,
                    mediaTaskMapper.selectVideoIdByTaskId(task.getId())
            );

            // 2. Record database row counts.
            Integer tasksBefore = countRows("task");
            Integer mediaBefore = countRows("media_task");

            // 3. Duplicate videoId violates UNIQUE constraint.
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> mediaTaskService.createMediaTask(
                            "duplicate-media-test",
                            videoId
                    )
            );

            // 4. Both tables must remain unchanged.
            assertEquals(tasksBefore, countRows("task"));
            assertEquals(mediaBefore, countRows("media_task"));

            // Original association must still exist.
            assertEquals(
                    videoId,
                    mediaTaskMapper.selectVideoIdByTaskId(task.getId())
            );

        } finally {
            // 5. Clean up committed test data.
            // FK cascade deletes the media_task row.
            jdbcTemplate.update(
                    "DELETE FROM task WHERE id = ?",
                    task.getId()
            );
        }
    }

    private Integer countRows(String table) {
        // Only called with fixed test-controlled table names.
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class
        );
    }
}
