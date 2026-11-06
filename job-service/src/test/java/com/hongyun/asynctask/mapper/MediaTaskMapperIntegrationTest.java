
package com.hongyun.asynctask.mapper;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;
import com.hongyun.asynctask.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "app.task.execution-worker.enabled=false"
})
@ActiveProfiles("test")

@Transactional
class MediaTaskMapperIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private MediaTaskMapper mediaTaskMapper;

    @Test
    void shouldInsertAndFindVideoId() {
        // 1. Create a Java task.
        Task task = taskService.createTask(
                "media-mapper-test",
                TaskType.MEDIA
        );

        // 2. Generate a Python-style video ID.
        String videoId = UUID.randomUUID().toString();

        // 3. Persist the association.
        int rows = mediaTaskMapper.insertMediaTask(
                task.getId(),
                videoId
        );

        assertEquals(1, rows);

        // 4. Read the association from MySQL.
        String actualVideoId =
                mediaTaskMapper.selectVideoIdByTaskId(task.getId());

        assertEquals(videoId, actualVideoId);
    }
}
