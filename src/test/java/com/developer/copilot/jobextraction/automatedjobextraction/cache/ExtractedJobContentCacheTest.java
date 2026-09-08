package com.developer.copilot.jobextraction.automatedjobextraction.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.developer.copilot.jobextraction.automatedjobextraction.redis.service.AutomatedJobExtractionRedisService;

@ExtendWith(MockitoExtension.class)
class ExtractedJobContentCacheTest {

    @Mock
    private AutomatedJobExtractionRedisService redisService;

    @Test
    void computeIfAbsent_loadsOnceInMemory() {
        ExtractedJobContentCache cache = new ExtractedJobContentCache(null);
        AtomicInteger loads = new AtomicInteger();

        String first = cache.computeIfAbsent(1L, "abc", () -> {
            loads.incrementAndGet();
            return "extracted";
        });
        String second = cache.computeIfAbsent(1L, "abc", () -> {
            loads.incrementAndGet();
            return "other";
        });

        assertEquals("extracted", first);
        assertEquals("extracted", second);
        assertEquals(1, loads.get());
    }

    @Test
    void computeIfAbsent_isPerUser() {
        ExtractedJobContentCache cache = new ExtractedJobContentCache(null);
        cache.computeIfAbsent(1L, "abc", () -> "one");
        String two = cache.computeIfAbsent(2L, "abc", () -> "two");
        assertEquals("two", two);
    }

    @Test
    void get_usesRedisWhenPresent() {
        when(redisService.get("extracted", "1_abc")).thenReturn("from-redis");
        ExtractedJobContentCache cache = new ExtractedJobContentCache(redisService);
        assertEquals("from-redis", cache.get(1L, "abc").orElseThrow());
        verify(redisService, times(1)).get("extracted", "1_abc");
    }

    @Test
    void put_writesRedis() {
        ExtractedJobContentCache cache = new ExtractedJobContentCache(redisService);
        cache.put(1L, "abc", "text");
        verify(redisService).set("extracted", "1_abc", "text", Duration.ofMinutes(3));
    }
}
