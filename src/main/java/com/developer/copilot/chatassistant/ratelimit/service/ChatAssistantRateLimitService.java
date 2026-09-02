package com.developer.copilot.chatassistant.ratelimit.service;

import com.developer.copilot.chatassistant.ratelimit.exception.RateLimitExceededException;
import com.developer.copilot.chatassistant.ratelimit.model.RateLimitResult;

public interface ChatAssistantRateLimitService {

    RateLimitResult consume(String bucket, String identity, int limit, long windowSeconds);

    default void consumeOrThrow(String bucket, String identity, int limit, long windowSeconds) {
        RateLimitResult result = consume(bucket, identity, limit, windowSeconds);
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }
}
