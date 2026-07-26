package com.developer.copilot.auth.exception;

public class PasswordResetTokenUsedException extends RuntimeException {

    public PasswordResetTokenUsedException(String message) {
        super(message);
    }
}