package com.developer.copilot.auth.redis.service.impl;

import java.time.Duration;

import com.developer.copilot.auth.redis.key.AuthRedisKeyBuilder;
import com.developer.copilot.auth.redis.repository.AuthRedisRepository;
import com.developer.copilot.auth.redis.service.AuthRedisService;

public class AuthRedisServiceImpl implements AuthRedisService {

    private final AuthRedisRepository repository;
    private final AuthRedisKeyBuilder keyBuilder;

    public AuthRedisServiceImpl(AuthRedisRepository repository, AuthRedisKeyBuilder keyBuilder) {
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
    public boolean tryAcquire(String namespace, String identity, Duration ttl) {
        return repository.setIfAbsent(key(namespace, identity), ttl);
    }

    @Override
    public long getCount(String namespace, String identity) {
        return repository.getCount(key(namespace, identity));
    }

    @Override
    public void delete(String namespace, String identity) {
        repository.delete(key(namespace, identity));
    }

    private String key(String namespace, String identity) {
        return keyBuilder.build(namespace, identity);
    }
}
