package com.developer.copilot.user.exception;

public class WorkExperienceNotFoundException extends RuntimeException {

    public WorkExperienceNotFoundException() {
        super("Work experience not found.");
    }

}
