package com.developer.copilot.ai.redis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.ai.redis.key.AiRedisKeyBuilder;
import com.developer.copilot.ai.redis.repository.AiRedisRepository;

@ExtendWith(MockitoExtension.class)
class AiRedisServiceImplTest {

    @Mock
    private AiRedisRepository repository;

    private AiRedisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiRedisServiceImpl(repository, new AiRedisKeyBuilder("ai"));
    }

    @Test
    void increment_usesNamespacedKey() {
        Duration ttl = Duration.ofSeconds(60);
        when(repository.increment("ai:rl-chat-user:7", ttl)).thenReturn(2L);

        assertEquals(2L, service.increment("rl-chat-user", "7", ttl));
    }

    @Test
    void ttlSeconds_usesNamespacedKey() {
        when(repository.ttlSeconds("ai:rl-chat-user:7")).thenReturn(42L);

        assertEquals(42L, service.ttlSeconds("rl-chat-user", "7"));
        verify(repository).ttlSeconds("ai:rl-chat-user:7");
    }
}
