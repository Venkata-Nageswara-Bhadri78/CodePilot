package com.developer.copilot.ai.exception;

/**
 * Raised when resume parsing has not finished yet, so AI grounding cannot proceed.
 */
public class AiResumePendingException extends RuntimeException {

    public AiResumePendingException(String message) {
        super(message);
    }
}
