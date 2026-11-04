package com.hongyun.asynctask;

import com.hongyun.asynctask.config.TaskRetryProperties;
import com.hongyun.asynctask.config.TaskTimeoutSchedulerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties({
        TaskRetryProperties.class,
        TaskTimeoutSchedulerProperties.class
        })
public class AsyncTaskServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(AsyncTaskServiceApplication.class, args);
    }
}