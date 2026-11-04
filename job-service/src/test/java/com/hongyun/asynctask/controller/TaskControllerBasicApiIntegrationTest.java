package com.hongyun.asynctask.controller;

import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStatus;
import com.hongyun.asynctask.model.TaskType;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.task.retry-scheduler.enabled=false",
        "app.task.execution-worker.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskControllerBasicApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskStateAuditMapper taskStateAuditMapper;

    private final List<Long> createdTaskIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long taskId : createdTaskIds) {
            taskStateAuditMapper.deleteTaskStateAuditsByTaskId(taskId);
            taskMapper.deleteTaskById(taskId);
        }
        createdTaskIds.clear();
    }

    @Test
    void createTask_shouldCreatePendingTask() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "api-create-test-task",
                                  "type": "MEDIA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("api-create-test-task"))
                .andExpect(jsonPath("$.data.type").value("MEDIA"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.retryCount").value(0))
                .andExpect(jsonPath("$.data.maxRetryCount").value(2))
                .andReturn();

        Long taskId = readLong(result, "$.data.id");
        createdTaskIds.add(taskId);
    }

    @Test
    void getTaskById_shouldReturnTask_whenTaskExists() throws Exception {
        Task task = insertTask("api-get-test-task", TaskType.EMAIL, TaskStatus.PENDING, 0, 5);

        mockMvc.perform(get("/api/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.id").value(task.getId()))
                .andExpect(jsonPath("$.data.name").value("api-get-test-task"))
                .andExpect(jsonPath("$.data.type").value("EMAIL"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.retryCount").value(0))
                .andExpect(jsonPath("$.data.maxRetryCount").value(5));
    }

    @Test
    void getTaskById_shouldReturnTaskNotFound_whenTaskDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found: 999999"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void updateTask_shouldUpdateTaskMetadata() throws Exception {
        Task task = insertTask("api-update-before-task", TaskType.MEDIA, TaskStatus.PENDING, 0, 2);

        mockMvc.perform(put("/api/tasks/{id}/metadata", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "api-update-after-task",
                                  "type": "EMAIL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.id").value(task.getId()))
                .andExpect(jsonPath("$.data.name").value("api-update-after-task"))
                .andExpect(jsonPath("$.data.type").value("EMAIL"));

        mockMvc.perform(get("/api/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("api-update-after-task"))
                .andExpect(jsonPath("$.data.type").value("EMAIL"));
    }

    @Test
    void updateTaskStatus_shouldUpdateStatusAndInsertAudit_whenTransitionIsValid() throws Exception {
        Task task = insertTask("api-status-valid-task", TaskType.MEDIA, TaskStatus.PENDING, 0, 2);

        mockMvc.perform(put("/api/tasks/{id}/status", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "RUNNING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.id").value(task.getId()))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));

        mockMvc.perform(get("/api/tasks/{id}/audits", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.data[0].toStatus").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].actor").value("MANUAL"))
                .andExpect(jsonPath("$.data[0].reason").value("MANUAL_STATUS_UPDATE"));
    }

    @Test
    void updateTaskStatus_shouldReturnInvalidTransition_whenTransitionIsInvalid() throws Exception {
        Task task = insertTask("api-status-invalid-task", TaskType.MEDIA, TaskStatus.PENDING, 0, 2);

        mockMvc.perform(put("/api/tasks/{id}/status", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUCCESS"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_STATE_TRANSITION"))
                .andExpect(jsonPath("$.message").value(
                        containsString("Invalid task state transition")
                ))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/tasks/{id}/audits", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void deleteTask_shouldDeleteTask_whenTaskExists() throws Exception {
        Task task = insertTask("api-delete-test-task", TaskType.REPORT, TaskStatus.PENDING, 0, 3);

        mockMvc.perform(delete("/api/tasks/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Task deleted successfully"));

        mockMvc.perform(get("/api/tasks/{id}", task.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found: " + task.getId()));
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
        createdTaskIds.add(task.getId());

        return task;
    }

    private Long readLong(MvcResult result, String jsonPath) throws Exception {
        Object value = JsonPath.read(
                result.getResponse().getContentAsString(),
                jsonPath
        );

        if (value instanceof Integer intValue) {
            return intValue.longValue();
        }

        if (value instanceof Long longValue) {
            return longValue;
        }

        throw new IllegalStateException("Cannot convert value to Long: " + value);
    }
}