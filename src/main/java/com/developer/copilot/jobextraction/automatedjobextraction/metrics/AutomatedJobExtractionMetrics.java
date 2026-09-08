package com.developer.copilot.jobextraction.automatedjobextraction.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AutomatedJobExtractionMetrics {

    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong invalidUrls = new AtomicLong();
    private final AtomicLong fetchFailures = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();

    public void recordSuccess(Duration latency) {
        long count = successes.incrementAndGet();
        log.info("automatedjobextraction metric=success count={} latencyMs={} invalidUrl={} fetchFailures={} cacheHits={}",
                count,
                latency == null ? 0 : latency.toMillis(),
                invalidUrls.get(),
                fetchFailures.get(),
                cacheHits.get());
    }

    public void recordInvalidUrl() {
        log.info("automatedjobextraction metric=invalidUrl count={}", invalidUrls.incrementAndGet());
    }

    public void recordFetchFailure() {
        log.info("automatedjobextraction metric=fetchFailure count={}", fetchFailures.incrementAndGet());
    }

    public void recordCacheHit() {
        log.info("automatedjobextraction metric=cacheHit count={}", cacheHits.incrementAndGet());
    }
}
