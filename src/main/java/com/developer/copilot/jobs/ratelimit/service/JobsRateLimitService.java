package com.developer.copilot.jobs.ratelimit.service;

import com.developer.copilot.jobs.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.jobs.ratelimit.model.RateLimitResult;

public interface JobsRateLimitService {

    RateLimitResult consume(String bucket, String identity, int limit, long windowSeconds);

    default void consumeOrThrow(String bucket, String identity, int limit, long windowSeconds) {
        RateLimitResult result = consume(bucket, identity, limit, windowSeconds);
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }
}
