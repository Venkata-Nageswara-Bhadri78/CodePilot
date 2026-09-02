package com.developer.copilot.ai.resilience;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.exception.AiUnavailableException;

import reactor.core.publisher.Flux;

/**
 * In-process circuit + bulkhead around paid chat/stream/job-chat calls. Extraction is
 * guarded by the job-extraction module and is not wrapped here.
 * 3 consecutive AI failures open the circuit for 30s; at most 5 concurrent provider calls.
 */
@Component
public class AiChatGuard {

    static final int MAX_CONCURRENT = 5;
    static final int FAILURES_TO_OPEN = 3;
    static final long OPEN_MS = 30_000L;

    private final Semaphore bulkhead = new Semaphore(MAX_CONCURRENT);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openUntilEpochMs;

    public <T> T call(Callable<T> callable) {
        if (System.currentTimeMillis() < openUntilEpochMs) {
            throw unavailable("The AI service is temporarily unavailable. Please try again shortly.");
        }
        if (!bulkhead.tryAcquire()) {
            throw unavailable("The AI service is busy. Please try again shortly.");
        }
        try {
            T result = callable.call();
            consecutiveFailures.set(0);
            return result;
        } catch (AiUnavailableException ex) {
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

    public Flux<AiStreamChunk> guardStream(Supplier<Flux<AiStreamChunk>> supplier) {
        if (System.currentTimeMillis() < openUntilEpochMs) {
            throw unavailable("The AI service is temporarily unavailable. Please try again shortly.");
        }
        if (!bulkhead.tryAcquire()) {
            throw unavailable("The AI service is busy. Please try again shortly.");
        }
        AtomicBoolean finished = new AtomicBoolean(false);
        try {
            return supplier.get()
                    .doOnNext(this::recordStreamChunk)
                    .doFinally(signal -> {
                        if (finished.compareAndSet(false, true)) {
                            bulkhead.release();
                        }
                    });
        } catch (RuntimeException ex) {
            if (finished.compareAndSet(false, true)) {
                bulkhead.release();
            }
            throw ex;
        }
    }

    private void recordStreamChunk(AiStreamChunk chunk) {
        if (chunk == null || !chunk.isCompleted()) {
            return;
        }
        if ("ERROR".equalsIgnoreCase(chunk.getFinishReason())) {
            onFailure();
            return;
        }
        consecutiveFailures.set(0);
    }

    private void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= FAILURES_TO_OPEN) {
            openUntilEpochMs = System.currentTimeMillis() + OPEN_MS;
            consecutiveFailures.set(0);
        }
    }

    private static AiUnavailableException unavailable(String message) {
        return new AiUnavailableException(message);
    }
}
