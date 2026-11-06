
package com.hongyun.asynctask.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MediaTaskMapper {

    int insertMediaTask(
            @Param("taskId") Long taskId,
            @Param("videoId") String videoId
    );

    String selectVideoIdByTaskId(
            @Param("taskId") Long taskId
    );
}
