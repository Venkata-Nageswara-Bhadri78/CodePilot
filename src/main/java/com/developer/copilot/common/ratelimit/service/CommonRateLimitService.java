package com.developer.copilot.common.ratelimit.service;

import com.developer.copilot.common.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.common.ratelimit.model.RateLimitResult;

public interface CommonRateLimitService {

    RateLimitResult consume(String bucket, String identity, int limit, long windowSeconds);

    default void consumeOrThrow(String bucket, String identity, int limit, long windowSeconds) {
        RateLimitResult result = consume(bucket, identity, limit, windowSeconds);
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }
}
