package com.developer.copilot.jobs.redis.key;

import java.util.Locale;

/**
 * Builds namespaced Redis keys for jobs rate-limit counters. Identity colons are stripped
 * so IPv6 cannot collide with the key delimiter.
 */
public class JobsRedisKeyBuilder {

    private final String prefix;

    public JobsRedisKeyBuilder(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "jobs" : prefix.trim();
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
