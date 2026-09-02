package com.developer.copilot.chatassistant.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Counters for the paid send-message door. Logged so CloudWatch/log drains can alarm without Actuator.
 */
@Slf4j
@Component
public class ChatAssistantMetrics {

    private final AtomicLong sendSuccess = new AtomicLong();
    private final AtomicLong jobNotFound = new AtomicLong();
    private final AtomicLong providerFailures = new AtomicLong();
    private final AtomicLong blankResponses = new AtomicLong();
    private final AtomicLong conflicts = new AtomicLong();

    public void recordSendSuccess(Duration aiLatency) {
        log.info("chatassistant metric=sendSuccess count={} aiLatencyMs={}",
                sendSuccess.incrementAndGet(), millis(aiLatency));
    }

    public void recordJobNotFound() {
        log.info("chatassistant metric=jobNotFound count={}", jobNotFound.incrementAndGet());
    }

    public void recordProviderFailure() {
        log.info("chatassistant metric=providerFailure count={}", providerFailures.incrementAndGet());
    }

    public void recordBlankResponse() {
        log.info("chatassistant metric=blankResponse count={}", blankResponses.incrementAndGet());
    }

    public void recordConflict() {
        log.info("chatassistant metric=conflict count={}", conflicts.incrementAndGet());
    }

    private static long millis(Duration latency) {
        return latency == null ? 0 : latency.toMillis();
    }
}
