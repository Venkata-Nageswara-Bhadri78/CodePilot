package com.developer.copilot.auth.exception;

public class PasswordResetTokenExpiredException extends RuntimeException {

    public PasswordResetTokenExpiredException(String message) {
        super(message);
    }
}