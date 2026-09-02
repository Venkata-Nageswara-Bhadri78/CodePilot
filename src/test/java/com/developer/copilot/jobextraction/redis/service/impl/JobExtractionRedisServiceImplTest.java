package com.developer.copilot.jobextraction.redis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.jobextraction.redis.key.JobExtractionRedisKeyBuilder;
import com.developer.copilot.jobextraction.redis.repository.JobExtractionRedisRepository;

@ExtendWith(MockitoExtension.class)
class JobExtractionRedisServiceImplTest {

    @Mock
    private JobExtractionRedisRepository repository;

    private JobExtractionRedisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobExtractionRedisServiceImpl(repository, new JobExtractionRedisKeyBuilder("jobextraction"));
    }

    @Test
    void increment_usesNamespacedKey() {
        Duration ttl = Duration.ofSeconds(60);
        when(repository.increment("jobextraction:rl-parse:7", ttl)).thenReturn(2L);

        assertEquals(2L, service.increment("rl-parse", "7", ttl));
    }

    @Test
    void ttlSeconds_usesNamespacedKey() {
        when(repository.ttlSeconds("jobextraction:rl-parse:7")).thenReturn(42L);

        assertEquals(42L, service.ttlSeconds("rl-parse", "7"));
        verify(repository).ttlSeconds("jobextraction:rl-parse:7");
    }

    @Test
    void getAndSet_useNamespacedKey() {
        Duration ttl = Duration.ofMinutes(3);
        when(repository.get("jobextraction:preview:1_abc")).thenReturn("{\"title\":\"T\"}");

        service.set("preview", "1_abc", "{\"title\":\"T\"}", ttl);
        assertEquals("{\"title\":\"T\"}", service.get("preview", "1_abc"));
        verify(repository).set("jobextraction:preview:1_abc", "{\"title\":\"T\"}", ttl);
    }
}
