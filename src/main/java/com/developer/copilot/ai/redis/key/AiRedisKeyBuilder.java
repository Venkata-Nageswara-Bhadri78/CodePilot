package com.developer.copilot.ai.redis.key;

import java.util.Locale;

/**
 * Builds namespaced Redis keys for AI rate-limit counters.
 * Identity colons are stripped so IPv6 cannot collide with the key delimiter.
 */
public class AiRedisKeyBuilder {

    private final String prefix;

    public AiRedisKeyBuilder(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "ai" : prefix.trim();
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
