package com.developer.copilot.auth.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleClientIdsGuardTest {

    @Test
    void requireProductionClientIds_rejectsEmptyAndPlaceholders() {
        assertThrows(IllegalStateException.class, () -> GoogleClientIdsGuard.requireProductionClientIds(List.of()));
        assertThrows(IllegalStateException.class, () -> GoogleClientIdsGuard.requireProductionClientIds(null));
        assertThrows(IllegalStateException.class, () -> GoogleClientIdsGuard.requireProductionClientIds(
                List.of("<your-google-oauth-web-client-id>")));
    }

    @Test
    void requireProductionClientIds_acceptsClientId() {
        GoogleClientIdsGuard.requireProductionClientIds(List.of("123-abc.apps.googleusercontent.com"));
    }
}
