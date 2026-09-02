package com.developer.copilot.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private long otpExpiryMinutes = 10;

    private long resetExpiryMinutes = 15;

    private long refreshExpiryDays = 30;

    private int maxOtpAttempts = 5;

    /** Access JWT lifetime. Default 15 minutes so logout of refresh is enough in practice. */
    private long accessExpiryMs = 900_000L;

    private int maxActiveRefreshTokens = 5;

    private long mailCooldownSeconds = 60;

    private int loginRateLimitPerMinute = 5;

    private int registerRateLimitPerMinute = 5;

    private int verifyRateLimitPerMinute = 10;

    private int resendRateLimitPerMinute = 3;

    private int forgotRateLimitPerMinute = 3;

    private int refreshRateLimitPerMinute = 10;

    private int maxFailedLogins = 10;

    private long failedLoginWindowMinutes = 15;
}
