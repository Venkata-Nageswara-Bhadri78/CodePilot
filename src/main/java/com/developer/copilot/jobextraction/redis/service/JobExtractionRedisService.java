package com.developer.copilot.jobextraction.redis.service;

import java.time.Duration;

/**
 * Job-extraction Redis operations: rate-limit counters and short-lived parse previews.
 * Keys are built inside the redis module. This is not a jobs-table cache.
 */
public interface JobExtractionRedisService {

    long increment(String namespace, String identity, Duration ttl);

    long ttlSeconds(String namespace, String identity);

    String get(String namespace, String identity);

    void set(String namespace, String identity, String value, Duration ttl);
}
