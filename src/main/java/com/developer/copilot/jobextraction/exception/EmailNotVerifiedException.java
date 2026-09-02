package com.developer.copilot.jobextraction.exception;

/**
 * Authenticated caller whose email is not verified. Distinct from a missing JWT (401).
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Please verify your email before using this feature.");
    }
}
