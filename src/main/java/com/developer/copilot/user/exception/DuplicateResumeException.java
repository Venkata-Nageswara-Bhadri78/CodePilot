package com.developer.copilot.user.exception;

public class DuplicateResumeException extends RuntimeException {

    public DuplicateResumeException() {
        super("Duplicate resume detected.");
    }

}