package com.hongyun.asynctask.model;

public class TaskStateChangeContext {
    private final TaskStateActor actor;
    private final TaskStateChangeReason reason;

    private TaskStateChangeContext(TaskStateActor actor, TaskStateChangeReason reason) {
        this.actor = actor;
        this.reason = reason;
    }

    public static  TaskStateChangeContext of(
            TaskStateActor actor,
            TaskStateChangeReason reason
    ) {
        return new TaskStateChangeContext(actor, reason);
    }


    public TaskStateActor getActor() {
        return actor;
    }

    public TaskStateChangeReason getReason() {
        return reason;
    }

}