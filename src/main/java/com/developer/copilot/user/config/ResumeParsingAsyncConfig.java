package com.developer.copilot.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated executor for resume parsing so PDF extraction never competes with
 * request handling threads.
 */
@Configuration
@EnableAsync
public class ResumeParsingAsyncConfig {

    public static final String RESUME_PARSING_EXECUTOR = "resumeParsingExecutor";

    @Bean(name = RESUME_PARSING_EXECUTOR)
    public Executor resumeParsingExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("resume-parse-");

        // Saturating the queue must not steal Tomcat threads. Extra work stays PENDING.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }
}
