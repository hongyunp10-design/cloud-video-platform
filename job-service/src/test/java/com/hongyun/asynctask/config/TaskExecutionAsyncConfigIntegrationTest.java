package com.hongyun.asynctask.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "app.task.execution-worker.enabled=false",
                "app.task.retry-scheduler.enabled=false"
        }
)

@ActiveProfiles("test")
class TaskExecutionAsyncConfigIntegrationTest {

    @Autowired
    @Qualifier("taskExecutionExecutor")
    private ThreadPoolTaskExecutor taskExecutionExecutor;

    @Autowired
    private TaskExecutionAsyncProperties taskExecutionAsyncProperties;

    @Test
    void taskExecutionExecutor_shouldBeConfiguredFromTestProfile() {
        assertThat(taskExecutionAsyncProperties.getCorePoolSize()).isEqualTo(1);
        assertThat(taskExecutionAsyncProperties.getMaxPoolSize()).isEqualTo(2);
        assertThat(taskExecutionAsyncProperties.getQueueCapacity()).isEqualTo(10);
        assertThat(taskExecutionAsyncProperties.getThreadNamePrefix()).isEqualTo("test-task-execution-");

        assertThat(taskExecutionExecutor).isNotNull();
        assertThat(taskExecutionExecutor.getCorePoolSize()).isEqualTo(1);
        assertThat(taskExecutionExecutor.getMaxPoolSize()).isEqualTo(2);
        assertThat(taskExecutionExecutor.getThreadNamePrefix()).isEqualTo("test-task-execution-");    }
}