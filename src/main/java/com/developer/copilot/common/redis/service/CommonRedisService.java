package com.developer.copilot.common.redis.service;

import java.time.Duration;

/**
 * Common Redis operations for internal API rate-limit counters. Keys are built inside
 * the redis module.
 */
public interface CommonRedisService {

    long increment(String namespace, String identity, Duration ttl);

    long ttlSeconds(String namespace, String identity);
}
