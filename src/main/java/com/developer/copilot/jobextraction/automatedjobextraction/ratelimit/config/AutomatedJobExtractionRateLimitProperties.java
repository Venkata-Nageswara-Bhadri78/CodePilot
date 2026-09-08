package com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.automatedjobextraction")
public class AutomatedJobExtractionRateLimitProperties {

    /** POST /api/v1/automated-job-extraction/parse per identity per minute. */
    private int parsePerMinute = 5;
}
