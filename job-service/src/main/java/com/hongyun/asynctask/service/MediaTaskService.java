
package com.hongyun.asynctask.service;

import com.hongyun.asynctask.mapper.MediaTaskMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MediaTaskService {

    private final TaskService taskService;
    private final MediaTaskMapper mediaTaskMapper;

    public MediaTaskService(
            TaskService taskService,
            MediaTaskMapper mediaTaskMapper
    ) {
        this.taskService = taskService;
        this.mediaTaskMapper = mediaTaskMapper;
    }

    @Transactional
    public Task createMediaTask(String name, String videoId) {

        // 1. Validate the Python video ID before writing.
        String normalizedVideoId = UUID.fromString(videoId).toString();

        // 2. Create a PENDING task in MySQL.
        Task task = taskService.createTask(name, TaskType.MEDIA);

        // 3. Persist the taskId <-> videoId association.
        int rows = mediaTaskMapper.insertMediaTask(
                task.getId(),
                normalizedVideoId
        );

        if (rows != 1) {
            throw new IllegalStateException(
                    "Failed to create media task association"
            );
        }

        // 4. Transaction commits when the method returns.
        return task;
    }
}
