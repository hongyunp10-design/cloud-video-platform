package com.hongyun.asynctask.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(TaskExecutionAsyncProperties.class)
public class TaskExecutionAsyncConfig {
    @Bean(name = "taskExecutionExecutor")
    public ThreadPoolTaskExecutor taskExecutionExecutor(
            TaskExecutionAsyncProperties properties
    ) {
        properties.validate();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getMaxPoolSize());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        return executor;
    }
}