package com.hongyun.asynctask.model;

public class TaskExecutionResult {
    public enum Outcome{
        SUCCESS,
        FAILED,
        SKIPPED
    }

    private final Long taskId;
    private final Outcome outcome;
    private final TaskStatus finalStatus;

    public TaskExecutionResult(Long taskId, TaskStatus finalStatus) {
        this.taskId = taskId;
        this.finalStatus = finalStatus;

        if (finalStatus == TaskStatus.SUCCESS) {
            this.outcome = Outcome.SUCCESS;
        }else if (finalStatus == TaskStatus.FAILED) {
            this.outcome = Outcome.FAILED;
        }else {
            throw new IllegalArgumentException(
                    "Unsupported finalState for task execution result: " + finalStatus
            );
        }
    }

    private TaskExecutionResult(Long taskId, Outcome outcome, TaskStatus finalStatus) {
        this.taskId = taskId;
        this.outcome = outcome;
        this.finalStatus = finalStatus;
    }

    public static TaskExecutionResult skipped(Long taskId) {
        return new TaskExecutionResult(taskId, Outcome.SKIPPED, null);
    }

    public Long getTaskId() {
        return taskId;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public TaskStatus getFinalStatus() {
        return finalStatus;
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public boolean isFailed() {
        return outcome == Outcome.FAILED;
    }

    public boolean isSkipped() {
        return outcome == Outcome.SKIPPED;
    }
}