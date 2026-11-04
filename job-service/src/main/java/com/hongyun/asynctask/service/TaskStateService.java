package com.hongyun.asynctask.service;

import com.hongyun.asynctask.exception.ConcurrentTaskStateChangeException;
import com.hongyun.asynctask.exception.InvalidTaskStateTransitionException;
import com.hongyun.asynctask.exception.TaskNotFoundException;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.mapper.TaskStateAuditMapper;
import com.hongyun.asynctask.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class TaskStateService {
    private static final Logger log = LoggerFactory.getLogger(TaskStateService.class);
    private static final int MAX_LAST_ERROR_MESSAGE_LENGTH = 1024;

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    TaskStatus.PENDING, Set.of(TaskStatus.RUNNING),
                    TaskStatus.RUNNING, Set.of(TaskStatus.SUCCESS, TaskStatus.FAILED),
                    TaskStatus.SUCCESS, Set.of(),
                    TaskStatus.FAILED, Set.of(TaskStatus.PENDING, TaskStatus.DEAD),
                    TaskStatus.DEAD, Set.of()
            );
    private final TaskMapper taskMapper;
    private final TaskStateAuditMapper taskStateAuditMapper;

    public TaskStateService(TaskMapper taskMapper, TaskStateAuditMapper taskStateAuditMapper) {
        this.taskMapper = taskMapper;
        this.taskStateAuditMapper = taskStateAuditMapper;
    }

    @Transactional
    @CacheEvict(value = "taskCache", key = "#taskId")
    public Task transitionTaskStatus(
            Long taskId,
            TaskStatus targetStatus,
            TaskStateChangeContext context
    ) {
        Task task = taskMapper.selectTaskById(taskId);

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        TaskStatus fromStatus = task.getStatus();

        validateStatusTransition(fromStatus, targetStatus);

        int updateRows;

        if (targetStatus == TaskStatus.SUCCESS) {
            updateRows =  taskMapper.updateTaskStatusAndClearLastErrorIfCurrentStatus(
                    taskId,
                    fromStatus,
                    targetStatus
            );
        }else {
            updateRows = taskMapper.updateTaskStatusIfCurrentStatus(
                    taskId,
                    fromStatus,
                    targetStatus
            );
        }

        if (updateRows == 0) {
           throw  new ConcurrentTaskStateChangeException(
                   taskId,
                   fromStatus,
                   targetStatus
           );
        }


        task.setStatus(targetStatus);

        if (targetStatus == TaskStatus.SUCCESS) {
            task.setLastErrorMessage(null);
        }

        insertTaskStateAudit(
                taskId,
                fromStatus,
                targetStatus,
                context,
                task.getRetryCount(),
                task.getMaxRetryCount()
        );

        log.info(
                "Task state transition completed: taskId={}, fromStatus={}, toStatus={}, retryCount={}, maxRetryCount={}, actor={}, reason={}",
                taskId,
                fromStatus,
                targetStatus,
                task.getRetryCount(),
                task.getMaxRetryCount(),
                context.getActor(),
                context.getReason()
        );
        return task;
    }

    @Transactional
    @CacheEvict(value = "taskCache", key = "#taskId")
    public Task retryTask(
            Long taskId,
            TaskStateChangeContext context
    ) {
        Task task = taskMapper.selectTaskById(taskId);

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        TaskStatus fromStatus = task.getStatus();
        int oldRetryCount = task.getRetryCount();
        int newRetryCount = oldRetryCount + 1;

        validateStatusTransition(fromStatus, TaskStatus.PENDING);

        int updateRows = taskMapper.retryTaskIfCurrentStatusAndRetryCount(
          taskId,
          fromStatus,
          oldRetryCount,
          TaskStatus.PENDING,
          newRetryCount
        );

        if (updateRows == 0) {
           throw new ConcurrentTaskStateChangeException(
                   taskId,
                   fromStatus,
                   TaskStatus.PENDING
           );
        }

        task.setStatus(TaskStatus.PENDING);
        task.setRetryCount(newRetryCount);
        task.setLastErrorMessage(null);

        insertTaskStateAudit(
                taskId,
                fromStatus,
                TaskStatus.PENDING,
                context,
                task.getRetryCount(),
                task.getMaxRetryCount()
        );

        log.info(
                "Task retry transition completed: taskId={}, fromStatus={}, toStatus={}, oldRetryCount={}, newRetryCount={}, maxRetryCount={}, actor={}, reason={}",
                taskId,
                fromStatus,
                TaskStatus.PENDING,
                oldRetryCount,
                newRetryCount,
                task.getMaxRetryCount(),
                context.getActor(),
                context.getReason()
        );
        return task;
    }

    @Transactional
    @CacheEvict(value = "taskCache", key = "#taskId")
    public Task transitionTaskStatusWithError(
            Long taskId,
            TaskStatus targetStatus,
            TaskStateChangeContext context,
            String lastErrorMessage
    ) {
        if (targetStatus != TaskStatus.FAILED) {
            throw new IllegalArgumentException("transitionTaskStatusWithError only supports targetStatus=FAILED");
        }

        Task task = taskMapper.selectTaskById(taskId);

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        TaskStatus fromStatus = task.getStatus();

        validateStatusTransition(fromStatus, targetStatus);

        String normalizedLastErrorMessage = normalizeLastErrorMessage(lastErrorMessage);

        int updateRows = taskMapper.updateTaskStatusAndLastErrorIfCurrentStatus(
                taskId,
                fromStatus,
                targetStatus,
                normalizedLastErrorMessage
        );

        if (updateRows == 0) {
            throw new ConcurrentTaskStateChangeException(
                    taskId,
                    fromStatus,
                    targetStatus
            );
        }

        task.setStatus(targetStatus);
        task.setLastErrorMessage(normalizedLastErrorMessage);

        insertTaskStateAudit(
                taskId,
                fromStatus,
                targetStatus,
                context,
                task.getRetryCount(),
                task.getMaxRetryCount()
        );
        log.info(
                "Task state transition with error completed: taskId={}, fromStatus={}, toStatus={}, retryCount={}, maxRetryCount={}, actor={}, reason={}, lastErrorMessage={}",
                taskId,
                fromStatus,
                targetStatus,
                task.getRetryCount(),
                task.getMaxRetryCount(),
                context.getActor(),
                context.getReason(),
                normalizedLastErrorMessage
        );

        return  task;
    }

    @Transactional
    @CacheEvict(value = "taskCache", key = "#taskId")
    public Task claimTask(Long taskId) {
        Task task = taskMapper.selectTaskById(taskId);

        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }

        TaskStatus fromStatus = task.getStatus();
        TaskStatus targetStatus = TaskStatus.RUNNING;

        validateStatusTransition(fromStatus, targetStatus);

        TaskStateChangeContext context = TaskStateChangeContext.of(
                TaskStateActor.WORKER,
                TaskStateChangeReason.WORKER_PICKED_PENDING_TASK
        );

        int updatedRows = taskMapper.claimTaskIfCurrentStatus(
                taskId,
                fromStatus,
                targetStatus
        );

        if(updatedRows == 0) {
            throw new ConcurrentTaskStateChangeException(taskId, fromStatus, targetStatus);
        }

        insertTaskStateAudit(taskId, fromStatus, targetStatus, context, task.getRetryCount(), task.getMaxRetryCount());

        Task claimedTask = taskMapper.selectTaskById(taskId);

        log.info(
                "Task claimed successfully: taskId={}, fromStatus={}, toStatus={}, retryCount={}, maxRetryCount={}, startedAt={}, actor={}, reason={}",
                claimedTask.getId(),
                fromStatus,
                targetStatus,
                claimedTask.getRetryCount(),
                claimedTask.getMaxRetryCount(),
                claimedTask.getStartedAt(),
                context.getActor(),
                context.getReason()
        );

        return claimedTask;
    }


    private void validateStatusTransition(TaskStatus currentStatus, TaskStatus targetStatus) {
        Set<TaskStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());

        if (!allowedTargets.contains(targetStatus)) {
            throw new InvalidTaskStateTransitionException(currentStatus, targetStatus);
        }
    }

    private void insertTaskStateAudit(
            Long taskId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            TaskStateChangeContext context,
            Integer retryCount,
            Integer maxRetryCount
    ) {
        TaskStateAudit taskStateAudit = new TaskStateAudit(
                null,
                taskId,
                fromStatus,
                toStatus,
                context.getActor(),
                context.getReason(),
                retryCount,
                maxRetryCount,
                null
        );
        taskStateAuditMapper.insertTaskStateAudit(taskStateAudit);
    }

    private String normalizeLastErrorMessage(String lastErrorMessage) {
        if(lastErrorMessage == null || lastErrorMessage.trim().isEmpty()){
            return "Task execution failed without error message";
        }

        String trimmedMessage =  lastErrorMessage.trim();

        if (trimmedMessage.length() <= MAX_LAST_ERROR_MESSAGE_LENGTH) {
            return trimmedMessage;
        }

        return trimmedMessage.substring(0, MAX_LAST_ERROR_MESSAGE_LENGTH);
    }
}