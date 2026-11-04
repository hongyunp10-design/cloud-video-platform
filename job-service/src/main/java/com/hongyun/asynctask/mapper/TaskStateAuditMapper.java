package com.hongyun.asynctask.mapper;

import com.hongyun.asynctask.model.TaskStateActor;
import com.hongyun.asynctask.model.TaskStateAudit;
import com.hongyun.asynctask.model.TaskStateChangeReason;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskStateAuditMapper {
    void insertTaskStateAudit(TaskStateAudit taskStateAudit);

    List<TaskStateAudit> selectTaskStateAuditsByTaskId(
            @Param("taskId") Long taskId,
            @Param("actor") TaskStateActor actor,
            @Param("reason") TaskStateChangeReason reason
    );

    void deleteTaskStateAuditsByTaskId(
            @Param("taskId") Long taskId
    );
}