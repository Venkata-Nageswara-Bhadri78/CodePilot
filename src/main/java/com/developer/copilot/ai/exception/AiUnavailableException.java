package com.developer.copilot.ai.exception;

/**
 * Circuit open or bulkhead full — fail fast instead of waiting on the AI provider timeout.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}
