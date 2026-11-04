package com.hongyun.asynctask.mapper;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskMapper {
    void insertTask(Task task);

    Task selectTaskById(@Param("id") Long id);

    List<Task> selectAllTasks();

    int updateTask(Task task);

    int deleteTaskById(@Param("id") Long id);

    List<Task> selectFailedTasksForRetry();

    List<Task> selectPendingTasks(@Param("limit") Integer limit);

    int updateTaskStatusIfCurrentStatus(
            @Param("id") Long id,
            @Param("fromStatus") TaskStatus fromStatus,
            @Param("toStatus") TaskStatus toStatus
            );

    int retryTaskIfCurrentStatusAndRetryCount(
            @Param("id") Long id,
            @Param("fromStatus") TaskStatus taskStatus,
            @Param("oldRetryCount") Integer oldRetryCount,
            @Param("toStatus") TaskStatus toStatus,
            @Param("newRetryCount") Integer newRetryCount
    );

    int updateTaskStatusAndLastErrorIfCurrentStatus(
            @Param("id") Long id,
            @Param("fromStatus") TaskStatus fromStatus,
            @Param("toStatus") TaskStatus toStatus,
            @Param("lastErrorMessage") String lastErrorMessage
    );

    int updateTaskStatusAndClearLastErrorIfCurrentStatus(
            @Param("id") Long id,
            @Param("fromStatus") TaskStatus fromStatus,
            @Param("toStatus") TaskStatus toStatus
    );

    long countTasksByStatus(
            @Param("status") TaskStatus status
    );

    int claimTaskIfCurrentStatus(
            @Param("id") Long id,
            @Param("fromStatus") TaskStatus fromStatus,
            @Param("toStatus") TaskStatus toStatus
    );

    List<Task> selectTimedOutRunningTasks(
            @Param("timeoutSeconds") long timeoutSeconds
    );

    int updateTaskStartedAt(
            @Param("id") Long id,
            @Param("startedAt")LocalDateTime startedAt
    );

    int updateTaskStartedAtByOffsetSeconds(
            @Param("id") Long id,
            @Param("offsetSeconds") long offsetSeconds
    );
}