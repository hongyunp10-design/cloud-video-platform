package com.hongyun.asynctask.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.task.timeout-scheduler")
public class TaskTimeoutSchedulerProperties {

    private boolean enabled = true;
    private long fixedDelayMs = 60000;
    private long initialDelayMs = 60000;
    private long timeoutSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        if(fixedDelayMs <= 0) {
            throw new IllegalArgumentException("fixedDelayMs must be positive");
        }
        this.fixedDelayMs = fixedDelayMs;
    }

    public long getInitialDealyMs() {
        return initialDelayMs;
    }

    public void setInitialDealyMs(long initialDealyMs) {
        if (initialDealyMs < 0) {
            throw new IllegalArgumentException("initialDelayMs must not be negative");
        }
        this.initialDelayMs = initialDealyMs;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        this.timeoutSeconds = timeoutSeconds;
    }
}