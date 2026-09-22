package com.developer.copilot.jobs.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Manual application pipeline status for a saved job. Independent of AI extraction
 * and scoring. New jobs default to {@link #APPLIED}. {@link #CUSTOM} stores the
 * user-entered label in {@code customStatus}.
 */
public enum JobStatus {

    APPLIED,
    SHORTLISTED,
    RECEIVED_CALL,
    SCREENING_CALL,
    INTERVIEWS,
    FINAL_ROUND,
    SELECTED,
    OFFER,
    ACCEPTED,
    REJECTED,
    WITHDRAWN,
    ON_HOLD,
    CUSTOM;

    @JsonValue
    public String toJson() {
        return name();
    }

    /**
     * Accepts enum names, spaced labels ({@code "RECEIVED CALL"}), hyphens, and the
     * prompt typo {@code SHOTLISTED} → {@link #SHORTLISTED}.
     */
    @JsonCreator
    public static JobStatus fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if ("SHOTLISTED".equals(normalized)) {
            return SHORTLISTED;
        }
        for (JobStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown job status: " + value);
    }
}
