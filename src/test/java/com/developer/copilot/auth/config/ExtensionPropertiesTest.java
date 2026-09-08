package com.developer.copilot.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ExtensionPropertiesTest {

    @Test
    void resolvedOrigin_emptyWhenIdBlank() {
        ExtensionProperties properties = new ExtensionProperties();
        properties.setId("  ");

        assertEquals(Optional.empty(), properties.resolvedOrigin());
    }

    @Test
    void resolvedOrigin_buildsChromeExtensionOrigin() {
        ExtensionProperties properties = new ExtensionProperties();
        properties.setId("abcdefghijklmnopqrstuvwxyzabcdef");

        assertEquals(
                Optional.of("chrome-extension://abcdefghijklmnopqrstuvwxyzabcdef"),
                properties.resolvedOrigin());
    }

    @Test
    void resolvedOrigin_rejectsUrlOrWildcard() {
        ExtensionProperties properties = new ExtensionProperties();
        properties.setId("chrome-extension://abc");
        assertThrows(IllegalStateException.class, properties::resolvedOrigin);

        properties.setId("*");
        assertThrows(IllegalStateException.class, properties::resolvedOrigin);

        properties.setId("abc/def");
        assertThrows(IllegalStateException.class, properties::resolvedOrigin);
    }

    @Test
    void defaults_keepExtensionEnabledWithWebTokenLifetime() {
        ExtensionProperties properties = new ExtensionProperties();

        assertTrue(properties.isEnabled());
        assertEquals(900_000L, properties.getAccessExpiryMs());
        assertEquals(10, properties.getTokenRateLimitPerMinute());
    }
}
