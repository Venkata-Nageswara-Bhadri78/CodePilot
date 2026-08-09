package com.developer.copilot.common.exception;

/**
 * Thrown when a user-supplied job posting URL is not a valid, absolute http/https URL.
 */
public class InvalidJobUrlException extends RuntimeException {

    public InvalidJobUrlException(String message) {
        super(message);
    }
}
