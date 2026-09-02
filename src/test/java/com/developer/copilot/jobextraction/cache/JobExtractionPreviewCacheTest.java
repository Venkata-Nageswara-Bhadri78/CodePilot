package com.developer.copilot.jobextraction.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.redis.service.JobExtractionRedisService;

@ExtendWith(MockitoExtension.class)
class JobExtractionPreviewCacheTest {

    @Mock
    private JobExtractionRedisService redisService;

    @Test
    void memory_roundTripIsPerUser() {
        JobExtractionPreviewCache cache = new JobExtractionPreviewCache(null);
        JobExtractionResultResponse preview = JobExtractionResultResponse.builder()
                .title("T")
                .company("C")
                .skills(List.of("Java"))
                .build();

        cache.put(1L, "hash-a", preview);

        Optional<JobExtractionResultResponse> hit = cache.get(1L, "hash-a");
        assertTrue(hit.isPresent());
        assertEquals("T", hit.get().getTitle());
        assertTrue(cache.get(99L, "hash-a").isEmpty());
        assertTrue(cache.get(1L, "other").isEmpty());
    }

    @Test
    void redis_getDeserializesPreview() {
        when(redisService.get("preview", "1_hash-a")).thenReturn("{\"title\":\"Cached\",\"company\":\"Acme\"}");
        JobExtractionPreviewCache cache = new JobExtractionPreviewCache(redisService);

        Optional<JobExtractionResultResponse> hit = cache.get(1L, "hash-a");

        assertTrue(hit.isPresent());
        assertEquals("Cached", hit.get().getTitle());
        assertEquals("Acme", hit.get().getCompany());
    }

    @Test
    void redis_putWritesJson() {
        JobExtractionPreviewCache cache = new JobExtractionPreviewCache(redisService);
        JobExtractionResultResponse preview = JobExtractionResultResponse.builder()
                .title("T")
                .company("C")
                .build();

        cache.put(1L, "hash-a", preview);

        verify(redisService).set(eq("preview"), eq("1_hash-a"), any(), eq(Duration.ofMinutes(3)));
        verify(redisService, never()).get(any(), any());
    }

    @Test
    void redis_getFailureFallsBackToMemory() {
        doThrow(new IllegalStateException("down")).when(redisService).set(any(), any(), any(), any());
        when(redisService.get(any(), any())).thenThrow(new IllegalStateException("down"));
        JobExtractionPreviewCache cache = new JobExtractionPreviewCache(redisService);
        cache.put(1L, "hash-a", JobExtractionResultResponse.builder().title("Mem").build());

        Optional<JobExtractionResultResponse> hit = cache.get(1L, "hash-a");

        assertTrue(hit.isPresent());
        assertEquals("Mem", hit.get().getTitle());
    }
}
