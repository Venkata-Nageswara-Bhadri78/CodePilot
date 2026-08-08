package com.developer.copilot.ai.exception;

/**
 * Runtime exception thrown for AI generation, streaming, or upstream LLM errors.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
