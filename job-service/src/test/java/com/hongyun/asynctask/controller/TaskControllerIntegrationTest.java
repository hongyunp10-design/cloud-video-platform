package com.hongyun.asynctask.controller;

import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =  {
        "app.task.retry-scheduler.enabled=false",
        "app.task.execution-worker.enbaled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper  taskStateAuditMapper;

    private Long taskId;

    @BeforeEach
    void setUp() {
        Task task = new Task(
                null,
                "controller-audit-test-task",
                TaskType.MEDIA,
                TaskStatus.FAILED,
                2,
                2
        );

        taskMapper.insertTask(task);
        taskId = task.getId();

        insertAudit(
                TaskStatus.PENDING,
                TaskStatus.RUNNING,
                TaskStateActor.WORKER,
                TaskStateChangeReason.WORKER_PICKED_PENDING_TASK,
                0
        );

        insertAudit(
                TaskStatus.RUNNING,
                TaskStatus.FAILED,
                TaskStateActor.WORKER,
                TaskStateChangeReason.WORKER_EXECUTION_FAILED,
                0
        );

        insertAudit(
                TaskStatus.FAILED,
                TaskStatus.PENDING,
                TaskStateActor.RETRY_SCHEDULER,
                TaskStateChangeReason.RETRY_ELIGIBLE_FAILED_TASK,
                1
        );

        insertAudit(
                TaskStatus.FAILED,
                TaskStatus.DEAD,
                TaskStateActor.RETRY_SCHEDULER,
                TaskStateChangeReason.RETRY_LIMIT_REACHED,
                2
        );
    }

    @AfterEach
    void tearDown() {
        if(taskId != null) {
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(taskId);
            taskMapper.deleteTaskById(taskId);
        }
    }

    @Test
    void getTaskStateAudits_shouldReturnAllAudits_whenNoFilterProvided() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}/audits", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].taskId").value(taskId))
                .andExpect(jsonPath("$.data[0].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.data[0].toStatus").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].actor").value("WORKER"))
                .andExpect(jsonPath("$.data[0].reason").value("WORKER_PICKED_PENDING_TASK"));
    }

    @Test
    void getTaskStateAudit_shouldFilterByActor() throws Exception{
        mockMvc.perform(get("/api/tasks/{id}/audits", taskId)
                .param("actor", "WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].actor").value("WORKER"))
                .andExpect(jsonPath("$.data[1].actor").value("WORKER"));
    }

    @Test
    void getTaskStateAudit_shouldFilterByReason() throws Exception{
        mockMvc.perform(get("/api/tasks/{id}/audits", taskId)
                        .param("reason", "WORKER_EXECUTION_FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fromStatus").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].toStatus").value("FAILED"))
                .andExpect(jsonPath("$.data[0].actor").value("WORKER"))
                .andExpect(jsonPath("$.data[0].reason").value("WORKER_EXECUTION_FAILED"))
        ;
    }
    @Test

    void getTaskStateAudits_shouldFilterByActorAndReason() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}/audits", taskId)
                        .param("actor", "RETRY_SCHEDULER")
                        .param("reason", "RETRY_LIMIT_REACHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fromStatus").value("FAILED"))
                .andExpect(jsonPath("$.data[0].toStatus").value("DEAD"))
                .andExpect(jsonPath("$.data[0].actor").value("RETRY_SCHEDULER"))
                .andExpect(jsonPath("$.data[0].reason").value("RETRY_LIMIT_REACHED"))
        ;
    }

    @Test
    void getTaskStateAudits_shouldReturnEmptyList_whenFilterDoesNotMatch() throws Exception{
        mockMvc.perform(get("/api/tasks/{id}/audits", taskId)
                .param("actor", "WORKER")
                        .param("reason", "RETRY_LIMIT_REACHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(0))
        ;
    }


    @Test
    void getTaskStateAudits_shouldReturnInvalidRequestParam_whenActorIsInvalid() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}/audits", taskId)
                        .param("actor", "worker"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAM"))
                .andExpect(jsonPath("$.message").value(
                        containsString("Invalid request parameter: actor=worker")
                ))
                .andExpect(jsonPath("$.message").value(
                        containsString("Allowed values: MANUAL, WORKER, RETRY_SCHEDULER")
                ))
                .andExpect(jsonPath("$.data").doesNotExist())
        ;

    }


    @Test
    void getTaskStateAudits_shouldReturnInvalidRequestParam_whenReasonIsInvalid() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}/audits", taskId)
                        .param("reason", "bad_reason"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAM"))
                .andExpect(jsonPath("$.message").value(
                        containsString("Invalid request parameter: reason=bad_reason")

                ))
                .andExpect(jsonPath("$.message").value(
                        containsString("Allowed values:")
                ))
                .andExpect(jsonPath("$.message").value(
                        containsString("WORKER_EXECUTION_FAILED")
                ))
                .andExpect(jsonPath("$.data").doesNotExist());
    }


    @Test
    void getTaskStateAudits_shouldReturnTaskNotFound_whenTaskDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}/audits", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found: 999999"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void insertAudit(
            TaskStatus fromStatus,
            TaskStatus toStatus,
            TaskStateActor actor,
            TaskStateChangeReason reason,
            Integer retryCount
    ) {
        TaskStateAudit audit = new TaskStateAudit(
                null,
                taskId,
                fromStatus,
                toStatus,
                actor,
                reason,
                retryCount,
                2,
                LocalDateTime.now()
        );

        taskStateAuditMapper.insertTaskStateAudit(audit);
    }
}
