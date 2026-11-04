package com.hongyun.asynctask.executor;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MediaTaskExecutor implements TaskExecutor{
    private static final Logger log = LoggerFactory.getLogger(MediaTaskExecutor.class);

    @Override
    public TaskType supportedType() {
        return TaskType.MEDIA;
    }

    @Override
    public void execute(Task task) {
        log.info(
                "Media task executor started: taskId={}, name={}",
                task.getId(),
                task.getName()
        );

        simulateMediaProcessing(task);

        if(shouldSimulateFailure(task)) {
            throw new RuntimeException("Simulated media task execution failure: taskId=" + task.getId());
        }

        log.info(
                "Media task executor completed: taskId={}, name={}",
                task.getId(),
                task.getName()
        );
    }

    private void simulateMediaProcessing(Task task) {
        try {
            Thread.sleep(1000);
        }catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Media task execution interrupted: taskId=" + task.getId(), ex);
        }
    }

    private boolean shouldSimulateFailure(Task task) {
        return task.getName() != null && task.getName().toLowerCase().contains("fail");
    }
}