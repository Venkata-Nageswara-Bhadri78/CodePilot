package com.developer.copilot.auth.exception;

public class InvalidGoogleTokenException extends RuntimeException {

    public static final String MESSAGE = "Invalid Google token.";

    public InvalidGoogleTokenException() {
        super(MESSAGE);
    }
}
