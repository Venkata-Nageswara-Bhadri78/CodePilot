package com.developer.copilot.jobextraction.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jobextraction")
public class JobExtractionRateLimitProperties {

    /** POST /api/v1/job-extraction/parse per identity per minute. Stricter than jobs POST. */
    private int parsePerMinute = 8;
}
