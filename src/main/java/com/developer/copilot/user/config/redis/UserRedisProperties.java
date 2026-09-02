package com.developer.copilot.user.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.user.redis")
public class UserRedisProperties {

    /**
     * When false, upload/parse rate limits use in-memory counters so local and tests
     * do not need Redis. Turn on for multi-instance (ECS). Not a profile or resume cache.
     */
    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password;

    private int database = 0;

    private long timeoutMs = 2000L;

    private String keyPrefix = "user";
}
