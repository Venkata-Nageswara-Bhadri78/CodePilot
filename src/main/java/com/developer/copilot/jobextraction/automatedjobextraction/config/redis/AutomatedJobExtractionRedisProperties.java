package com.developer.copilot.jobextraction.automatedjobextraction.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.automatedjobextraction.redis")
public class AutomatedJobExtractionRedisProperties {

    /**
     * When false, parse rate limits and extracted-content cache use in-memory stores.
     */
    private boolean enabled = false;

    private String host = "localhost";

    private int port = 6379;

    private String password;

    private int database = 0;

    private long timeoutMs = 2000L;

    private String keyPrefix = "automatedjobextraction";
}
