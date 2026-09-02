package com.developer.copilot.jobextraction.redis.service.impl;

import java.time.Duration;

import com.developer.copilot.jobextraction.redis.key.JobExtractionRedisKeyBuilder;
import com.developer.copilot.jobextraction.redis.repository.JobExtractionRedisRepository;
import com.developer.copilot.jobextraction.redis.service.JobExtractionRedisService;

public class JobExtractionRedisServiceImpl implements JobExtractionRedisService {

    private final JobExtractionRedisRepository repository;
    private final JobExtractionRedisKeyBuilder keyBuilder;

    public JobExtractionRedisServiceImpl(
            JobExtractionRedisRepository repository,
            JobExtractionRedisKeyBuilder keyBuilder) {
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
