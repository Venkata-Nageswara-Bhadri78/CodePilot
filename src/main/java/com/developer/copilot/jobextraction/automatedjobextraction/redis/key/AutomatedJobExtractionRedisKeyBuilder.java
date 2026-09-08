package com.developer.copilot.jobextraction.automatedjobextraction.redis.key;

import java.util.Locale;

/**
 * Builds namespaced Redis keys for automated job-extraction rate-limit counters and
 * extracted-content cache. Identity colons are stripped so IPv6 cannot collide with
 * the key delimiter.
 */
public class AutomatedJobExtractionRedisKeyBuilder {

    private final String prefix;

    public AutomatedJobExtractionRedisKeyBuilder(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "automatedjobextraction" : prefix.trim();
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
