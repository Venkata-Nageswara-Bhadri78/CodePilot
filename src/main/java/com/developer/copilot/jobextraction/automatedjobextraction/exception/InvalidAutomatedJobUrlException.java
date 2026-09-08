package com.developer.copilot.jobextraction.automatedjobextraction.exception;

/**
 * The supplied URL is not a usable job posting (not a job page, empty, expired,
 * blocked, or the target job cannot be identified). Client message is fixed so
 * implementation details never leak.
 */
public class InvalidAutomatedJobUrlException extends RuntimeException {

    public static final String MESSAGE = "INVALID JOB URL";

    public InvalidAutomatedJobUrlException() {
        super(MESSAGE);
    }
}
