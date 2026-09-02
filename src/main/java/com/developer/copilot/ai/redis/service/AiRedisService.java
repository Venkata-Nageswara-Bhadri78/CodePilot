package com.developer.copilot.ai.redis.service;

import java.time.Duration;

/**
 * AI Redis operations for rate-limit counters. Keys are built inside the redis module.
 */
public interface AiRedisService {

    long increment(String namespace, String identity, Duration ttl);

    long ttlSeconds(String namespace, String identity);
}
