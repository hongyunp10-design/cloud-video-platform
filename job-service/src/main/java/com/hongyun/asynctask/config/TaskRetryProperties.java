package com.hongyun.asynctask.config;

import com.hongyun.asynctask.model.TaskType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.task.retry-policy")
public class TaskRetryProperties {

    private Integer defaultMaxRetryCount;
    private Map<TaskType, Integer> perType = new HashMap<>();

    public Integer getDefaultMaxRetryCount() {
        return defaultMaxRetryCount;
    }

    public void setDefaultMaxRetryCount(Integer defaultMaxRetryCount) {
        this.defaultMaxRetryCount = defaultMaxRetryCount;
    }

    public Map<TaskType, Integer> getPerType() {
        return perType;
    }

    public void setPerType(Map<TaskType, Integer> perType) {
        this.perType = perType;
    }
}
