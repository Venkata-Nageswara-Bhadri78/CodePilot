package com.developer.copilot.user.ratelimit.service.impl;

import com.developer.copilot.user.ratelimit.model.RateLimitResult;
import com.developer.copilot.user.redis.service.UserRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRateLimitServiceImplTest {

    private UserRateLimitServiceImpl memoryLimiter;

    @Mock
    private UserRedisService redisService;

    @BeforeEach
    void setUp() {
        memoryLimiter = new UserRateLimitServiceImpl();
    }

    @Test
    void consume_blocksAfterLimit() {
        RateLimitResult first = memoryLimiter.consume("upload-user", "7", 2, 60);
        RateLimitResult second = memoryLimiter.consume("upload-user", "7", 2, 60);
        RateLimitResult third = memoryLimiter.consume("upload-user", "7", 2, 60);

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertFalse(third.allowed());
        assertTrue(third.retryAfterSeconds() >= 1);
    }

    @Test
    void consume_isolatesIdentities() {
        assertTrue(memoryLimiter.consume("upload-user", "7", 1, 60).allowed());
        assertTrue(memoryLimiter.consume("upload-user", "8", 1, 60).allowed());
        assertFalse(memoryLimiter.consume("upload-user", "7", 1, 60).allowed());
    }

    @Test
    void consume_zeroLimit_alwaysAllows() {
        assertTrue(memoryLimiter.consume("upload-user", "7", 0, 60).allowed());
    }

    @Test
    void consume_usesRedisWhenAvailable() {
        UserRateLimitServiceImpl redisLimiter = new UserRateLimitServiceImpl(redisService);
        when(redisService.increment(eq("rl-upload-user"), eq("7"), eq(Duration.ofSeconds(60))))
                .thenReturn(1L, 3L);
        when(redisService.ttlSeconds("rl-upload-user", "7")).thenReturn(42L);

        assertTrue(redisLimiter.consume("upload-user", "7", 2, 60).allowed());
        RateLimitResult denied = redisLimiter.consume("upload-user", "7", 2, 60);
        assertFalse(denied.allowed());
        assertEquals(42L, denied.retryAfterSeconds());
    }

    @Test
    void consume_fallsBackToMemoryWhenRedisThrows() {
        UserRateLimitServiceImpl redisLimiter = new UserRateLimitServiceImpl(redisService);
        when(redisService.increment(any(), any(), any())).thenThrow(new IllegalStateException("down"));

        assertTrue(redisLimiter.consume("upload-user", "7", 2, 60).allowed());
        assertTrue(redisLimiter.consume("upload-user", "7", 2, 60).allowed());
        assertFalse(redisLimiter.consume("upload-user", "7", 2, 60).allowed());
    }
}
