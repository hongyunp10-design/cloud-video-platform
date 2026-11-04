package com.hongyun.asynctask.service;

import com.hongyun.asynctask.exception.TaskNotFoundException;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStateActor;
import com.hongyun.asynctask.model.TaskStateAudit;
import com.hongyun.asynctask.model.TaskStateChangeReason;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskStateAuditService{
    private final TaskMapper taskMapper;
    private final TaskStateAuditMapper taskStateAuditMapper;

    public TaskStateAuditService(TaskMapper taskMapper, TaskStateAuditMapper taskStateAuditMapper){
        this.taskMapper = taskMapper;
        this.taskStateAuditMapper = taskStateAuditMapper;
    }

    public List<TaskStateAudit> getTaskStateAuditByTaskId(
            Long taskId,
            TaskStateActor actor,
            TaskStateChangeReason reason
            ) {
        Task task = taskMapper.selectTaskById(taskId);

        if(task == null) {
            throw new TaskNotFoundException(taskId);
        }

        return taskStateAuditMapper.selectTaskStateAuditsByTaskId(
                taskId,
                actor,
                reason
                );
    }
}