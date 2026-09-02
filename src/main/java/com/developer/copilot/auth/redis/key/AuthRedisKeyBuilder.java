package com.developer.copilot.auth.redis.key;

import java.util.Locale;

/**
 * Builds namespaced Redis keys for auth counters. Identity colons are stripped so IPv6
 * and emails cannot collide with the key delimiter.
 */
public class AuthRedisKeyBuilder {

    private final String prefix;

    public AuthRedisKeyBuilder(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "auth" : prefix.trim();
    }

    public String build(String namespace, String identity) {
        return prefix + ":" + sanitize(namespace) + ":" + sanitize(identity);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(':', '_');
    }
}
