package com.hongyun.asynctask.service;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStatus;
import com.hongyun.asynctask.model.TaskSubmissionResult;
import com.hongyun.asynctask.model.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskExecutionAsyncServiceTest {

    @Mock
    private ThreadPoolTaskExecutor taskExecutionExecutor;

    @Mock
    private TaskExecutionService taskExecutionService;

    @InjectMocks
    private TaskExecutionAsyncService taskExecutionAsyncService;

    @Test
    void submitTask_shouldReturnFailedResult_whenExecutorRejectsTask() {
        Task task = new Task(
                1L,
                "submit-rejected-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        doThrow(new TaskRejectedException("executor saturated"))
                .when(taskExecutionExecutor)
                .execute(any(Runnable.class));

        TaskSubmissionResult result = taskExecutionAsyncService.submitTask(task);

        assertThat(result.isSubmitted()).isFalse();
        assertThat(result.getTaskId()).isEqualTo(1L);
        assertThat(result.getFailureMessage()).contains("executor saturated");

        verify(taskExecutionService, never()).executeSingleTask(any(Task.class));
    }
}