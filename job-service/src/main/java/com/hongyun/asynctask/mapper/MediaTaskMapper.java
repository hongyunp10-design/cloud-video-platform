
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

    /**
     * Find the existing task ID for a staged video.
     * Returns null if no media task exists.
     */
    Long selectTaskIdByVideoId(
            @Param("videoId") String videoId
    );
}
