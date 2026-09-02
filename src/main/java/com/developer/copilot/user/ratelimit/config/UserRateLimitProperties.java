package com.developer.copilot.user.ratelimit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.user")
public class UserRateLimitProperties {

    /** POST /api/v1/users/resumes per identity per minute. */
    private int uploadPerMinute = 8;

    /** DELETE /api/v1/users/resumes/{id} per identity per minute. */
    private int deletePerMinute = 8;

    /** GET /api/v1/internal/resumes/** parsed reads per identity per minute. */
    private int parsePerMinute = 20;
}
