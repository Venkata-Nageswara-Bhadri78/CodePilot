package com.developer.copilot.user.exception;

public class ResumeNotFoundException extends RuntimeException {

    public ResumeNotFoundException() {
        super("Resume not found.");
    }

}