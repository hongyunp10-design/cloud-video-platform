package com.hongyun.asynctask.controller;

import com.hongyun.asynctask.dto.response.TaskStatsResponse;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStatus;
import com.hongyun.asynctask.model.TaskType;
import com.hongyun.asynctask.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "app.task.execution-worker.enabled=false",
                "app.task.retry-scheduler.enabled=false"
        }
)

@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskControllerStatsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

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
    void getTaskStats_shouldReturnTaskCountsByStatus() throws Exception {
        TaskStatsResponse before = taskService.getTaskStats();

        insertTask("controller-stats-pending-1", TaskType.MEDIA, TaskStatus.PENDING, 0, 2);
        insertTask("controller-stats-pending-2", TaskType.EMAIL, TaskStatus.PENDING, 0, 5);
        insertTask("controller-stats-running-1", TaskType.MEDIA, TaskStatus.RUNNING, 0, 2);
        insertTask("controller-stats-success-1", TaskType.REPORT, TaskStatus.SUCCESS, 0, 3);
        insertTask("controller-stats-failed-1", TaskType.MEDIA, TaskStatus.FAILED, 1, 2);
        insertTask("controller-stats-dead-1", TaskType.EMAIL, TaskStatus.DEAD, 5, 5);


        mockMvc.perform(get("/api/tasks/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.pendingCount").value(before.getPendingCount() + 2))
                .andExpect(jsonPath("$.data.runningCount").value(before.getRunningCount() + 1))
                .andExpect(jsonPath("$.data.failedCount").value(before.getFailedCount() + 1))
                .andExpect(jsonPath("$.data.deadCount").value(before.getFailedCount() + 1))
                .andExpect(jsonPath("$.data.totalCount").value(before.getTotalCount() + 6)
                );

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
}