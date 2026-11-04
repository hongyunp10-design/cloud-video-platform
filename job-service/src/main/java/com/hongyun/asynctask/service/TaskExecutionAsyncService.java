package com.hongyun.asynctask.service;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskExecutionResult;
import com.hongyun.asynctask.model.TaskSubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionAsyncService  {

    private  static final Logger log = LoggerFactory.getLogger(TaskExecutionAsyncService.class);

    private final ThreadPoolTaskExecutor taskExecutionExecutor;
    private final TaskExecutionService taskExecutionService;

    public TaskExecutionAsyncService(
        @Qualifier("taskExecutionExecutor") ThreadPoolTaskExecutor taskExecutionExecutor,
        TaskExecutionService taskExecutionService
    ){
        this.taskExecutionExecutor = taskExecutionExecutor;
        this.taskExecutionService = taskExecutionService;
    }

    public TaskSubmissionResult submitTask(Task task) {
        log.info(
                "Submitting task to execution executor: taskId={}, type={}, name={}",
                task.getId(),
                task.getType(),
                task.getName()
        );
        try {
            taskExecutionExecutor.execute(
                    () -> {
                        try {
                            TaskExecutionResult result = taskExecutionService.executeSingleTask(task);

                            log.info(
                                    "Async task execution finished: taskId={}, outcome={}, finalStatus={}",
                                    result.getTaskId(),
                                    result.getOutcome(),
                                    result.getFinalStatus()
                            );
                        } catch (Exception ex) {
                            log.error(
                                    "Unexpected async task execution error: taskId={}, type={}, name={}",
                                    task.getId(),
                                    task.getType(),
                                    task.getName(),
                                    ex
                            );

                        }
                    }
            );
            return TaskSubmissionResult.submitted(task.getId());
        }catch (Exception ex) {
            log.error(
                    "Failed to submit task to execution executorL taskId={}, type={}, name={}",
                    task.getId(),
                    task.getType(),
                    task.getName(),
                    ex
            );
            return TaskSubmissionResult.failed(task.getId(), ex.getMessage());
        }
    }
}