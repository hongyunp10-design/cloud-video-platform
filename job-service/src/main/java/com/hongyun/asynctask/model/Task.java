package com.hongyun.asynctask.model;

import org.springframework.cglib.core.Local;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Task implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private TaskType type;
    private TaskStatus status;

    private Integer retryCount;
    private Integer maxRetryCount;
    private String lastErrorMessage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;

    public Task() {
    }

    public Task(
            Long id,
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount
    ) {
        this(id, name, type, status, retryCount, maxRetryCount, null);
    }

    public Task(
            Long id,
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount,
            String lastErrorMessage
    ){
        this(id, name, type, status, retryCount, maxRetryCount, lastErrorMessage, null, null, null);
    }

    public Task(
            Long id,
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount,
            String lastErrorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime startedAt
    ){
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.retryCount = retryCount;
        this.maxRetryCount = maxRetryCount;
        this.lastErrorMessage = lastErrorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }


    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
}