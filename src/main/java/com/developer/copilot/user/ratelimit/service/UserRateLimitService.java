package com.developer.copilot.user.ratelimit.service;

import com.developer.copilot.user.ratelimit.model.RateLimitResult;

public interface UserRateLimitService {

    RateLimitResult consume(String bucket, String identity, int limit, long windowSeconds);
}
