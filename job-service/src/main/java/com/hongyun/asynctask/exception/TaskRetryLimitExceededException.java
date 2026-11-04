package com.hongyun.asynctask.exception;

public class TaskRetryLimitExceededException extends RuntimeException {
    public TaskRetryLimitExceededException(Long taskId, Integer maxRetryCount) {
        super("Task retry limit exceeded : taskId=" + taskId + ", maxRetryCount=" + maxRetryCount);
    }
}