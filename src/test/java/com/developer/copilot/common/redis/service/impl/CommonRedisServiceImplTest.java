package com.developer.copilot.common.redis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.developer.copilot.common.redis.key.CommonRedisKeyBuilder;
import com.developer.copilot.common.redis.repository.CommonRedisRepository;

class CommonRedisServiceImplTest {

    private CommonRedisRepository repository;
    private CommonRedisServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(CommonRedisRepository.class);
        service = new CommonRedisServiceImpl(repository, new CommonRedisKeyBuilder("common"));
    }

    @Test
    void increment_usesNamespacedKey() {
        Duration ttl = Duration.ofSeconds(60);
        when(repository.increment("common:rl-internal-user:7", ttl)).thenReturn(2L);

        assertEquals(2L, service.increment("rl-internal-user", "7", ttl));
    }

    @Test
    void ttlSeconds_usesNamespacedKey() {
        when(repository.ttlSeconds("common:rl-internal-user:7")).thenReturn(42L);

        assertEquals(42L, service.ttlSeconds("rl-internal-user", "7"));
        verify(repository).ttlSeconds("common:rl-internal-user:7");
    }
}
