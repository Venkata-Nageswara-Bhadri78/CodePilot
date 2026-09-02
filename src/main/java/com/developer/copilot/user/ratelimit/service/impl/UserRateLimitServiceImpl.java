package com.developer.copilot.user.ratelimit.service.impl;

import com.developer.copilot.user.ratelimit.model.RateLimitResult;
import com.developer.copilot.user.ratelimit.service.UserRateLimitService;
import com.developer.copilot.user.redis.service.UserRedisService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed counters via Redis when it is enabled and reachable; otherwise an
 * in-memory sliding window so a single instance (and tests) still rate-limit.
 */
@Slf4j
public class UserRateLimitServiceImpl implements UserRateLimitService {

    private static final String NS_RATE_LIMIT = "rl";

    private final UserRedisService redisService;
    private final ConcurrentHashMap<String, Deque<Long>> hitWindows = new ConcurrentHashMap<>();

    public UserRateLimitServiceImpl() {
        this(null);
    }

    public UserRateLimitServiceImpl(UserRedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public RateLimitResult consume(String bucket, String identity, int limit, long windowSeconds) {
        if (limit <= 0) {
            return RateLimitResult.permit();
        }
        String id = identity == null || identity.isBlank() ? "unknown" : identity;
        if (redisService != null) {
            try {
                return consumeRedis(bucket, id, limit, windowSeconds);
            } catch (RuntimeException ex) {
                log.warn("User Redis rate-limit failed; using in-memory fallback: {}", ex.getMessage());
            }
        }
        return consumeMemory(bucket + ":" + id, limit, windowSeconds * 1000L);
    }

    private RateLimitResult consumeRedis(String bucket, String identity, int limit, long windowSeconds) {
        String namespace = NS_RATE_LIMIT + "-" + bucket;
        Duration ttl = Duration.ofSeconds(windowSeconds);
        long count = redisService.increment(namespace, identity, ttl);
        if (count > limit) {
            long retryAfter = redisService.ttlSeconds(namespace, identity);
            return RateLimitResult.deny(retryAfter > 0 ? retryAfter : windowSeconds);
        }
        return RateLimitResult.permit();
    }

    private RateLimitResult consumeMemory(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> window = hitWindows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > windowMs) {
                window.removeFirst();
            }
            if (window.size() >= limit) {
                long oldest = window.peekFirst() == null ? now : window.peekFirst();
                long retryAfter = Math.max(1L, (windowMs - (now - oldest) + 999L) / 1000L);
                return RateLimitResult.deny(retryAfter);
            }
            window.addLast(now);
            return RateLimitResult.permit();
        }
    }
}
