package com.developer.copilot.user.exception;

public class EducationNotFoundException extends RuntimeException {

    public EducationNotFoundException() {
        super("Education record not found.");
    }

}
