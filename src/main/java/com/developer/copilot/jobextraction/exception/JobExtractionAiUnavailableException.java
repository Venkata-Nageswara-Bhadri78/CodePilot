package com.developer.copilot.jobextraction.exception;

/**
 * Circuit open or bulkhead full — fail fast instead of waiting on the AI provider timeout.
 */
public class JobExtractionAiUnavailableException extends RuntimeException {

    public JobExtractionAiUnavailableException(String message) {
        super(message);
    }
}
