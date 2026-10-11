package com.developer.copilot.jobs.entity;

/**
 * Why an automatic save landed in {@code jobs_draft} instead of {@code jobs}.
 */
public enum JobDraftFailureCategory {
    VALIDATION,
    PERSISTENCE,
    EXTERNAL,
    UNEXPECTED
}
