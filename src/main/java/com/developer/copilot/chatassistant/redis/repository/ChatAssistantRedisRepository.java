package com.developer.copilot.chatassistant.redis.repository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Thin Redis access for chat-assistant rate-limit counters. Not a Spring Data repository.
 */
public class ChatAssistantRedisRepository {

    private final StringRedisTemplate template;

    public ChatAssistantRedisRepository(StringRedisTemplate template) {
        this.template = template;
    }

    public long increment(String key, Duration ttl) {
        Long value = template.opsForValue().increment(key);
        if (value != null && value == 1L && ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            template.expire(key, ttl);
        }
        return value == null ? 0L : value;
    }

    public long ttlSeconds(String key) {
        Long seconds = template.getExpire(key, TimeUnit.SECONDS);
        if (seconds == null || seconds < 0L) {
            return 0L;
        }
        return seconds;
    }
}
