package com.hongyun.asynctask.dto;

import com.hongyun.asynctask.model.TaskStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateTaskStatusRequest {
    @NotNull(message = "status must not be null")
    private TaskStatus status;

    public UpdateTaskStatusRequest() {

    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}