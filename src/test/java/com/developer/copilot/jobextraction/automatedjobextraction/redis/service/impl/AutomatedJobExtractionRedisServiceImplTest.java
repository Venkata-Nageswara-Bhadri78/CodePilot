package com.developer.copilot.jobextraction.automatedjobextraction.redis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.jobextraction.automatedjobextraction.redis.key.AutomatedJobExtractionRedisKeyBuilder;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.repository.AutomatedJobExtractionRedisRepository;

@ExtendWith(MockitoExtension.class)
class AutomatedJobExtractionRedisServiceImplTest {

    @Mock
    private AutomatedJobExtractionRedisRepository repository;

    private AutomatedJobExtractionRedisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutomatedJobExtractionRedisServiceImpl(
                repository, new AutomatedJobExtractionRedisKeyBuilder("automatedjobextraction"));
    }

    @Test
    void increment_usesNamespacedKey() {
        Duration ttl = Duration.ofSeconds(60);
        when(repository.increment("automatedjobextraction:rl-parse:7", ttl)).thenReturn(2L);

        assertEquals(2L, service.increment("rl-parse", "7", ttl));
    }

    @Test
    void ttlSeconds_usesNamespacedKey() {
        when(repository.ttlSeconds("automatedjobextraction:rl-parse:7")).thenReturn(42L);

        assertEquals(42L, service.ttlSeconds("rl-parse", "7"));
        verify(repository).ttlSeconds("automatedjobextraction:rl-parse:7");
    }

    @Test
    void getAndSet_useNamespacedKey() {
        Duration ttl = Duration.ofMinutes(3);
        when(repository.get("automatedjobextraction:extracted:1_abc")).thenReturn("Job Title: T");

        service.set("extracted", "1_abc", "Job Title: T", ttl);
        assertEquals("Job Title: T", service.get("extracted", "1_abc"));
        verify(repository).set("automatedjobextraction:extracted:1_abc", "Job Title: T", ttl);
    }
}
