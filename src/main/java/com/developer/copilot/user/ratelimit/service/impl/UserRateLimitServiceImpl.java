package com.developer.copilot.user.ratelimit.service.impl;

import com.developer.copilot.user.ratelimit.model.RateLimitResult;
import com.developer.copilot.user.ratelimit.service.UserRateLimitService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public class UserRateLimitServiceImpl implements UserRateLimitService {

    private final ConcurrentHashMap<String, Deque<Long>> hitWindows = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult consume(String bucket, String identity, int limit, long windowSeconds) {
        if (limit <= 0) {
            return RateLimitResult.permit();
        }
        String id = identity == null || identity.isBlank() ? "unknown" : identity;
        return consumeMemory(bucket + ":" + id, limit, windowSeconds * 1000L);
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
