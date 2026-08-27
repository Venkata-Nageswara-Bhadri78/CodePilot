package com.developer.copilot.common.security.internal;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.developer.copilot.common.config.InternalApiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fails startup in production profiles when the internal API key boundary is disabled
 * or the shared secret is blank.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiStartupValidator implements ApplicationRunner {

    private final InternalApiProperties internalApiProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!isProductionProfile()) {
            return;
        }

        if (!internalApiProperties.isEnabled()) {
            throw new IllegalStateException(
                    "internal.api.enabled must be true in production profiles.");
        }
        if (!StringUtils.hasText(internalApiProperties.getKey())) {
            throw new IllegalStateException(
                    "internal.api.key must be configured in production profiles.");
        }

        log.info("Internal API key protection verified for production profile.");
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }
}
