package com.developer.copilot.chatassistant.exception;

/**
 * Thrown when an operation requires an existing chat session for a job (e.g. deleting it),
 * but no chat has ever been started for that job.
 */
public class ChatSessionNotFoundException extends RuntimeException {

    public ChatSessionNotFoundException(String message) {
        super(message);
    }
}
