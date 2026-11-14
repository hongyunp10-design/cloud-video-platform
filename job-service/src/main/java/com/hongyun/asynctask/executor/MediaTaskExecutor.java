
package com.hongyun.asynctask.executor;

import com.hongyun.asynctask.client.PythonVideoClient;
import com.hongyun.asynctask.mapper.MediaTaskMapper;
import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.model.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MediaTaskExecutor implements TaskExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(MediaTaskExecutor.class);

    private static final int MAX_POLLS = 60;
    private static final long POLL_INTERVAL_MS = 2000;

    private final MediaTaskMapper mediaTaskMapper;
    private final PythonVideoClient videoClient;

    public MediaTaskExecutor(
            MediaTaskMapper mediaTaskMapper,
            PythonVideoClient videoClient
    ) {
        this.mediaTaskMapper = mediaTaskMapper;
        this.videoClient = videoClient;
    }

    @Override
    public TaskType supportedType() {
        return TaskType.MEDIA;
    }

    @Override
    public void execute(Task task) {

        Long taskId = task.getId();

        int executionAttempt = task.getRetryCount();

        // 1. Find the Python video ID in MySQL.
        String videoId =
                mediaTaskMapper.selectVideoIdByTaskId(taskId);

        if (videoId == null || videoId.isBlank()) {
            throw new IllegalStateException(
                    "Missing videoId for taskId=" + taskId
            );
        }

        log.info(
                "event=media_execution_started taskId={} videoId={} attempt={}",
                taskId,
                videoId,
                executionAttempt
        );

        // 2. Submit the staged video to Python/Ray.
        videoClient.submitProcessing(videoId);

        log.info(
                "event=media_process_request_accepted taskId={} videoId={} attempt={}",
                taskId,
                videoId,
                executionAttempt
        );

        // 3. Wait until Python reports a terminal status.
        for (int pollNumber = 1; pollNumber <= MAX_POLLS; pollNumber++) {
            String status = videoClient.getStatus(videoId);

            if ("done".equals(status)) {
                log.info(
                        "event=media_processing_done taskId={} videoId={} attempt={} polls={}",
                        taskId,
                        videoId,
                        executionAttempt,
                        pollNumber
                );
                return;
            }

            if ("failed".equals(status)) {
                throw new IllegalStateException(
                        "Video processing failed: videoId=" + videoId
                );
            }

            if (!"processing".equals(status)) {
                throw new IllegalStateException(
                        "Unexpected video status: " + status
                );
            }

            if (pollNumber < MAX_POLLS) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Media task interrupted: taskId=" + taskId,
                            ex
                    );
                }
            }
        }

        // 4. Never report SUCCESS after a timeout.
        throw new IllegalStateException(
                "Video processing timed out: videoId=" + videoId
        );
    }
}
