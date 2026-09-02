package com.developer.copilot.jobs.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jobs.redis")
public class JobsRedisProperties {

    /**
     * When false, jobs rate limits use in-memory counters so local and tests do not need Redis.
     * Turn on for multi-instance (ECS) jobs rate limits. Not a job-list or GET-by-id cache.
     */
    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password;

    private int database = 0;

    private long timeoutMs = 2000L;

    private String keyPrefix = "jobs";
}
