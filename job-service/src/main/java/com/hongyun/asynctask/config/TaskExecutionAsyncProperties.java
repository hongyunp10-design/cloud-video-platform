package com.hongyun.asynctask.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.task.execution-async")
public class TaskExecutionAsyncProperties {

    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 100;
    private String threadNamePrefix = "task-execution-";

    public void validate() {
        if(corePoolSize < 1) {
            throw new IllegalArgumentException("app.task.execution-async.core-pool-size must be >= 1");
        }

        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                    "app.task.executon-async.max-pool-size must be >= core-pool-size"
            );
        }

        if(queueCapacity < 0) {
            throw new IllegalArgumentException("app.task.execution-async.queue-capacity must be >= 0");
        }

        if (threadNamePrefix == null || threadNamePrefix.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "app.task.execution-async.thread-name-prefix must not be blank"
            );
        }
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

}