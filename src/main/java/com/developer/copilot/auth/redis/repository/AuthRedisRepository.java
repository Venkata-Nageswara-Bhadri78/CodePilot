package com.developer.copilot.auth.redis.repository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Thin Redis access for auth counters. Not a Spring Data repository.
 */
public class AuthRedisRepository {

    private final StringRedisTemplate template;

    public AuthRedisRepository(StringRedisTemplate template) {
        this.template = template;
    }

    public long increment(String key, Duration ttl) {
        Long value = template.opsForValue().increment(key);
        if (value != null && value == 1L && ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            template.expire(key, ttl);
        }
        return value == null ? 0L : value;
    }

    public boolean setIfAbsent(String key, Duration ttl) {
        Boolean acquired = template.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public long getCount(String key) {
        String raw = template.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public long ttlSeconds(String key) {
        Long seconds = template.getExpire(key, TimeUnit.SECONDS);
        if (seconds == null || seconds < 0L) {
            return 0L;
        }
        return seconds;
    }

    public void delete(String key) {
        template.delete(key);
    }
}
