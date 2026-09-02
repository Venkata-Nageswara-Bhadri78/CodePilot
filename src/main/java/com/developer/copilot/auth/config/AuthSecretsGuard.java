package com.developer.copilot.auth.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Production must take the HMAC secret from the environment, not a committed properties file.
 */
@Component
@Profile({"prod", "production"})
public class AuthSecretsGuard {

    private final Environment environment;

    public AuthSecretsGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void requireEnvSecret() {
        requireProductionSecret(firstNonBlank(
                environment.getProperty("APP_JWT_SECRET"),
                System.getenv("APP_JWT_SECRET")));
    }

    static void requireProductionSecret(String envSecret) {
        if (envSecret == null || envSecret.isBlank()) {
            throw new IllegalStateException(
                    "Production must set APP_JWT_SECRET; do not boot from a committed JWT secret.");
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
