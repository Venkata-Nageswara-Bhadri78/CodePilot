package com.developer.copilot.jobs.exception;

public class DuplicateJobDraftException extends RuntimeException {

    public static final String MESSAGE = "A draft for this posting already exists.";

    public DuplicateJobDraftException() {
        super(MESSAGE);
    }
}
