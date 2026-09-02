package com.developer.copilot.jobextraction.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jobextraction.redis")
public class JobExtractionRedisProperties {

    /**
     * When false, parse rate limits and preview cache use in-memory stores so local
     * and tests do not need Redis. Turn on for multi-instance (ECS).
     */
    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password;

    private int database = 0;

    private long timeoutMs = 2000L;

    private String keyPrefix = "jobextraction";
}
