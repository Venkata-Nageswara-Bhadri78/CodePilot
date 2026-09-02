package com.developer.copilot.user.ratelimit.service.impl;

import com.developer.copilot.user.ratelimit.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRateLimitServiceImplTest {

    private UserRateLimitServiceImpl limiter;

    @BeforeEach
    void setUp() {
        limiter = new UserRateLimitServiceImpl();
    }

    @Test
    void consume_blocksAfterLimit() {
        RateLimitResult first = limiter.consume("upload-user", "7", 2, 60);
        RateLimitResult second = limiter.consume("upload-user", "7", 2, 60);
        RateLimitResult third = limiter.consume("upload-user", "7", 2, 60);

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertFalse(third.allowed());
        assertTrue(third.retryAfterSeconds() >= 1);
    }

    @Test
    void consume_isolatesIdentities() {
        assertTrue(limiter.consume("upload-user", "7", 1, 60).allowed());
        assertTrue(limiter.consume("upload-user", "8", 1, 60).allowed());
        assertFalse(limiter.consume("upload-user", "7", 1, 60).allowed());
    }

    @Test
    void consume_zeroLimit_alwaysAllows() {
        assertTrue(limiter.consume("upload-user", "7", 0, 60).allowed());
    }
}
