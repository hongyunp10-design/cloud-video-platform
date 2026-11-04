package com.hongyun.asynctask.exception;


import com.hongyun.asynctask.model.TaskStatus;

public class ConcurrentTaskStateChangeException extends RuntimeException{
    public ConcurrentTaskStateChangeException(
            Long taskId,
            TaskStatus expectStatus,
            TaskStatus targetStatus
    ) {
        super(
                "Concurrent task state change detected: taskId="
                + taskId
                + ", expectedStatus="
                + expectStatus
                + ", targetStatus"
                + targetStatus
        );
    }
}