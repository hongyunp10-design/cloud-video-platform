package com.hongyun.asynctask.model;

public class TaskSubmissionResult  {

    private final Long taskId;
    private final boolean submitted;
    private final String failureMessage;

    private  TaskSubmissionResult(Long taskId, boolean submitted, String failureMessage) {
        this.taskId = taskId;
        this.submitted = submitted;
        this.failureMessage = failureMessage;
    }

    public static TaskSubmissionResult submitted(Long taskId) {
        return new TaskSubmissionResult(taskId, true, null);
    }

    public static TaskSubmissionResult failed(Long taskId, String failureMessage) {
        return new TaskSubmissionResult(taskId, false, failureMessage);
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public boolean isSubmitted() {
        return submitted;
    }


}