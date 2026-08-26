package com.developer.copilot.user.entity;

/**
 * Lifecycle of the parsed representation of a resume.
 */
public enum ResumeParsingStatus {

    /**
     * Record created, extraction has not produced a usable result yet.
     */
    PENDING,

    /**
     * Extraction succeeded and the parsed content is stored.
     */
    COMPLETED,

    /**
     * Terminal. Every allowed attempt failed and no further retry is performed.
     */
    FAILED
}
