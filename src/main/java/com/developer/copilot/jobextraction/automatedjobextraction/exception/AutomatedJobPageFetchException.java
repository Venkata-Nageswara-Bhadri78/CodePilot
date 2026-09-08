package com.developer.copilot.jobextraction.automatedjobextraction.exception;

/**
 * The job page could not be retrieved due to a technical failure (timeout, network,
 * oversized body). Message is always a generic client-safe string.
 */
public class AutomatedJobPageFetchException extends RuntimeException {

    public static final String MESSAGE = "Unable to access the job posting. Please try again later.";

    public AutomatedJobPageFetchException() {
        super(MESSAGE);
    }

    public AutomatedJobPageFetchException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
