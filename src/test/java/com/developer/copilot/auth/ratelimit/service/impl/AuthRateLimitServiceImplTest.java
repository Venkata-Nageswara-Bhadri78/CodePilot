package com.developer.copilot.auth.ratelimit.service.impl;

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

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.auth.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.auth.ratelimit.model.RateLimitResult;
import com.developer.copilot.auth.redis.service.AuthRedisService;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitServiceImplTest {

    private AuthProperties properties;
    private AuthRateLimitServiceImpl memoryLimiter;

    @Mock
    private AuthRedisService redisService;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setLoginRateLimitPerMinute(2);
        properties.setMaxFailedLogins(2);
        properties.setFailedLoginWindowMinutes(15);
        memoryLimiter = new AuthRateLimitServiceImpl(properties, null);
    }

    @Test
    void consume_inMemory_blocksAfterLimit() {
        RateLimitResult first = memoryLimiter.consume("login-email", "a@b.com", 2, 60);
        RateLimitResult second = memoryLimiter.consume("login-email", "a@b.com", 2, 60);
        RateLimitResult third = memoryLimiter.consume("login-email", "a@b.com", 2, 60);

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertFalse(third.allowed());
        assertTrue(third.retryAfterSeconds() >= 1);
    }

    @Test
    void consumeOrThrow_throwsAfterLimit() {
        memoryLimiter.consumeOrThrow("login-email", "a@b.com", 1, 60);
        assertThrows(RateLimitExceededException.class,
                () -> memoryLimiter.consumeOrThrow("login-email", "a@b.com", 1, 60));
    }

    @Test
    void tryAcquireMail_inMemory_respectsCooldown() {
        assertTrue(memoryLimiter.tryAcquireMail("a@b.com", 60));
        assertFalse(memoryLimiter.tryAcquireMail("a@b.com", 60));
    }

    @Test
    void loginFailureWindow_blocksThenClearsOnSuccess() {
        memoryLimiter.recordLoginFailure("a@b.com");
        assertFalse(memoryLimiter.isLoginBlocked("a@b.com"));
        memoryLimiter.recordLoginFailure("a@b.com");
        assertTrue(memoryLimiter.isLoginBlocked("a@b.com"));
        memoryLimiter.recordLoginSuccess("a@b.com");
        assertFalse(memoryLimiter.isLoginBlocked("a@b.com"));
    }

    @Test
    void consume_usesRedisWhenAvailable() {
        AuthRateLimitServiceImpl redisLimiter = new AuthRateLimitServiceImpl(properties, redisService);
        when(redisService.increment(eq("rl-login-email"), eq("a@b.com"), eq(Duration.ofSeconds(60))))
                .thenReturn(1L, 3L);
        when(redisService.ttlSeconds("rl-login-email", "a@b.com")).thenReturn(42L);

        assertTrue(redisLimiter.consume("login-email", "a@b.com", 2, 60).allowed());
        RateLimitResult denied = redisLimiter.consume("login-email", "a@b.com", 2, 60);
        assertFalse(denied.allowed());
        assertEquals(42L, denied.retryAfterSeconds());
    }

    @Test
    void consume_fallsBackToMemoryWhenRedisThrows() {
        AuthRateLimitServiceImpl redisLimiter = new AuthRateLimitServiceImpl(properties, redisService);
        when(redisService.increment(any(), any(), any())).thenThrow(new IllegalStateException("down"));

        assertTrue(redisLimiter.consume("login-email", "a@b.com", 2, 60).allowed());
        assertTrue(redisLimiter.consume("login-email", "a@b.com", 2, 60).allowed());
        assertFalse(redisLimiter.consume("login-email", "a@b.com", 2, 60).allowed());
    }
}
