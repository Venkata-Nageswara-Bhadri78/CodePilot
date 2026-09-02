package com.developer.copilot.auth.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AuthSecretsGuardTest {

    @Test
    void requireProductionSecret_rejectsMissingEnv() {
        assertThrows(IllegalStateException.class, () -> AuthSecretsGuard.requireProductionSecret(null));
        assertThrows(IllegalStateException.class, () -> AuthSecretsGuard.requireProductionSecret("  "));
    }

    @Test
    void requireProductionSecret_acceptsEnvValue() {
        AuthSecretsGuard.requireProductionSecret("test-secret-key-that-is-long-enough-for-hmac-sha256");
    }
}
