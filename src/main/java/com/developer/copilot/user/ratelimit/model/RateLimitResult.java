package com.developer.copilot.user.ratelimit.model;

public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static RateLimitResult permit() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult deny(long retryAfterSeconds) {
        return new RateLimitResult(false, Math.max(1L, retryAfterSeconds));
    }
}
