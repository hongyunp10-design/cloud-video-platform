package com.hongyun.asynctask.model;

public enum TaskStateActor {
    MANUAL,
    WORKER,
    RETRY_SCHEDULER,
    TIMEOUT_SCANNER
}