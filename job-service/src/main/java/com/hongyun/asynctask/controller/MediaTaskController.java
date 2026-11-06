
package com.hongyun.asynctask.controller;

import com.hongyun.asynctask.model.Task;
import com.hongyun.asynctask.service.MediaTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media-tasks")
public class MediaTaskController {

    private final MediaTaskService mediaTaskService;

    public MediaTaskController(MediaTaskService mediaTaskService) {
        this.mediaTaskService = mediaTaskService;
    }

    @PostMapping
    public ResponseEntity<Task> createMediaTask(
            @Valid @RequestBody CreateMediaTaskRequest request
    ) {
        Task task = mediaTaskService.createMediaTask(
                request.name(),
                request.videoId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(task);
    }

    public record CreateMediaTaskRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank String videoId
    ) {}
}
