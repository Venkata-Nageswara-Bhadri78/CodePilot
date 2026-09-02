package com.developer.copilot.common.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.common")
public class CommonRateLimitProperties {

    /**
     * All {@code /api/v1/internal/**} calls per calling-service identity (the internal key)
     * per minute. Safety net for any new internal controller.
     */
    private int internalKeyPerMinute = 60;

    /**
     * All {@code /api/v1/internal/**} calls per JWT subject per minute.
     */
    private int internalUserPerMinute = 30;
}
