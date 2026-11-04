package com.hongyun.asynctask.dto;

import com.hongyun.asynctask.model.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateTaskRequest {
    @NotBlank(message = "name must not be blank")
    private String name;

    @NotNull(message = "type must not be null")
    private TaskType type;

    public CreateTaskRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
    }
}