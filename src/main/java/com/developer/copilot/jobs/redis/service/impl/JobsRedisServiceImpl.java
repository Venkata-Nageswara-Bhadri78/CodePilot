package com.developer.copilot.jobs.redis.service.impl;

import java.time.Duration;

import com.developer.copilot.jobs.redis.key.JobsRedisKeyBuilder;
import com.developer.copilot.jobs.redis.repository.JobsRedisRepository;
import com.developer.copilot.jobs.redis.service.JobsRedisService;

public class JobsRedisServiceImpl implements JobsRedisService {

    private final JobsRedisRepository repository;
    private final JobsRedisKeyBuilder keyBuilder;

    public JobsRedisServiceImpl(JobsRedisRepository repository, JobsRedisKeyBuilder keyBuilder) {
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

    private String key(String namespace, String identity) {
        return keyBuilder.build(namespace, identity);
    }
}
