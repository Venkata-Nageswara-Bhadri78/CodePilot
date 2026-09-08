package com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.model.RateLimitResult;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.service.AutomatedJobExtractionRedisService;

class AutomatedJobExtractionRateLimitServiceImplTest {

    private AutomatedJobExtractionRateLimitServiceImpl limiter;

    @BeforeEach
    void setUp() {
        limiter = new AutomatedJobExtractionRateLimitServiceImpl(null);
    }

    @Test
    void consume_inMemory_blocksAfterLimit() {
        RateLimitResult first = limiter.consume("parse-ip", "10.0.0.1", 2, 60);
        RateLimitResult second = limiter.consume("parse-ip", "10.0.0.1", 2, 60);
        RateLimitResult third = limiter.consume("parse-ip", "10.0.0.1", 2, 60);

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertFalse(third.allowed());
        assertTrue(third.retryAfterSeconds() >= 1);
    }

    @Test
    void consumeOrThrow_throwsAfterLimit() {
        limiter.consumeOrThrow("parse-user", "7", 1, 60);
        assertThrows(RateLimitExceededException.class,
                () -> limiter.consumeOrThrow("parse-user", "7", 1, 60));
    }

    @Test
    void consume_zeroLimit_alwaysAllows() {
        assertTrue(limiter.consume("parse-ip", "1", 0, 60).allowed());
    }

    @Test
    void consume_usesRedisWhenAvailable() {
        AutomatedJobExtractionRedisService redisService = mock(AutomatedJobExtractionRedisService.class);
        AutomatedJobExtractionRateLimitServiceImpl redisLimiter =
                new AutomatedJobExtractionRateLimitServiceImpl(redisService);
        when(redisService.increment(eq("rl-parse-ip"), eq("7"), eq(Duration.ofSeconds(60))))
                .thenReturn(1L, 3L);
        when(redisService.ttlSeconds("rl-parse-ip", "7")).thenReturn(42L);

        assertTrue(redisLimiter.consume("parse-ip", "7", 2, 60).allowed());
        RateLimitResult denied = redisLimiter.consume("parse-ip", "7", 2, 60);
        assertFalse(denied.allowed());
        assertEquals(42L, denied.retryAfterSeconds());
    }

    @Test
    void consume_fallsBackToMemoryWhenRedisThrows() {
        AutomatedJobExtractionRedisService redisService = mock(AutomatedJobExtractionRedisService.class);
        AutomatedJobExtractionRateLimitServiceImpl redisLimiter =
                new AutomatedJobExtractionRateLimitServiceImpl(redisService);
        when(redisService.increment(any(), any(), any())).thenThrow(new IllegalStateException("down"));

        assertTrue(redisLimiter.consume("parse-ip", "7", 2, 60).allowed());
        assertTrue(redisLimiter.consume("parse-ip", "7", 2, 60).allowed());
        assertFalse(redisLimiter.consume("parse-ip", "7", 2, 60).allowed());
    }
}
