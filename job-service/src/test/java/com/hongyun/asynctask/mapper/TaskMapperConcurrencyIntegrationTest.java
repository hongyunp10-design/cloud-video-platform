package com.hongyun.asynctask.mapper;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStatus;
import com.hongyun.asynctask.model.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "app.task.execution-worker.enabled=false",
                "app.task.retry-scheduler.enabled=false"
        }
)
@ActiveProfiles("test")
class  TaskMapperConcurrencyIntegrationTest {
    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper taskStateAuditMapper;

    private final List<Long> createdTaskIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for(Long taskId : createdTaskIds) {
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(taskId);
            taskMapper.deleteTaskById(taskId);
        }
        createdTaskIds.clear();
    }

    @Test
    void updateTaskStatusIfCurrentStatus_shouldUpdate_whenCurrentStatusMatches() {
        Task task = insertTask(
                "mapper-concurrency-status-match-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        int updateRows = taskMapper.updateTaskStatusIfCurrentStatus(
                task.getId(),
                TaskStatus.PENDING,
                TaskStatus.RUNNING
        );

        assertThat(updateRows).isEqualTo(1);

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);
    }

    @Test
    void updateTaskStatusIfCurrentStatus_shouldNotUpdate_whenCurrentStatusDoesNotMatch() {
        Task task = insertTask(
                "mapper-concurrency-status-mismatch-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2
        );

        int updateRows = taskMapper.updateTaskStatusIfCurrentStatus(
                task.getId(),
                TaskStatus.PENDING,
                TaskStatus.RUNNING
        );

        assertThat(updateRows).isEqualTo(0);

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);
    }


    @Test
    void retryTaskIfCurrentStatusAndRetryCount_shouldUpdate_whenStatusAndRetryCountMatch() {
        Task task = insertTask(
                "mapper-concurrency-retry-match-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                0,
                2
        );

        int updateRows = taskMapper.retryTaskIfCurrentStatusAndRetryCount(
                task.getId(),
                TaskStatus.FAILED,
                0,
                TaskStatus.PENDING,
                1
        );

        assertThat(updateRows).isEqualTo(1);

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(taskInDb.getRetryCount()).isEqualTo(1);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);
    }

    @Test
    void retryTaskIfCurrentStatusAndRetryCount_shouldNotUpdate_whenRetryCountDoesNotMatch() {
        Task task = insertTask(
                "mapper-concurrency-retry-mismatch-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                1,
                2
        );

        int updateRows = taskMapper.retryTaskIfCurrentStatusAndRetryCount(
                task.getId(),
                TaskStatus.FAILED,
                0,
                TaskStatus.PENDING,
                1
        );

        assertThat(updateRows).isEqualTo(0);

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskInDb.getRetryCount()).isEqualTo(1);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);
    }

    private Task insertTask(
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount
    ) {
        Task task = new Task(
                null,
                name,
                type,
                status,
                retryCount,
                maxRetryCount
        );

        taskMapper.insertTask(task);
        createdTaskIds.add(task.getId());
        return task;
    }
}