package com.developer.copilot.chatassistant.redis.service.impl;

import java.time.Duration;

import com.developer.copilot.chatassistant.redis.key.ChatAssistantRedisKeyBuilder;
import com.developer.copilot.chatassistant.redis.repository.ChatAssistantRedisRepository;
import com.developer.copilot.chatassistant.redis.service.ChatAssistantRedisService;

public class ChatAssistantRedisServiceImpl implements ChatAssistantRedisService {

    private final ChatAssistantRedisRepository repository;
    private final ChatAssistantRedisKeyBuilder keyBuilder;

    public ChatAssistantRedisServiceImpl(
            ChatAssistantRedisRepository repository,
            ChatAssistantRedisKeyBuilder keyBuilder) {
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
