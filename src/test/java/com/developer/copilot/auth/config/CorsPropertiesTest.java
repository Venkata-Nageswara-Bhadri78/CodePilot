package com.developer.copilot.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void resolvedAllowedOrigins_dropsWildcard() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("https://app.example.com", "*", "http://localhost:3000"));

        List<String> resolved = properties.resolvedAllowedOrigins();

        assertEquals(List.of("https://app.example.com", "http://localhost:3000"), resolved);
        assertFalse(resolved.contains("*"));
    }

    @Test
    void resolvedAllowedOrigins_emptyWhenOnlyWildcard() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("*"));

        assertEquals(List.of(), properties.resolvedAllowedOrigins());
    }
}
