package com.hongyun.asynctask.dto;

import java.util.List;

public class RetryTasksResponse {
    private int scannedCount;
    private int retriedCount;
    private int deadCount;
    private List<Long> retriedTaskIds;
    private List<Long> deadTaskIds;


    public RetryTasksResponse() {
    }

    public RetryTasksResponse(
            int scannedCount,
            int retriedCount,
            int deadCount,
            List<Long> retriedTaskIds,
            List<Long> deadTaskIds) {
        this.scannedCount = scannedCount;
        this.retriedCount = retriedCount;
        this.deadCount = deadCount;
        this.retriedTaskIds = retriedTaskIds;
        this.deadTaskIds = deadTaskIds;
    }

    public int getScannedCount() {
        return scannedCount;
    }

    public void setScannedCount(int scannedCount) {
        this.scannedCount = scannedCount;
    }

    public int getDeadCount() {
        return deadCount;
    }

    public void setDeadCount(int deadCount) {
        this.deadCount = deadCount;
    }

    public int getRetriedCount() {
        return retriedCount;
    }

    public void setRetriedCount(int retriedCount) {
        this.retriedCount = retriedCount;
    }

    public List<Long> getRetriedTaskIds() {
        return retriedTaskIds;
    }

    public void setRetriedTaskIds(List<Long> retriedTaskIds) {
        this.retriedTaskIds = retriedTaskIds;
    }

    public List<Long> getDeadTaskIds() {
        return deadTaskIds;
    }

    public void setDeadTaskIds(List<Long> deadTaskIds) {
        this.deadTaskIds = deadTaskIds;
    }
}