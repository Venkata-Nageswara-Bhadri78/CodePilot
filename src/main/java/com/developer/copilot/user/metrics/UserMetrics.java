package com.developer.copilot.user.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Upload/parse counters logged on each outcome so CloudWatch/log drains can alarm without Actuator.
 */
@Slf4j
@Component
public class UserMetrics {

    private final AtomicLong uploadSuccess = new AtomicLong();
    private final AtomicLong uploadInvalid = new AtomicLong();
    private final AtomicLong uploadDuplicate = new AtomicLong();
    private final AtomicLong uploadCap = new AtomicLong();
    private final AtomicLong parseCompleted = new AtomicLong();
    private final AtomicLong parseFailed = new AtomicLong();
    private final AtomicLong minioDeleteFailures = new AtomicLong();

    public void recordUploadSuccess() {
        log.info("user metric=uploadSuccess count={}", uploadSuccess.incrementAndGet());
    }

    public void recordUploadInvalid() {
        log.info("user metric=uploadInvalid count={}", uploadInvalid.incrementAndGet());
    }

    public void recordUploadDuplicate() {
        log.info("user metric=uploadDuplicate count={}", uploadDuplicate.incrementAndGet());
    }

    public void recordUploadCap() {
        log.info("user metric=uploadCap count={}", uploadCap.incrementAndGet());
    }

    public void recordParseCompleted() {
        log.info("user metric=parseCompleted count={}", parseCompleted.incrementAndGet());
    }

    public void recordParseFailed() {
        log.info("user metric=parseFailed count={}", parseFailed.incrementAndGet());
    }

    public void recordOnDemandParse(Duration latency) {
        log.info("user metric=onDemandParse latencyMs={} parseCompleted={} parseFailed={}",
                latency == null ? 0 : latency.toMillis(),
                parseCompleted.get(),
                parseFailed.get());
    }

    public void recordMinioDeleteFailure() {
        log.info("user metric=minioDeleteFailure count={}", minioDeleteFailures.incrementAndGet());
    }
}
