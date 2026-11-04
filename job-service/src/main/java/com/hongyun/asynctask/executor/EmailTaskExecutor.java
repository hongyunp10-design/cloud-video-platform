package com.hongyun.asynctask.executor;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailTaskExecutor implements TaskExecutor{
    private static final Logger log = LoggerFactory.getLogger(EmailTaskExecutor.class);

    @Override
    public TaskType supportedType() {
        return TaskType.EMAIL;
    }

    @Override
    public void execute(Task task) {
        log.info(
                "Email task executor started: taskId={}, name={}",
                task.getId(),
                task.getName()
        );

        simulateEmailProcessing(task);

        if(shouldSimulateFailure(task)) {
            throw new RuntimeException("Simulated email task execution failure: taskId=" + task.getId());
        }

        log.info(
                "Email task executor completed: taskId={}, name={}",
                task.getId(),
                task.getName()
        );
    }

    private void simulateEmailProcessing(Task task) {
        try {
            Thread.sleep(500);
        }catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email task execution interrupted: taskId=" + task.getId(), ex);
        }
    }

    private boolean shouldSimulateFailure(Task task) {
        return task.getName() != null && task.getName().toLowerCase().contains("fail");
    }
}