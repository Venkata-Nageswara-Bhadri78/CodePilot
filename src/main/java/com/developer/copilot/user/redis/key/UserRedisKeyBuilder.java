package com.developer.copilot.user.redis.key;

import java.util.Locale;

/**
 * Builds namespaced Redis keys for user rate-limit counters. Identity colons are stripped
 * so IPv6 cannot collide with the key delimiter.
 */
public class UserRedisKeyBuilder {

    private final String prefix;

    public UserRedisKeyBuilder(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "user" : prefix.trim();
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
