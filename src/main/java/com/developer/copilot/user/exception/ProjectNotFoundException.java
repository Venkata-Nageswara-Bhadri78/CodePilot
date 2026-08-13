package com.developer.copilot.user.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException() {
        super("Project not found.");
    }

}
