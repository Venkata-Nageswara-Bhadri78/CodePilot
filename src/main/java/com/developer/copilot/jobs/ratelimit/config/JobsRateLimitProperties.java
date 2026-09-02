package com.developer.copilot.jobs.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jobs")
public class JobsRateLimitProperties {

    /** POST /api/v1/jobs per identity per minute. */
    private int postPerMinute = 15;

    /** PUT / PATCH / DELETE per identity per minute. */
    private int mutatePerMinute = 30;

    /** GET /api/v1/jobs with no query string. */
    private int listPerMinute = 60;

    /** GET /api/v1/jobs with a query string (search/paging). Stricter than a bare list. */
    private int searchPerMinute = 20;

    /** GET /api/v1/jobs/{id}. */
    private int readPerMinute = 60;
}
