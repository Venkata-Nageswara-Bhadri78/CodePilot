package com.developer.copilot.ai.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiRateLimitProperties {

    /** POST /api/v1/ai/chat and /chat/stream per identity per minute. */
    private int chatPerMinute = 8;

    /** GET /api/v1/ai/resume-context per identity per minute. */
    private int resumeContextPerMinute = 20;
}
