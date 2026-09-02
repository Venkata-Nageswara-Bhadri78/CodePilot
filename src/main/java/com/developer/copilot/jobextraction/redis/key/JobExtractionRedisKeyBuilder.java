package com.developer.copilot.jobextraction.redis.key;

import java.util.Locale;

/**
 * Builds namespaced Redis keys for job-extraction rate-limit counters and preview cache.
 * Identity colons are stripped so IPv6 cannot collide with the key delimiter.
 */
public class JobExtractionRedisKeyBuilder {

    private final String prefix;

    public JobExtractionRedisKeyBuilder(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "jobextraction" : prefix.trim();
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
