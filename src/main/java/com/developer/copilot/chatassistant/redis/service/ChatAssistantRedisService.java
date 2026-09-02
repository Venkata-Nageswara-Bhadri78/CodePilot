package com.developer.copilot.chatassistant.redis.service;

import java.time.Duration;

/**
 * Chat-assistant Redis operations for rate-limit counters. Keys are built inside the redis module.
 */
public interface ChatAssistantRedisService {

    long increment(String namespace, String identity, Duration ttl);

    long ttlSeconds(String namespace, String identity);
}
