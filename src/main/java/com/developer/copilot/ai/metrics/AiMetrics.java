package com.developer.copilot.ai.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Chat/stream counters logged on each outcome so CloudWatch/log drains can alarm without Actuator.
 */
@Slf4j
@Component
public class AiMetrics {

    private final AtomicLong chatSuccess = new AtomicLong();
    private final AtomicLong streamStop = new AtomicLong();
    private final AtomicLong streamError = new AtomicLong();
    private final AtomicLong providerFailures = new AtomicLong();
    private final AtomicLong timeouts = new AtomicLong();
    private final AtomicLong missingResume = new AtomicLong();
    private final AtomicLong parseFailures = new AtomicLong();

    public void recordChatSuccess(Duration latency, Long totalTokens) {
        log.info("ai metric=chatSuccess count={} latencyMs={} totalTokens={} providerFailures={} timeouts={}",
                chatSuccess.incrementAndGet(),
                millis(latency),
                totalTokens == null ? 0 : totalTokens,
                providerFailures.get(),
                timeouts.get());
    }

    public void recordStreamStop() {
        log.info("ai metric=streamStop count={}", streamStop.incrementAndGet());
    }

    public void recordStreamError() {
        log.info("ai metric=streamError count={}", streamError.incrementAndGet());
    }

    public void recordProviderFailure() {
        log.info("ai metric=providerFailure count={}", providerFailures.incrementAndGet());
    }

    public void recordTimeout() {
        log.info("ai metric=timeout count={}", timeouts.incrementAndGet());
    }

    public void recordMissingResume() {
        log.info("ai metric=missingResume count={}", missingResume.incrementAndGet());
    }

    public void recordParseFailure() {
        log.info("ai metric=parseFailure count={}", parseFailures.incrementAndGet());
    }

    private static long millis(Duration latency) {
        return latency == null ? 0 : latency.toMillis();
    }
}
