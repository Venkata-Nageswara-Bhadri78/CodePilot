package com.developer.copilot.user.redis.service.impl;

import com.developer.copilot.user.redis.key.UserRedisKeyBuilder;
import com.developer.copilot.user.redis.repository.UserRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRedisServiceImplTest {

    @Mock
    private UserRedisRepository repository;

    private UserRedisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRedisServiceImpl(repository, new UserRedisKeyBuilder("user"));
    }

    @Test
    void increment_usesNamespacedKey() {
        Duration ttl = Duration.ofSeconds(60);
        when(repository.increment("user:rl-upload:7", ttl)).thenReturn(2L);

        assertEquals(2L, service.increment("rl-upload", "7", ttl));
    }

    @Test
    void ttlSeconds_usesNamespacedKey() {
        when(repository.ttlSeconds("user:rl-upload:7")).thenReturn(42L);

        assertEquals(42L, service.ttlSeconds("rl-upload", "7"));
        verify(repository).ttlSeconds("user:rl-upload:7");
    }
}
