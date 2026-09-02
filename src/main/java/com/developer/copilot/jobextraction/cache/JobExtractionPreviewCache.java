package com.developer.copilot.jobextraction.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
