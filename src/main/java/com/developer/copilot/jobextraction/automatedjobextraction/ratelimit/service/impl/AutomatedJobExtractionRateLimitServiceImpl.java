package com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.service.impl;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.model.RateLimitResult;
import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.service.AutomatedJobExtractionRateLimitService;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.service.AutomatedJobExtractionRedisService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutomatedJobExtractionRateLimitServiceImpl implements AutomatedJobExtractionRateLimitService {

    private static final String NS_RATE_LIMIT = "rl";

    private final AutomatedJobExtractionRedisService redisService;
    private final ConcurrentHashMap<String, Deque<Long>> hitWindows = new ConcurrentHashMap<>();

    public AutomatedJobExtractionRateLimitServiceImpl(AutomatedJobExtractionRedisService redisService) {
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
                log.warn("Automated job-extraction Redis rate-limit failed; using in-memory fallback: {}",
                        ex.getMessage());
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
