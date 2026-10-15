package com.developer.copilot.auth.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Production must name the GIS web client IDs that ID tokens are allowed to present as {@code aud}.
 */
@Component
@Profile({"prod", "production"})
public class GoogleClientIdsGuard {

    private final GoogleProperties googleProperties;

    public GoogleClientIdsGuard(GoogleProperties googleProperties) {
        this.googleProperties = googleProperties;
    }

    @PostConstruct
    void requireClientIds() {
        requireProductionClientIds(googleProperties.resolvedClientIds());
    }

    static void requireProductionClientIds(List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty() || clientIds.stream().anyMatch(GoogleClientIdsGuard::placeholder)) {
            throw new IllegalStateException(
                    "Production must set app.google.client-ids to the Google Identity Services web client id.");
        }
    }

    private static boolean placeholder(String clientId) {
        String value = clientId.toLowerCase(Locale.ROOT);
        return value.contains("your-google")
                || value.contains("changeme")
                || value.contains("placeholder")
                || value.contains("<");
    }
}
