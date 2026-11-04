package com.hongyun.asynctask.exception;

import com.hongyun.asynctask.model.TaskStatus;

public class InvalidTaskStateTransitionException extends RuntimeException {
    public InvalidTaskStateTransitionException(TaskStatus from, TaskStatus to) {
        super("Invalid task state transition: " + from + " -> " + to);
    }
}