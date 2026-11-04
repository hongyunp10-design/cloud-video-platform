package com.hongyun.asynctask.executor;

import com.hongyun.asynctask.model.TaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "app.task.execution-worker.enabled=false",
                "app.task.rey-scheduler.enabled=false"
        }
)

@ActiveProfiles("test")
class TaskExecutorRegistryIntegrationTest {

    @Autowired
    private TaskExecutorRegistry taskExecutorRegistry;

    @Test
    void getExecutor_shouldReturnMediaTaskExecutor_whenTaskTypeIsMedia() {
        TaskExecutor taskExecutor = taskExecutorRegistry.getExecutor(TaskType.MEDIA);

        assertThat(taskExecutor).isInstanceOf(MediaTaskExecutor.class);
        assertThat(taskExecutor.supportedType()).isEqualTo(TaskType.MEDIA);
    }

    @Test
    void getExecutor_shouldReturnEmailTaskExecutor_whenTaskTypeIsEmail() {
        TaskExecutor taskExecutor = taskExecutorRegistry.getExecutor(TaskType.EMAIL);

        assertThat(taskExecutor).isInstanceOf(EmailTaskExecutor.class);
        assertThat(taskExecutor.supportedType()).isEqualTo(TaskType.EMAIL);
    }

    @Test
    void getExecutor_shouldReturnReportTaskExecutor_whenTaskTypeIsReport() {
        TaskExecutor taskExecutor = taskExecutorRegistry.getExecutor(TaskType.REPORT);

        assertThat(taskExecutor).isInstanceOf(ReportTaskExecutor.class);
        assertThat(taskExecutor.supportedType()).isEqualTo(TaskType.REPORT);
    }
}