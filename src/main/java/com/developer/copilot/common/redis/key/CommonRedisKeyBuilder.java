package com.developer.copilot.common.redis.key;

import java.util.Locale;

/**
 * Builds namespaced Redis keys for common (internal API) rate-limit counters.
 * Identity colons are stripped so IPv6 cannot collide with the key delimiter.
 */
public class CommonRedisKeyBuilder {

    private final String prefix;

    public CommonRedisKeyBuilder(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "common" : prefix.trim();
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
