package com.developer.copilot.user.exception;

/**
 * Raised when the content of a resume PDF cannot be turned into usable text.
 */
public class ResumeParsingException extends RuntimeException {

    public ResumeParsingException(String message) {
        super(message);
    }

    public ResumeParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
