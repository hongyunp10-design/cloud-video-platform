package com.hongyun.asynctask.dto.response;

public class TaskStatsResponse {

    private final long pendingCount;
    private final long runningCount;
    private final long successCount;
    private final long failedCount;
    private final long deadCount;
    private final long totalCount;

    public TaskStatsResponse(
            long pendingCount,
            long runningCount,
            long successCount,
            long failedCount,
            long deadCount){
        this.pendingCount = pendingCount;
        this.runningCount = runningCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.deadCount = deadCount;
        this.totalCount = pendingCount
                + runningCount
                + successCount
                + failedCount
                + deadCount;
    }


    public long getPendingCount() {
        return pendingCount;
    }

    public long getRunningCount() {
        return runningCount;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public long getDeadCount() {
        return deadCount;
    }

    public long getTotalCount() {
        return totalCount;
    }
}