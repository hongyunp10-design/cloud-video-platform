package com.hongyun.asynctask.worker;

import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.*;
import com.hongyun.asynctask.scheduler.TaskRetryScheduler;
import com.hongyun.asynctask.service.TaskExecutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.task.execution-worker.enabled=true",
        "app.task.retry-scheduler.enabled=true",
        "app.task.execution-worker.bacth-size=1000",
        "app.task.execution-worker.fixed-delay-ms=3600000",
        "app.task.retry-scheduler.fixed-delay-ms=3600000"
})
@ActiveProfiles("test")
class TaskWorkerRetryIntegrationTest {
    @Autowired
    private TaskExecutionWorker taskExecutionWorker;

    @Autowired
    private TaskRetryScheduler taskRetryScheduler;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper taskStateAuditMapper;

    @SpyBean
    private TaskExecutionService taskExecutionService;

    private final List<Long> createdTaskIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for(Long taskId : createdTaskIds) {
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(taskId);
            taskMapper.deleteTaskById(taskId);
        }
        createdTaskIds.clear();
        Mockito.reset(taskExecutionService);
    }

    @Test
    void executionPendingTask_shouldMoveTaskToSuccess_whenTaskExecutionSucceeds() throws Exception {
        Task task = insertTask(
                "worker-success-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        taskExecutionWorker.executePendingTask();

        Task taskInDb = waitUntilTaskStatus(task.getId(), TaskStatus.SUCCESS, 5000);
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(taskInDb.getLastErrorMessage()).isNull();
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        List<TaskStateAudit> audits =
                taskStateAuditMapper.selectTaskStateAuditsByTaskId(task.getId(), null, null);

                assertThat(audits).hasSize(2);

                assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.PENDING);
                assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.RUNNING);
                assertThat(audits.get(0).getActor().name()).isEqualTo("WORKER");
                assertThat(audits.get(0).getReason().name()).isEqualTo("WORKER_PICKED_PENDING_TASK");

                assertThat(audits.get(1).getFromStatus()).isEqualTo(TaskStatus.RUNNING);
                assertThat(audits.get(1).getToStatus()).isEqualTo(TaskStatus.SUCCESS);
                assertThat(audits.get(1).getActor().name()).isEqualTo("WORKER");
                assertThat(audits.get(1).getReason().name()).isEqualTo("WORKER_EXECUTION_SUCCEEDED");
    }


    @Test
    void executePendingTask_shouldMoveTaskToFailed_whenTaskExecutionFails() throws Exception{
        Task task = insertTask(
                "worker-fail-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        taskExecutionWorker.executePendingTask();

        Task taskInDb = waitUntilTaskStatus(task.getId(), TaskStatus.FAILED, 5000);
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskInDb.getLastErrorMessage()).contains("Simulated media task execution failure");
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        List<TaskStateAudit> audits =
                taskStateAuditMapper.selectTaskStateAuditsByTaskId(task.getId(), null, null);

        assertThat(audits).hasSize(2);

        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(0).getActor().name()).isEqualTo("WORKER");
        assertThat(audits.get(0).getReason().name()).isEqualTo("WORKER_PICKED_PENDING_TASK");

        assertThat(audits.get(1).getFromStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(audits.get(1).getToStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audits.get(1).getActor().name()).isEqualTo("WORKER");
        assertThat(audits.get(1).getReason().name()).isEqualTo("WORKER_EXECUTION_FAILED");
    }

    @Test
    void retryFailedTasks_shouldMoveFailedTaskBackToPending_whenRetryCountIsBelowLimit() {
        Task task = insertTask(
                "retry-eligible-test-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                0,
                2,
                "previout execution failure"
        );

        taskRetryScheduler.retryFailedTaskOnScheduler();

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(taskInDb.getRetryCount()).isEqualTo(1);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);
        assertThat(taskInDb.getLastErrorMessage()).isNull();

        List<TaskStateAudit> audits =
                taskStateAuditMapper.selectTaskStateAuditsByTaskId(task.getId(), null, null);

        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getFromStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(audits.get(0).getToStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(audits.get(0).getActor().name()).isEqualTo("RETRY_SCHEDULER");
        assertThat(audits.get(0).getReason().name()).isEqualTo("RETRY_ELIGIBLE_FAILED_TASK");
        assertThat(audits.get(0).getRetryCount()).isEqualTo(1);
        assertThat(audits.get(0).getMaxRetryCount()).isEqualTo(2);
    }


    @Test
    void retryFailedTasks_shouldMoveFailedTaskToDead_whenRetryCountReachesLimit() {

        Task task = insertTask(

                "retry-dead-test-task",

                TaskType.MEDIA,

                TaskStatus.FAILED,

                2,

                2

        );

        taskRetryScheduler.retryFailedTaskOnScheduler();

        Task taskInDb = taskMapper.selectTaskById(task.getId());

        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.DEAD);

        assertThat(taskInDb.getRetryCount()).isEqualTo(2);

        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        List<TaskStateAudit> audits =
                taskStateAuditMapper.selectTaskStateAuditsByTaskId(task.getId(), null, null);

        assertThat(audits).hasSize(1);

        TaskStateAudit audit = audits.get(0);

        assertThat(audit.getFromStatus()).isEqualTo(TaskStatus.FAILED);

        assertThat(audit.getToStatus()).isEqualTo(TaskStatus.DEAD);

        assertThat(audit.getActor().name()).isEqualTo("RETRY_SCHEDULER");

        assertThat(audit.getReason().name()).isEqualTo("RETRY_LIMIT_REACHED");

        assertThat(audit.getRetryCount()).isEqualTo(2);

        assertThat(audit.getMaxRetryCount()).isEqualTo(2);

    }

    @Test
    void executeSingleTask_shouldReturnSkipped_whenTaskCannotBeClaimed() {
        Task task = insertTask(
                "worker-claim-skipped-test-task",
                TaskType.MEDIA,
                TaskStatus.RUNNING,
                0,
                2
        );

        TaskExecutionResult result = taskExecutionService.executeSingleTask(task);

        assertThat(result.getTaskId()).isEqualTo(task.getId());
        assertThat(result.isSkipped()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.getFinalStatus()).isNull();

        Task taskInDb = taskMapper.selectTaskById(task.getId());
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(2);

        List<TaskStateAudit> audits = taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                task.getId(),
                null,
                null
        );

        assertThat(audits).isEmpty();
    }


    @Test
    void executePendingTask_shouldExecuteEmailTaskSuccessfully() throws Exception{
        Task task = insertTask(
                "worker-email-success-test-task",
                TaskType.EMAIL,
                TaskStatus.PENDING,
                0,
                5
        );

        taskExecutionWorker.executePendingTask();

        Task taskInDb = waitUntilTaskStatus(task.getId(), TaskStatus.SUCCESS, 5000);
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(5);

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
        assertThat(audits.get(1).getToStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(audits.get(1).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(1).getReason()).isEqualTo(TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED);
    }


    @Test
    void executePendingTask_shouldExecuteReportTaskSuccessfully() throws Exception{
        Task task = insertTask(
                "worker-report-success-test-task",
                TaskType.REPORT,
                TaskStatus.PENDING,
                0,
                3
        );

        taskExecutionWorker.executePendingTask();

        Task taskInDb = waitUntilTaskStatus(task.getId(), TaskStatus.SUCCESS, 5000);
        assertThat(taskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(taskInDb.getRetryCount()).isEqualTo(0);
        assertThat(taskInDb.getMaxRetryCount()).isEqualTo(3);

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
        assertThat(audits.get(1).getToStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(audits.get(1).getActor()).isEqualTo(TaskStateActor.WORKER);
        assertThat(audits.get(1).getReason()).isEqualTo(TaskStateChangeReason.WORKER_EXECUTION_SUCCEEDED);
    }

    @Test
    void executePendingTask_shouldSubmitAndExecuteMultiplePendingTaskAsynchronously() throws Exception {
        Task mediaTask = insertTask(
                "worker-multiple-media-success-test-task",
                TaskType.MEDIA,
                TaskStatus.PENDING,
                0,
                2
        );

        Task emailTask = insertTask(
                "worker-multiple-email-success-test-task",
                TaskType.EMAIL,
                TaskStatus.PENDING,
                0,
                5
        );

        Task reportTask = insertTask(
                "worker-multiple-report-success-test-task",
                TaskType.REPORT,
                TaskStatus.PENDING,
                0,
                3
        );

        taskExecutionWorker.executePendingTask();

        Task mediaTaskInDb = waitUntilTaskStatus(mediaTask.getId(),  TaskStatus.SUCCESS, 8000);
        Task emailTaskInDb = waitUntilTaskStatus(emailTask.getId(), TaskStatus.SUCCESS, 8000);
        Task reportTaskInDb = waitUntilTaskStatus(reportTask.getId(), TaskStatus.SUCCESS, 8000);

        assertThat(mediaTaskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(emailTaskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(reportTaskInDb.getStatus()).isEqualTo(TaskStatus.SUCCESS);

        assertTaskWorkerSuccessAudits(mediaTask.getId());
        assertTaskWorkerSuccessAudits(emailTask.getId());
        assertTaskWorkerSuccessAudits(reportTask.getId());
    }

    private void assertTaskWorkerSuccessAudits(Long taskId) {
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


    private Task waitUntilTaskStatus(
            Long taskId,
            TaskStatus expectedStatus,
            long timeoutMillis
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {
            Task task = taskMapper.selectTaskById(taskId);

            if (task != null && task.getStatus() == expectedStatus) {
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
    ){
        return  insertTask(name, type, status, retryCount, maxRetryCount, null);
    }

    private Task insertTask(
            String name,
            TaskType type,
            TaskStatus status,
            Integer retryCount,
            Integer maxRetryCount,
            String lastErrorMessage
    ){
        Task task = new Task(
                null,
                name,
                type,
                status,
                retryCount,
                maxRetryCount,
                lastErrorMessage
        );

        taskMapper.insertTask(task);
        createdTaskIds.add(task.getId());

        return task;
    }
}