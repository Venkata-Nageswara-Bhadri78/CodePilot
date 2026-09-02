package com.developer.copilot.jobs.redis.service;

import java.time.Duration;

/**
 * Jobs Redis operations used by rate-limiting. Keys are built inside the redis module.
 * This is not a cache for job rows or GET /{id}.
 */
public interface JobsRedisService {

    long increment(String namespace, String identity, Duration ttl);

    long ttlSeconds(String namespace, String identity);
}
