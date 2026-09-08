package com.developer.copilot.jobextraction.automatedjobextraction.redis.service;

import java.time.Duration;

public interface AutomatedJobExtractionRedisService {

    long increment(String namespace, String identity, Duration ttl);

    long ttlSeconds(String namespace, String identity);

    String get(String namespace, String identity);

    void set(String namespace, String identity, String value, Duration ttl);
}
