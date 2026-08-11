package com.developer.copilot.jobs.exception;

/**
 * Thrown when a user attempts to add a job posting (identified by its canonical source URL)
 * that already exists in their own job records. A different user may still add the same
 * job posting independently.
 */
public class DuplicateJobException extends RuntimeException {

    public DuplicateJobException(String message) {
        super(message);
    }
}
