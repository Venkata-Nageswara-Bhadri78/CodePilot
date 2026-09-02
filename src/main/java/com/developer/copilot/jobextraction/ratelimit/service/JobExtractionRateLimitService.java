package com.developer.copilot.jobextraction.ratelimit.service;

import com.developer.copilot.jobextraction.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.jobextraction.ratelimit.model.RateLimitResult;

public interface JobExtractionRateLimitService {

    RateLimitResult consume(String bucket, String identity, int limit, long windowSeconds);

    default void consumeOrThrow(String bucket, String identity, int limit, long windowSeconds) {
        RateLimitResult result = consume(bucket, identity, limit, windowSeconds);
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }
}
