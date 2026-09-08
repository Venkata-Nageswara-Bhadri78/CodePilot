package com.developer.copilot.jobextraction.automatedjobextraction.redis.service.impl;

import java.time.Duration;

import com.developer.copilot.jobextraction.automatedjobextraction.redis.key.AutomatedJobExtractionRedisKeyBuilder;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.repository.AutomatedJobExtractionRedisRepository;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.service.AutomatedJobExtractionRedisService;

public class AutomatedJobExtractionRedisServiceImpl implements AutomatedJobExtractionRedisService {

    private final AutomatedJobExtractionRedisRepository repository;
    private final AutomatedJobExtractionRedisKeyBuilder keyBuilder;

    public AutomatedJobExtractionRedisServiceImpl(
            AutomatedJobExtractionRedisRepository repository,
            AutomatedJobExtractionRedisKeyBuilder keyBuilder) {
        this.repository = repository;
        this.keyBuilder = keyBuilder;
    }

    @Override
    public long increment(String namespace, String identity, Duration ttl) {
        return repository.increment(key(namespace, identity), ttl);
    }

    @Override
    public long ttlSeconds(String namespace, String identity) {
        return repository.ttlSeconds(key(namespace, identity));
    }

    @Override
    public String get(String namespace, String identity) {
        return repository.get(key(namespace, identity));
    }

    @Override
    public void set(String namespace, String identity, String value, Duration ttl) {
        repository.set(key(namespace, identity), value, ttl);
    }

    private String key(String namespace, String identity) {
        return keyBuilder.build(namespace, identity);
    }
}
