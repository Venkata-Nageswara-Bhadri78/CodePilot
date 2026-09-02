package com.developer.copilot.user.redis.service;

import java.time.Duration;

/**
 * User Redis operations used by rate-limiting. Keys are built inside the redis module.
 * This is not a cache for profiles or resume files.
 */
public interface UserRedisService {

    long increment(String namespace, String identity, Duration ttl);

    long ttlSeconds(String namespace, String identity);
}
