package com.hongyun.asynctask.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongyun.asynctask.model.Task;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TaskCacheService {
    private static final String TASK_KEY_PREFIX = "task:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public TaskCacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Task getTask(Long taskId) {
        String key = buildKey(taskId);
        String value = stringRedisTemplate.opsForValue().get(key);

        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readValue(value, Task.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize task from Redis", e);
        }
    }

    public void cacheTask(Task task) {
        String key = buildKey(task.getId());
        try {
            String value = objectMapper.writeValueAsString(task);
            System.out.println("DEBUG CACHE PUT: key=" + key + ", value=" + value);
            stringRedisTemplate.opsForValue().set(key, value, 10, TimeUnit.MINUTES);
            System.out.println("DEBUG CACHE PUT DONE");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize task to Redis", e);
        }
    }

    public void evictTask(Long taskId) {
        stringRedisTemplate.delete(buildKey(taskId));
    }

    private String buildKey(Long taskId) {
        return TASK_KEY_PREFIX + taskId;
    }
}