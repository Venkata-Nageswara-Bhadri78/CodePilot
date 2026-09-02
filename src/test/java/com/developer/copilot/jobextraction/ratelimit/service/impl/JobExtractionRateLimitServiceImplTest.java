package com.developer.copilot.jobextraction.ratelimit.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.developer.copilot.jobextraction.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.jobextraction.ratelimit.model.RateLimitResult;

class JobExtractionRateLimitServiceImplTest {

    private JobExtractionRateLimitServiceImpl limiter;

    @BeforeEach
    void setUp() {
        limiter = new JobExtractionRateLimitServiceImpl();
    }

    @Test
    void consume_inMemory_blocksAfterLimit() {
        RateLimitResult first = limiter.consume("parse-user", "7", 2, 60);
        RateLimitResult second = limiter.consume("parse-user", "7", 2, 60);
        RateLimitResult third = limiter.consume("parse-user", "7", 2, 60);

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
        assertTrue(limiter.consume("parse-user", "7", 0, 60).allowed());
    }
}
