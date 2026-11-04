package com.hongyun.asynctask.executor;

import com.hongyun.asynctask.exception.TaskExecutorNotFoundException;
import com.hongyun.asynctask.model.TaskType;
import com.hongyun.asynctask.service.TaskExecutionService;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskExecutorRegistry  {
    private final Map<TaskType, TaskExecutor> executorMap = new EnumMap<>(TaskType.class);

    public TaskExecutorRegistry(List<TaskExecutor> taskExecutors) {
        for(TaskExecutor taskExecutor : taskExecutors) {
            TaskType taskType = taskExecutor.supportedType();

            if (executorMap.containsKey(taskType)) {
                throw new IllegalArgumentException("Duplicate task executor for task type: " + taskType);
            }

            executorMap.put(taskType, taskExecutor);
        }
    }

    public TaskExecutor getExecutor(TaskType taskType) {
        TaskExecutor taskExecutor = executorMap.get(taskType);

        if (taskExecutor == null) {
            throw new TaskExecutorNotFoundException(taskType);
        }

        return taskExecutor;
    }
}