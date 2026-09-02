package com.developer.copilot.jobextraction.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Parse counters logged on each outcome so CloudWatch/log drains can alarm without Actuator.
 */
@Slf4j
@Component
public class JobExtractionMetrics {

    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong badUrls = new AtomicLong();
    private final AtomicLong aiFailures = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();

    public void recordSuccess(Duration latency) {
        long count = successes.incrementAndGet();
        log.info("jobextraction metric=success count={} latencyMs={} duplicates={} badUrl={} aiFailures={} cacheHits={}",
                count,
                latency == null ? 0 : latency.toMillis(),
                duplicates.get(),
                badUrls.get(),
                aiFailures.get(),
                cacheHits.get());
    }

    public void recordDuplicate() {
        log.info("jobextraction metric=duplicate count={}", duplicates.incrementAndGet());
    }

    public void recordBadUrl() {
        log.info("jobextraction metric=badUrl count={}", badUrls.incrementAndGet());
    }

    public void recordAiFailure() {
        log.info("jobextraction metric=aiFailure count={}", aiFailures.incrementAndGet());
    }

    public void recordCacheHit() {
        log.info("jobextraction metric=cacheHit count={}", cacheHits.incrementAndGet());
    }
}
