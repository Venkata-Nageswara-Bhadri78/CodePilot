package com.developer.copilot.ai.redis.service.impl;

import java.time.Duration;

import com.developer.copilot.ai.redis.key.AiRedisKeyBuilder;
import com.developer.copilot.ai.redis.repository.AiRedisRepository;
import com.developer.copilot.ai.redis.service.AiRedisService;

public class AiRedisServiceImpl implements AiRedisService {

    private final AiRedisRepository repository;
    private final AiRedisKeyBuilder keyBuilder;

    public AiRedisServiceImpl(AiRedisRepository repository, AiRedisKeyBuilder keyBuilder) {
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
