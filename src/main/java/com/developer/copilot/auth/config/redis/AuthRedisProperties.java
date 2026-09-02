package com.developer.copilot.auth.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth.redis")
public class AuthRedisProperties {

    /**
     * When false, auth uses in-memory counters so local and tests do not need Redis.
     * Turn on for multi-instance (ECS) rate limits, mail cooldown, and login backoff.
     */
    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password;

    private int database = 0;

    private long timeoutMs = 2000L;

    private String keyPrefix = "auth";
}
