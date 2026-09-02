package com.developer.copilot.jobs.ratelimit.model;

public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static RateLimitResult permit() {
        return new RateLimitResult(true, 0L);
    }

    public static RateLimitResult deny(long retryAfterSeconds) {
        return new RateLimitResult(false, Math.max(1L, retryAfterSeconds));
    }
}
