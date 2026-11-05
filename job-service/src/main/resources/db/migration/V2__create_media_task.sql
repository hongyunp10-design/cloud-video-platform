
-- V2: Link Java tasks to Python video processing jobs.

CREATE TABLE media_task (
                            task_id BIGINT NOT NULL,
                            video_id VARCHAR(36) NOT NULL,
                            created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3),

                            PRIMARY KEY (task_id),

                            CONSTRAINT uk_media_task_video_id
                                UNIQUE (video_id),

                            CONSTRAINT fk_media_task_task
                                FOREIGN KEY (task_id)
                                    REFERENCES task(id)
                                    ON DELETE CASCADE

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
