package com.developer.copilot.auth.redis.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.auth.redis.key.AuthRedisKeyBuilder;
import com.developer.copilot.auth.redis.repository.AuthRedisRepository;

@ExtendWith(MockitoExtension.class)
class AuthRedisServiceImplTest {

    @Mock
    private AuthRedisRepository repository;

    private AuthRedisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthRedisServiceImpl(repository, new AuthRedisKeyBuilder("auth"));
    }

    @Test
    void increment_usesNamespacedKey() {
        Duration ttl = Duration.ofSeconds(60);
        when(repository.increment("auth:rl-login-email:a@b.com", ttl)).thenReturn(2L);

        assertEquals(2L, service.increment("rl-login-email", "a@b.com", ttl));
    }

    @Test
    void tryAcquire_delegatesToSetIfAbsent() {
        Duration ttl = Duration.ofSeconds(60);
        when(repository.setIfAbsent("auth:mail:a@b.com", ttl)).thenReturn(true);

        assertTrue(service.tryAcquire("mail", "a@b.com", ttl));
    }

    @Test
    void delete_usesNamespacedKey() {
        service.delete("login-fail", "a@b.com");
        verify(repository).delete("auth:login-fail:a@b.com");
    }
}
