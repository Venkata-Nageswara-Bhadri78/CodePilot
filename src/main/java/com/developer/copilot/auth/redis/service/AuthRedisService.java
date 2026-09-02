package com.developer.copilot.auth.redis.service;

import java.time.Duration;

/**
 * Auth Redis operations used by rate-limiting. Keys are built inside the redis module.
 */
public interface AuthRedisService {

    long increment(String namespace, String identity, Duration ttl);

    long ttlSeconds(String namespace, String identity);

    boolean tryAcquire(String namespace, String identity, Duration ttl);

    long getCount(String namespace, String identity);

    void delete(String namespace, String identity);
}
