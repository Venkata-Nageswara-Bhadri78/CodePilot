package com.developer.copilot.jobextraction.automatedjobextraction.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.redis.service.AutomatedJobExtractionRedisService;

import lombok.extern.slf4j.Slf4j;

/**
 * Short TTL cache of extracted job text keyed by {@code userId + urlHash}. Avoids a
 * second outbound fetch on double-submit. Never shared across users.
 */
@Slf4j
@Component
public class ExtractedJobContentCache {

    static final String NS_EXTRACTED = "extracted";
    static final Duration TTL = Duration.ofMinutes(3);

    private final AutomatedJobExtractionRedisService redisService;
    private final ConcurrentHashMap<String, MemoryEntry> memory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

    public ExtractedJobContentCache(
            @Autowired(required = false) AutomatedJobExtractionRedisService redisService) {
        this.redisService = redisService;
    }

    public Optional<String> get(Long userId, String urlHash) {
        String identity = identity(userId, urlHash);
        if (redisService != null) {
            try {
                String text = redisService.get(NS_EXTRACTED, identity);
                if (text != null && !text.isBlank()) {
                    return Optional.of(text);
                }
                return Optional.empty();
            } catch (Exception ex) {
                log.warn("Automated job-extraction Redis extracted-text get failed; trying memory: {}",
                        ex.getMessage());
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

    public String computeIfAbsent(Long userId, String urlHash, Supplier<String> loader) {
        Optional<String> cached = get(userId, urlHash);
        if (cached.isPresent()) {
            return cached.get();
        }
        String identity = identity(userId, urlHash);
        CompletableFuture<String> created = new CompletableFuture<>();
        CompletableFuture<String> existing = inFlight.putIfAbsent(identity, created);
        if (existing != null) {
            return await(existing);
        }
        try {
            String result = loader.get();
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

    private static String await(CompletableFuture<String> future) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an in-flight automated extraction.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("In-flight automated extraction failed.", cause);
        }
    }

    public void put(Long userId, String urlHash, String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return;
        }
        String identity = identity(userId, urlHash);
        if (redisService != null) {
            try {
                redisService.set(NS_EXTRACTED, identity, extractedText, TTL);
                return;
            } catch (RuntimeException ex) {
                log.warn("Automated job-extraction Redis extracted-text put failed; using memory: {}",
                        ex.getMessage());
            }
        }
        memory.put(identity, new MemoryEntry(extractedText, Instant.now().plus(TTL)));
    }

    private static String identity(Long userId, String urlHash) {
        return String.valueOf(userId) + "_" + (urlHash == null ? "" : urlHash);
    }

    private record MemoryEntry(String value, Instant expiresAt) {
    }
}
