package com.developer.copilot.jobs.exception;

/**
 * The job could not be saved and the fallback draft could not be stored either.
 * The message is safe to return to a client.
 */
public class JobDraftPersistenceException extends RuntimeException {

    public static final String MESSAGE =
            "The job could not be saved and the draft could not be stored.";

    public JobDraftPersistenceException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
