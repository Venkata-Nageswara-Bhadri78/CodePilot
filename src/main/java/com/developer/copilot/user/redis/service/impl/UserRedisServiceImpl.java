package com.developer.copilot.user.redis.service.impl;

import java.time.Duration;

import com.developer.copilot.user.redis.key.UserRedisKeyBuilder;
import com.developer.copilot.user.redis.repository.UserRedisRepository;
import com.developer.copilot.user.redis.service.UserRedisService;

public class UserRedisServiceImpl implements UserRedisService {

    private final UserRedisRepository repository;
    private final UserRedisKeyBuilder keyBuilder;

    public UserRedisServiceImpl(UserRedisRepository repository, UserRedisKeyBuilder keyBuilder) {
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
