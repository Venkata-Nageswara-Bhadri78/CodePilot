package com.developer.copilot.auth.ratelimit.service.impl;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.auth.ratelimit.model.RateLimitResult;
import com.developer.copilot.auth.ratelimit.service.AuthRateLimitService;
import com.developer.copilot.auth.redis.service.AuthRedisService;

import lombok.extern.slf4j.Slf4j;

/**
 * Distributed counters via Redis when it is enabled and reachable; otherwise an
 * in-memory sliding window so a single instance (and tests) still rate-limit.
 */
@Slf4j
public class AuthRateLimitServiceImpl implements AuthRateLimitService {

    private static final String NS_RATE_LIMIT = "rl";
    private static final String NS_MAIL = "mail";
    private static final String NS_LOGIN_FAIL = "login-fail";

    private final AuthProperties authProperties;
    private final AuthRedisService redisService;

    private final ConcurrentHashMap<String, Deque<Long>> hitWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastMailEpochMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> loginFailures = new ConcurrentHashMap<>();

    public AuthRateLimitServiceImpl(AuthProperties authProperties, AuthRedisService redisService) {
        this.authProperties = authProperties;
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
                log.warn("Auth Redis rate-limit failed; using in-memory fallback: {}", ex.getMessage());
            }
        }
        return consumeMemory(bucket + ":" + id, limit, windowSeconds * 1000L);
    }

    @Override
    public boolean tryAcquireMail(String identity, long cooldownSeconds) {
        if (identity == null || cooldownSeconds <= 0) {
            return true;
        }
        if (redisService != null) {
            try {
                return redisService.tryAcquire(NS_MAIL, identity, Duration.ofSeconds(cooldownSeconds));
            } catch (RuntimeException ex) {
                log.warn("Auth Redis mail cooldown failed; using in-memory fallback: {}", ex.getMessage());
            }
        }
        return tryAcquireMailMemory(identity, cooldownSeconds * 1000L);
    }

    @Override
    public boolean isLoginBlocked(String email) {
        int maxFailures = authProperties.getMaxFailedLogins();
        if (email == null || maxFailures <= 0) {
            return false;
        }
        long windowMs = failedLoginWindowMs();
        if (redisService != null) {
            try {
                return redisService.getCount(NS_LOGIN_FAIL, email) >= maxFailures;
            } catch (RuntimeException ex) {
                log.warn("Auth Redis login-block check failed; using in-memory fallback: {}", ex.getMessage());
            }
        }
        pruneLoginFailures(email, windowMs);
        Deque<Long> failures = loginFailures.get(email);
        return failures != null && failures.size() >= maxFailures;
    }

    @Override
    public void recordLoginFailure(String email) {
        if (email == null) {
            return;
        }
        long windowMs = failedLoginWindowMs();
        if (redisService != null) {
            try {
                redisService.increment(NS_LOGIN_FAIL, email, Duration.ofMillis(windowMs));
                return;
            } catch (RuntimeException ex) {
                log.warn("Auth Redis login-failure record failed; using in-memory fallback: {}", ex.getMessage());
            }
        }
        long now = System.currentTimeMillis();
        loginFailures.compute(email, (key, deque) -> {
            Deque<Long> next = deque == null ? new ArrayDeque<>() : deque;
            next.addLast(now);
            dropExpired(next, now, windowMs);
            return next;
        });
    }

    @Override
    public void recordLoginSuccess(String email) {
        if (email == null) {
            return;
        }
        if (redisService != null) {
            try {
                redisService.delete(NS_LOGIN_FAIL, email);
            } catch (RuntimeException ex) {
                log.warn("Auth Redis login-success clear failed; using in-memory fallback: {}", ex.getMessage());
            }
        }
        loginFailures.remove(email);
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
            dropExpired(window, now, windowMs);
            if (window.size() >= limit) {
                long oldest = window.peekFirst() == null ? now : window.peekFirst();
                long retryAfter = Math.max(1L, (windowMs - (now - oldest) + 999L) / 1000L);
                return RateLimitResult.deny(retryAfter);
            }
            window.addLast(now);
            return RateLimitResult.permit();
        }
    }

    private boolean tryAcquireMailMemory(String identity, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long previous = lastMailEpochMs.get(identity);
        if (previous != null && now - previous < cooldownMs) {
            return false;
        }
        lastMailEpochMs.put(identity, now);
        return true;
    }

    private void pruneLoginFailures(String email, long windowMs) {
        Deque<Long> failures = loginFailures.get(email);
        if (failures == null) {
            return;
        }
        dropExpired(failures, System.currentTimeMillis(), windowMs);
        if (failures.isEmpty()) {
            loginFailures.remove(email);
        }
    }

    private long failedLoginWindowMs() {
        return authProperties.getFailedLoginWindowMinutes() * 60_000L;
    }

    private static void dropExpired(Deque<Long> timestamps, long now, long windowMs) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
            timestamps.removeFirst();
        }
    }
}
