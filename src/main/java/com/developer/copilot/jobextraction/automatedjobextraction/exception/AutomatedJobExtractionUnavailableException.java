package com.developer.copilot.jobextraction.automatedjobextraction.exception;

/**
 * Outbound fetch circuit is open or the fetch bulkhead is full. Distinct from an
 * invalid job URL so the client can retry.
 */
public class AutomatedJobExtractionUnavailableException extends RuntimeException {

    public AutomatedJobExtractionUnavailableException(String message) {
        super(message);
    }
}
