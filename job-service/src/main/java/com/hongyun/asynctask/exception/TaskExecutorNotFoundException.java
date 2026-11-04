package com.hongyun.asynctask.exception;

import com.hongyun.asynctask.model.TaskType;

public  class TaskExecutorNotFoundException  extends RuntimeException{
    public TaskExecutorNotFoundException(TaskType taskType) {
        super("Task executor not found for task type: " + taskType);
    }

}