package com.developer.copilot.common.security.internal;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.developer.copilot.common.config.InternalApiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fails startup in production profiles when the internal API key boundary is disabled,
 * the shared secret is blank, too short to be a meaningful secret, or is still set to a
 * well-known placeholder value.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiStartupValidator implements ApplicationRunner {

    /**
     * Minimum acceptable length for {@code internal.api.key} in production. A short key
     * (e.g. "a" or "123") would pass a non-blank check but is trivially guessable, defeating
     * the purpose of the internal API boundary.
     */
    static final int MIN_KEY_LENGTH = 32;

    /**
     * Common placeholder tokens that must never make it into a real deployment. Matched as
     * substrings (case-insensitive, after stripping non-alphanumeric characters) so both a
     * short literal placeholder and a long-but-still-a-placeholder value (e.g.
     * "please-change-me-before-deploying-to-prod") are caught.
     */
    private static final Set<String> DISALLOWED_PLACEHOLDER_TOKENS = Set.of(
            "changeme", "placeholder", "yoursecretkey", "yourinternalapikey",
            "internalapikey", "example", "generatealongrandomkey");

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

        String key = internalApiProperties.getKey();
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException(
                    "internal.api.key must be configured in production profiles.");
        }
        if (key.trim().length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "internal.api.key must be at least " + MIN_KEY_LENGTH
                            + " characters long in production profiles.");
        }
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        boolean isPlaceholder = DISALLOWED_PLACEHOLDER_TOKENS.stream().anyMatch(normalizedKey::contains);
        if (isPlaceholder) {
            throw new IllegalStateException(
                    "internal.api.key must not be a placeholder value in production profiles.");
        }

        log.info("Internal API key protection verified for production profile.");
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }
}
