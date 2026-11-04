package com.hongyun.asynctask.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskExecutionAsyncPropertiesTest {
    @Test
    void validate_shouldPass_whenPropertiesAreValid() {
        TaskExecutionAsyncProperties properties = new TaskExecutionAsyncProperties();
        properties.setCorePoolSize(2);
        properties.setMaxPoolSize(4);
        properties.setQueueCapacity(100);
        properties.setThreadNamePrefix("task-execution-");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldThrowException_whenCorePoolSizeIsLessThanOne() {
        TaskExecutionAsyncProperties properties = new TaskExecutionAsyncProperties();
        properties.setCorePoolSize(0);
        properties.setMaxPoolSize(4);
        properties.setQueueCapacity(100);
        properties.setThreadNamePrefix("teak-execution-");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("core-pool-size must be >= 1");
    }

    @Test
    void validate_shouldThrowException_whenMaxPoolSizeIsLessThanCorePoolSize() {
        TaskExecutionAsyncProperties properties = new TaskExecutionAsyncProperties();
        properties.setCorePoolSize(4);
        properties.setMaxPoolSize(2);
        properties.setQueueCapacity(100);
        properties.setThreadNamePrefix("task-execution-");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-pool-size must be >= core-pool-size");
    }



    @Test
    void validate_shouldThrowException_whenQueueCapacityIsNegative() {
        TaskExecutionAsyncProperties properties = new TaskExecutionAsyncProperties();
        properties.setCorePoolSize(2);
        properties.setMaxPoolSize(4);
        properties.setQueueCapacity(-1);
        properties.setThreadNamePrefix("task-execution-");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queue-capacity must be >= 0");
    }

    @Test
    void validate_shouldThrowException_whenThreadNamePrefixIsBlank() {
        TaskExecutionAsyncProperties properties = new TaskExecutionAsyncProperties();
        properties.setCorePoolSize(2);
        properties.setMaxPoolSize(4);
        properties.setQueueCapacity(100);
        properties.setThreadNamePrefix(" ");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thread-name-prefix must not be blank");
    }
}