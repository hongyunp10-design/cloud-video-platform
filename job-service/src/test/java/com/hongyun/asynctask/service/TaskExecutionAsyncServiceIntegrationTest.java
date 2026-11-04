package com.hongyun.asynctask.service;

import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
        properties = {
                "app.task.execution-worker.enabled=false",
                "app.task.retry-scheduler.enabled=false"
        }
)


@ActiveProfiles("test")
class TaskExecutionAsyncServiceIntegrationTest {

    @Autowired
    private TaskExecutionAsyncService taskExecutionAsyncService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper taskStateAuditMapper;

    private  Long createdTaskId;

    @AfterEach
    void tearDown() {
        if (createdTaskId != null) {
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(createdTaskId);
            taskMapper.deleteTaskById(createdTaskId);
        }
    }


    @Test
    void submitTask_shouldExecuteTaskAsynchronously() throws Exception {
        Task task = insertTask(
                "async-service-success-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        TaskSubmissionResult submissionResult = taskExecutionAsyncService.submitTask(task);

        assertThat(submissionResult.isSubmitted()).isTrue();
        assertThat(submissionResult.getTaskId()).isEqualTo(task.getId());
        assertThat(submissionResult.getFailureMessage()).isNull();

        Task taskInDb = waitUntilTaskStatus(task.getId(), TaskStatus.SUCCESS, 5000);

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        assertTaskHasWorkerSuccessAudits(task.getId());
    }


    @Test
    void submitTask_shouldExecuteOnlyOnce_whenSameTaskSubmittedTwice() throws Exception{
        Task task = insertTask(
                "async-service-duplicate-submit-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        TaskSubmissionResult firstSubmission = taskExecutionAsyncService.submitTask(task);
        TaskSubmissionResult secondSubmission = taskExecutionAsyncService.submitTask(task);

        assertThat(firstSubmission.isSubmitted()).isTrue();
        assertThat(firstSubmission.getTaskId()).isEqualTo(task.getId());
        assertThat(firstSubmission.getFailureMessage()).isNull();

        assertThat(secondSubmission.isSubmitted()).isTrue();
        assertThat(secondSubmission.getTaskId()).isEqualTo(task.getId());
        assertThat(secondSubmission.getFailureMessage()).isNull();

        Task taskInDb = waitUntilTaskStatus(task.getId(), TaskStatus.SUCCESS, 5000);

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        waitUntilAuditCount(task.getId(), 2, 5000);

        assertTaskHasWorkerSuccessAudits(task.getId());
    }

    @Test
    void submitTask_shouldPersistLastErrorMessage_whenTaskExecutionFails() throws Exception{
        Task task = insertTask(
                "async-service-fail-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        TaskSubmissionResult submissionResult = taskExecutionAsyncService.submitTask(task);

        assertThat(submissionResult.isSubmitted()).isTrue();
        assertThat(submissionResult.getTaskId()).isEqualTo(task.getId());
        assertThat(submissionResult.getFailureMessage()).isNull();

        Task taskInDb = waitUntilTaskStatus(task.getId(), TaskStatus.FAILED, 5000);

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskInDb.getLastErrorMessage())
                .contains("Simulated media task execution failure");

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
                );

        assertThat(audits).hasSize(2);

        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(0).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(0).getReason()).isEqualTo(TaskStateChangeReason.WORKER_PICKED_PENDING_TASK);

        assertThat(audits.get(1).getFromStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(1).getToStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audits.get(1).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(1).getReason()).isEqualTo(TaskStateChangeReason.WORKER_EXECUTION_FAILED);
    }


    private void assertTaskHasWorkerSuccessAudits(Long taskId) {
        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                taskId,
                null,
                null
        );

        assertThat(audits).hasSize(2);

        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(0).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(0).getReason()).isEqualTo(TaskStateChangeReason.WORKER_PICKED_PENDING_TASK);

        assertThat(audits.get(1).getFromStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(1).getToStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(audits.get(1).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(1).getReason()).isEqualTo(TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED);
    }





    private void waitUntilAuditCount(
            Long taskId,
            int expectedCount,
            long timeoutMills
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMills;

        while(System.currentTimeMillis() < deadline) {
            List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                    taskId,
                    null,
                    null
            );
            if (audits.size() == expectedCount) {
                return;
            }

            Thread.sleep(100);
        }

        List<TaskStateAudit> finalAudits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                taskId,
                null,
                null
        );

        assertThat(finalAudits).hasSize(expectedCount);
    }





    private Task waitUntilTaskStatus(
            Long taskId,
            TaskStatus expectedStatus,
            long timeoutMillis
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while(System.currentTimeMillis() < deadline) {
            Task task = taskMapper.selectTaskById(taskId);

            if(task != null && task.getStatus() == expectedStatus) {
                return task;
            }

            Thread.sleep(100);
        }

        Task finalTask = taskMapper.selectTaskById(taskId);
        assertThat(finalTask).isNotNull();
        assertThat(finalTask.getStatus()).isEqualTo(expectedStatus);

        return finalTask;
    }





    private Task insertTask(
            String name,

            TaskType type,

            TaskStatus status,

            Integer retryCount,

            Integer maxRetryCount

    ) {

        Task task = new Task(

                null,

                name,

                type,

                status,

                retryCount,

                maxRetryCount

        );

        taskMapper.insertTask(task);

        createdTaskId = task.getId();

        return task;

    }
}