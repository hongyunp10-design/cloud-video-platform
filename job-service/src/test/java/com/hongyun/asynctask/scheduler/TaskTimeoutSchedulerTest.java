package com.hongyun.asynctask.scheduler;

import com.hongyun.asynctask.config.TaskTimeoutSchedulerProperties;
import com.hongyun.asynctask.dto.response.RunningTaskTimeoutResponse;
import com.hongyun.asynctask.service.TaskTimeoutService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class TaskTimeoutSchedulerTest {
    @Test
    void scanTimeOutRunningTasks_shouldCallTimeoutService_whenSchedulerIsEnabled() {
        TaskTimeoutService taskTimeoutService = mock(TaskTimeoutService.class);

        TaskTimeoutSchedulerProperties properties =new TaskTimeoutSchedulerProperties();

        properties.setEnabled(true);
        properties.setTimeoutSeconds(300);

        when(taskTimeoutService.markTimedOutRunningTasksAsFailed(300))
                .thenReturn(new RunningTaskTimeoutResponse(1 , 1, List.of(100L)));

        TaskTimeoutScheduler scheduler = new TaskTimeoutScheduler(
                taskTimeoutService,
                properties
        );

        scheduler.scanTimedOutRunningTasks();

        verify(taskTimeoutService).markTimedOutRunningTasksAsFailed(300);
    }

    @Test
    void scanTimeOutRunningTasks_ShouldNotCallTimeoutService_shenSchedulerIsDisabled() {
        TaskTimeoutService taskTimeoutService = mock(TaskTimeoutService.class);

        TaskTimeoutSchedulerProperties properties = new TaskTimeoutSchedulerProperties();
        properties.setEnabled(false);
        properties.setTimeoutSeconds(300);

        TaskTimeoutScheduler scheduler = new TaskTimeoutScheduler(
                taskTimeoutService,
                properties
        );

        scheduler.scanTimedOutRunningTasks();

        verify(taskTimeoutService, never()).markTimedOutRunningTasksAsFailed(300);
    }


}