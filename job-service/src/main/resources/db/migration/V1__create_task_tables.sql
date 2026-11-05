
-- V1: Initialize the existing Java task management schema.
-- This migration runs against an empty database.

CREATE TABLE task (
                      id BIGINT NOT NULL AUTO_INCREMENT,
                      name VARCHAR(255) NOT NULL,
                      type VARCHAR(32) NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      retry_count INT NOT NULL DEFAULT 0,
                      max_retry_count INT NOT NULL DEFAULT 3,
                      last_error_message VARCHAR(1024) DEFAULT NULL,
                      created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
                      started_at DATETIME(3) DEFAULT NULL,
                      completed_at DATETIME(3) DEFAULT NULL,
                      PRIMARY KEY (id),
                      INDEX idx_task_status_id (status, id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE task_state_audit (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  task_id BIGINT NOT NULL,
                                  from_status VARCHAR(32) NOT NULL,
                                  to_status VARCHAR(32) NOT NULL,
                                  actor VARCHAR(64) NOT NULL,
                                  reason VARCHAR(128) NOT NULL,
                                  retry_count INT NOT NULL,
                                  max_retry_count INT NOT NULL,
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                  PRIMARY KEY (id),
                                  INDEX idx_task_audit_task_id (task_id, id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
