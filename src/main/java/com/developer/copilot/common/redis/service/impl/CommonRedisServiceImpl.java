package com.developer.copilot.common.redis.service.impl;

import java.time.Duration;

import com.developer.copilot.common.redis.key.CommonRedisKeyBuilder;
import com.developer.copilot.common.redis.repository.CommonRedisRepository;
import com.developer.copilot.common.redis.service.CommonRedisService;

public class CommonRedisServiceImpl implements CommonRedisService {

    private final CommonRedisRepository repository;
    private final CommonRedisKeyBuilder keyBuilder;

    public CommonRedisServiceImpl(CommonRedisRepository repository, CommonRedisKeyBuilder keyBuilder) {
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
