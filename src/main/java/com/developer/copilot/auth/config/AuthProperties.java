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
}
