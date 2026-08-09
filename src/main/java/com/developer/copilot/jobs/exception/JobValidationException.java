package com.developer.copilot.jobs.exception;

/**
 * Thrown when a job mutation would leave a mandatory field (title, company, sourceUrl,
 * originalDescription) blank. Bean validation covers this for full create/replace payloads;
 * this exception covers the partial-update (PATCH) path where fields are optional-by-absence
 * but must never be optional-by-blank-value once set.
 */
public class JobValidationException extends RuntimeException {

    public JobValidationException(String message) {
        super(message);
    }
}
