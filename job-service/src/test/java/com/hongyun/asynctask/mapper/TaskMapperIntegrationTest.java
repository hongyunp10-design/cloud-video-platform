package com.hongyun.asynctask.mapper;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStatus;
import com.hongyun.asynctask.model.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "app.task.execution-worker.enbaled=false",
                "app.task.retry-scheduler.enabled=false"
        }
)

@ActiveProfiles("test")
class TaskMapperIntegrationTest {

    @Autowired
    private TaskMapper taskMapper;

    private Long createdTaskId;

    @AfterEach
    void tearDown() {
        if (createdTaskId != null) {
            taskMapper.deleteTaskById(createdTaskId);
        }
    }

    @Test
    void insertAndSelectTask_shouldPersistLastErrorMessage() {
        Task task = new Task(
                null,
                "mapper-last-error-message-test-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                1,
                2,
                "simulated failure for mapper test"
        );

        taskMapper.insertTask(task);
        createdTaskId =task.getId();

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb).isNotNull();
        assertThat(taskInDb.getId()).isEqualTo(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskInDb.getLastErrorMessage()).isEqualTo("simulated failure for mapper test");
    }

    @Test

    void insertAndSelectTask_shouldAllowNullLastErrorMessage() {
        Task task = new Task(
                null,
                "mapper-null-last-error-message-test-task",
                TaskType.EMAIL,
                TaskStatus.PENDING,
                0,
                5
        );

        taskMapper.insertTask(task);
        createdTaskId = task.getId();

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb).isNotNull();
        assertThat(taskInDb.getLastErrorMessage()).isNull();
    }

    @Test
    void insertAndSelectTask_shouldPopulateTimestamps() {
        Task task = new Task(
                null,
                "mapper-timestamp-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        taskMapper.insertTask(task);
        createdTaskId = task.getId();

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb).isNotNull();
        assertThat(taskInDb.getCreatedAt()).isNotNull();
        assertThat(taskInDb.getUpdatedAt()).isNotNull();
        assertThat(taskInDb.getStartedAt()).isNull();
    }
}