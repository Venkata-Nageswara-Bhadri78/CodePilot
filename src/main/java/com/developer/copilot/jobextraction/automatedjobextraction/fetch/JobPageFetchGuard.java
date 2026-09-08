package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobExtractionUnavailableException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobPageFetchException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;
import com.developer.copilot.jobextraction.automatedjobextraction.util.AutomatedJobExtractionLimits;

/**
 * In-process circuit + bulkhead around outbound job-page fetches.
 */
@Component
public class JobPageFetchGuard {

    private final Semaphore bulkhead = new Semaphore(AutomatedJobExtractionLimits.FETCH_BULKHEAD);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openUntilEpochMs;

    public <T> T call(Callable<T> callable) {
        if (System.currentTimeMillis() < openUntilEpochMs) {
            throw new AutomatedJobExtractionUnavailableException(
                    "The job page could not be retrieved. Please try again shortly.");
        }
        if (!bulkhead.tryAcquire()) {
            throw new AutomatedJobExtractionUnavailableException(
                    "The job page could not be retrieved. Please try again shortly.");
        }
        try {
            T result = callable.call();
            consecutiveFailures.set(0);
            return result;
        } catch (InvalidAutomatedJobUrlException | AutomatedJobExtractionUnavailableException ex) {
            throw ex;
        } catch (AutomatedJobPageFetchException ex) {
            onFailure();
            throw ex;
        } catch (Exception ex) {
            onFailure();
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AutomatedJobPageFetchException(ex);
        } finally {
            bulkhead.release();
        }
    }

    private void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= AutomatedJobExtractionLimits.FETCH_FAILURES_TO_OPEN) {
            openUntilEpochMs = System.currentTimeMillis() + AutomatedJobExtractionLimits.FETCH_CIRCUIT_OPEN_MS;
            consecutiveFailures.set(0);
        }
    }
}
