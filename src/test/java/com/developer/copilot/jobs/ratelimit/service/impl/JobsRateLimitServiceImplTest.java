package com.developer.copilot.jobs.ratelimit.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.jobs.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.jobs.ratelimit.model.RateLimitResult;
import com.developer.copilot.jobs.redis.service.JobsRedisService;

@ExtendWith(MockitoExtension.class)
class JobsRateLimitServiceImplTest {

    private JobsRateLimitServiceImpl memoryLimiter;

    @Mock
    private JobsRedisService redisService;

    @BeforeEach
    void setUp() {
        memoryLimiter = new JobsRateLimitServiceImpl(null);
    }

    @Test
    void consume_inMemory_blocksAfterLimit() {
        RateLimitResult first = memoryLimiter.consume("post-user", "7", 2, 60);
        RateLimitResult second = memoryLimiter.consume("post-user", "7", 2, 60);
        RateLimitResult third = memoryLimiter.consume("post-user", "7", 2, 60);

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertFalse(third.allowed());
        assertTrue(third.retryAfterSeconds() >= 1);
    }

    @Test
    void consumeOrThrow_throwsAfterLimit() {
        memoryLimiter.consumeOrThrow("post-user", "7", 1, 60);
        assertThrows(RateLimitExceededException.class,
                () -> memoryLimiter.consumeOrThrow("post-user", "7", 1, 60));
    }

    @Test
    void consume_usesRedisWhenAvailable() {
        JobsRateLimitServiceImpl redisLimiter = new JobsRateLimitServiceImpl(redisService);
        when(redisService.increment(eq("rl-post-user"), eq("7"), eq(Duration.ofSeconds(60))))
                .thenReturn(1L, 3L);
        when(redisService.ttlSeconds("rl-post-user", "7")).thenReturn(42L);

        assertTrue(redisLimiter.consume("post-user", "7", 2, 60).allowed());
        RateLimitResult denied = redisLimiter.consume("post-user", "7", 2, 60);
        assertFalse(denied.allowed());
        assertEquals(42L, denied.retryAfterSeconds());
    }

    @Test
    void consume_fallsBackToMemoryWhenRedisThrows() {
        JobsRateLimitServiceImpl redisLimiter = new JobsRateLimitServiceImpl(redisService);
        when(redisService.increment(any(), any(), any())).thenThrow(new IllegalStateException("down"));

        assertTrue(redisLimiter.consume("post-user", "7", 2, 60).allowed());
        assertTrue(redisLimiter.consume("post-user", "7", 2, 60).allowed());
        assertFalse(redisLimiter.consume("post-user", "7", 2, 60).allowed());
    }

    @Test
    void consume_zeroLimit_alwaysAllows() {
        assertTrue(memoryLimiter.consume("post-user", "7", 0, 60).allowed());
    }
}
