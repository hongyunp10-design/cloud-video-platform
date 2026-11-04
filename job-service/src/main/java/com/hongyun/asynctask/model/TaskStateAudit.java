package com.hongyun.asynctask.model;

import java.time.LocalDateTime;

public class TaskStateAudit {
    private Long id;
    private Long taskId;
    private TaskStatus fromStatus;
    private TaskStatus toStatus;
    private TaskStateActor actor;
    private TaskStateChangeReason reason;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime createdAt;

    public  TaskStateAudit(){
    }

    public TaskStateAudit(
            Long id,
            Long taskId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            TaskStateActor actor,
            TaskStateChangeReason reason,
            Integer retryCount,
            Integer maxRetryCount,
            LocalDateTime createdAt
            ){
        this.id = id;
        this.taskId = taskId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.reason = reason;
        this.retryCount = retryCount;
        this.maxRetryCount = maxRetryCount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public TaskStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(TaskStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public TaskStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(TaskStatus toStatus) {
        this.toStatus = toStatus;
    }

    public TaskStateActor getActor() {
        return actor;
    }

    public void setActor(TaskStateActor actor) {
        this.actor = actor;
    }

    public TaskStateChangeReason getReason() {
        return reason;
    }

    public void setReason(TaskStateChangeReason reason) {
        this.reason = reason;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}