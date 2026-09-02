package com.developer.copilot.jobextraction.redis.repository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Thin Redis access for job-extraction rate-limit counters and preview JSON. Not a Spring Data repository.
 */
public class JobExtractionRedisRepository {

    private final StringRedisTemplate template;

    public JobExtractionRedisRepository(StringRedisTemplate template) {
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

    public String get(String key) {
        return template.opsForValue().get(key);
    }

    public void set(String key, String value, Duration ttl) {
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            template.opsForValue().set(key, value, ttl);
        } else {
            template.opsForValue().set(key, value);
        }
    }
}
