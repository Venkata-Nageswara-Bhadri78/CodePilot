package com.developer.copilot.ai.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.redis")
public class AiRedisProperties {

    /**
     * When false, AI rate limits use in-memory stores so local and tests do not need Redis.
     * Turn on for multi-instance (ECS).
     */
    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password;

    private int database = 0;

    private long timeoutMs = 2000L;

    private String keyPrefix = "ai";
}
