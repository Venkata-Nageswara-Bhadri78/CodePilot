package com.developer.copilot.auth.ratelimit.service;

import com.developer.copilot.auth.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.auth.ratelimit.model.RateLimitResult;

public interface AuthRateLimitService {

    RateLimitResult consume(String bucket, String identity, int limit, long windowSeconds);

    default void consumeOrThrow(String bucket, String identity, int limit, long windowSeconds) {
        RateLimitResult result = consume(bucket, identity, limit, windowSeconds);
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }

    boolean tryAcquireMail(String identity, long cooldownSeconds);

    boolean isLoginBlocked(String email);

    void recordLoginFailure(String email);

    void recordLoginSuccess(String email);
}
