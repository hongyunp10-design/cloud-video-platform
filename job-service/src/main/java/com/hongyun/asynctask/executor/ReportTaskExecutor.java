package com.hongyun.asynctask.executor;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReportTaskExecutor implements TaskExecutor{
    private static final Logger log = LoggerFactory.getLogger(ReportTaskExecutor.class);

    @Override
    public TaskType supportedType() {
        return TaskType.REPORT;
    }

    @Override
    public void execute(Task task) {
        log.info(
                "Report task executor started: taskId={}, name={}",
                task.getId(),
                task.getName()
        );

        simulateReportProcessing(task);

        if(shouldSimulateFailure(task)) {
            throw new RuntimeException("Simulated report task execution failure: taskId=" + task.getId());
        }

        log.info(
                "Report task executor completed: taskId={}, name={}",
                task.getId(),
                task.getName()
        );
    }

    private void simulateReportProcessing(Task task) {
        try {
            Thread.sleep(800);
        }catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Report task execution interrupted: taskId=" + task.getId(), ex);
        }
    }

    private boolean shouldSimulateFailure(Task task) {
        return task.getName() != null && task.getName().toLowerCase().contains("fail");
    }
}