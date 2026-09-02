package com.developer.copilot.jobs.redis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.jobs.redis.key.JobsRedisKeyBuilder;
import com.developer.copilot.jobs.redis.repository.JobsRedisRepository;

@ExtendWith(MockitoExtension.class)
class JobsRedisServiceImplTest {

    @Mock
    private JobsRedisRepository repository;

    private JobsRedisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobsRedisServiceImpl(repository, new JobsRedisKeyBuilder("jobs"));
    }

    @Test
    void increment_usesNamespacedKey() {
        Duration ttl = Duration.ofSeconds(60);
        when(repository.increment("jobs:rl-post:7", ttl)).thenReturn(2L);

        assertEquals(2L, service.increment("rl-post", "7", ttl));
    }

    @Test
    void ttlSeconds_usesNamespacedKey() {
        when(repository.ttlSeconds("jobs:rl-post:7")).thenReturn(42L);

        assertEquals(42L, service.ttlSeconds("rl-post", "7"));
        verify(repository).ttlSeconds("jobs:rl-post:7");
    }
}
