package com.developer.copilot.jobextraction.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.redis.service.JobExtractionRedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Short TTL preview cache keyed by {@code userId + urlHash}. Redis when enabled;
 * otherwise in-memory so a double-click on one instance is free. Never shared across users.
 */
@Slf4j
@Component
public class JobExtractionPreviewCache {

    static final String NS_PREVIEW = "preview";
    static final Duration TTL = Duration.ofMinutes(3);

    private final JobExtractionRedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, MemoryEntry> memory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<JobExtractionResultResponse>> inFlight =
            new ConcurrentHashMap<>();

    public JobExtractionPreviewCache(@Autowired(required = false) JobExtractionRedisService redisService) {
        this.redisService = redisService;
    }

    public Optional<JobExtractionResultResponse> get(Long userId, String urlHash) {
        String identity = identity(userId, urlHash);
        if (redisService != null) {
            try {
                String json = redisService.get(NS_PREVIEW, identity);
                if (json != null && !json.isBlank()) {
                    return Optional.of(objectMapper.readValue(json, JobExtractionResultResponse.class));
                }
                return Optional.empty();
            } catch (Exception ex) {
                log.warn("Job-extraction Redis preview get failed; trying memory: {}", ex.getMessage());
            }
        }
        MemoryEntry entry = memory.get(identity);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            memory.remove(identity);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    /**
     * Returns a cached preview, or runs {@code loader} once per {@code userId+urlHash}
     * so two tabs cannot start two model calls for the same parse.
     */
    public JobExtractionResultResponse computeIfAbsent(
            Long userId,
            String urlHash,
            Supplier<JobExtractionResultResponse> loader) {
        Optional<JobExtractionResultResponse> cached = get(userId, urlHash);
        if (cached.isPresent()) {
            return cached.get();
        }
        String identity = identity(userId, urlHash);
        CompletableFuture<JobExtractionResultResponse> created = new CompletableFuture<>();
        CompletableFuture<JobExtractionResultResponse> existing = inFlight.putIfAbsent(identity, created);
        if (existing != null) {
            return await(existing);
        }
        try {
            JobExtractionResultResponse result = loader.get();
            put(userId, urlHash, result);
            created.complete(result);
            return result;
        } catch (RuntimeException ex) {
            created.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(identity, created);
        }
    }

    private static JobExtractionResultResponse await(
            CompletableFuture<JobExtractionResultResponse> future) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an in-flight job extraction.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("In-flight job extraction failed.", cause);
        }
    }

    public void put(Long userId, String urlHash, JobExtractionResultResponse preview) {
        if (preview == null) {
            return;
        }
        String identity = identity(userId, urlHash);
        if (redisService != null) {
            try {
                redisService.set(NS_PREVIEW, identity, objectMapper.writeValueAsString(preview), TTL);
                return;
            } catch (JsonProcessingException | RuntimeException ex) {
                log.warn("Job-extraction Redis preview put failed; using memory: {}", ex.getMessage());
            }
        }
        memory.put(identity, new MemoryEntry(preview, Instant.now().plus(TTL)));
    }

    private static String identity(Long userId, String urlHash) {
        return String.valueOf(userId) + "_" + (urlHash == null ? "" : urlHash);
    }

    private record MemoryEntry(JobExtractionResultResponse value, Instant expiresAt) {
    }
}
