package com.hongyun.asynctask.dto;

import com.hongyun.asynctask.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UpdateTaskRequest {
    @NotBlank(message = "name must not be blank")
    private String name;
    @NotBlank(message = "type must not be blank")
    private String type;
    @NotNull(message = "status must not be null")
    private TaskStatus status;

    public UpdateTaskRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}