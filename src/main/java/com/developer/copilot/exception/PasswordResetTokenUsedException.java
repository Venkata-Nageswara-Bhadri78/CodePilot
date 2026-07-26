package com.developer.copilot.exception;

public class PasswordResetTokenUsedException extends RuntimeException {

    public PasswordResetTokenUsedException(String message) {
        super(message);
    }
}