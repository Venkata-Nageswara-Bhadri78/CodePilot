package com.developer.copilot.jobextraction.resilience;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.jobextraction.exception.JobExtractionAiUnavailableException;

/**
 * In-process circuit + bulkhead around the paid model call. No extra library:
 * 3 consecutive AI failures open the circuit for 30s; at most 5 concurrent extracts.
 */
@Component
public class JobExtractionAiGuard {

    static final int MAX_CONCURRENT = 5;
    static final int FAILURES_TO_OPEN = 3;
    static final long OPEN_MS = 30_000L;

    private final Semaphore bulkhead = new Semaphore(MAX_CONCURRENT);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openUntilEpochMs;

    public <T> T call(Callable<T> callable) {
        if (System.currentTimeMillis() < openUntilEpochMs) {
            throw new JobExtractionAiUnavailableException(
                    "The AI service is temporarily unavailable. Please try again shortly.");
        }
        if (!bulkhead.tryAcquire()) {
            throw new JobExtractionAiUnavailableException(
                    "The AI service is busy. Please try again shortly.");
        }
        try {
            T result = callable.call();
            consecutiveFailures.set(0);
            return result;
        } catch (JobExtractionAiUnavailableException ex) {
            throw ex;
        } catch (AiServiceException ex) {
            onFailure();
            throw ex;
        } catch (Exception ex) {
            onFailure();
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AiServiceException(
                    "An unexpected error occurred while communicating with the AI model. Please try again.",
                    ex);
        } finally {
            bulkhead.release();
        }
    }

    private void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= FAILURES_TO_OPEN) {
            openUntilEpochMs = System.currentTimeMillis() + OPEN_MS;
            consecutiveFailures.set(0);
        }
    }
}
