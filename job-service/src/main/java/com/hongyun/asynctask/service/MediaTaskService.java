
package com.hongyun.asynctask.service;

import com.hongyun.asynctask.mapper.MediaTaskMapper;
import com.hongyun.asynctask.mapper.TaskMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class MediaTaskService {

    private final TaskService taskService;
    private final TaskMapper taskMapper;
    private final MediaTaskMapper mediaTaskMapper;
    private final TransactionTemplate transactionTemplate;

    public MediaTaskService(
            TaskService taskService,
            TaskMapper taskMapper,
            MediaTaskMapper mediaTaskMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.mediaTaskMapper = mediaTaskMapper;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    // Preserve the existing method used by current tests.
    public Task createMediaTask(String name, String videoId) {
        return createMediaTaskWithResult(name, videoId).task();
    }

    /**
     * Returns both the task and whether this request created it.
     */
    public MediaTaskCreationResult createMediaTaskWithResult(
            String name,
            String videoId
    ) {
        // 1. Normalize the video ID.
        String normalizedVideoId =
                UUID.fromString(videoId).toString();

        // 2. Fast path: return an existing task.
        Task existing = findExistingTask(normalizedVideoId);

        if (existing != null) {
            return new MediaTaskCreationResult(existing, false);
        }

        // 3. Create task + media association atomically.
        try {
            Task created = transactionTemplate.execute(status -> {

                Task task = taskService.createTask(
                        name,
                        TaskType.MEDIA
                );

                int rows = mediaTaskMapper.insertMediaTask(
                        task.getId(),
                        normalizedVideoId
                );

                if (rows != 1) {
                    throw new IllegalStateException(
                            "Failed to create media task association"
                    );
                }

                return task;
            });

            return new MediaTaskCreationResult(created, true);

        } catch (DuplicateKeyException ex) {

            // 4. Another request may have committed the
            // same videoId while this request was inserting.
            //
            // TransactionTemplate has already rolled back
            // our unsuccessful task creation.

            Task winner = findExistingTask(normalizedVideoId);

            if (winner != null) {
                return new MediaTaskCreationResult(winner, false);
            }

            // Do not hide unrelated database constraint errors.
            throw ex;
        }
    }

    private Task findExistingTask(String videoId) {

        Long taskId =
                mediaTaskMapper.selectTaskIdByVideoId(videoId);

        if (taskId == null) {
            return null;
        }

        return taskMapper.selectTaskById(taskId);
    }

    public record MediaTaskCreationResult(
            Task task,
            boolean created
    ) {}
}
