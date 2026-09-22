package com.developer.copilot.jobs.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobStatusTest {

    @Test
    void fromJson_acceptsEnumNameAndSpacedLabel() {
        assertEquals(JobStatus.APPLIED, JobStatus.fromJson("APPLIED"));
        assertEquals(JobStatus.RECEIVED_CALL, JobStatus.fromJson("RECEIVED CALL"));
        assertEquals(JobStatus.SCREENING_CALL, JobStatus.fromJson("screening-call"));
        assertEquals(JobStatus.SHORTLISTED, JobStatus.fromJson("SHOTLISTED"));
        assertEquals(JobStatus.CUSTOM, JobStatus.fromJson("custom"));
    }

    @Test
    void fromJson_blank_isNull() {
        assertNull(JobStatus.fromJson(null));
        assertNull(JobStatus.fromJson("  "));
    }

    @Test
    void fromJson_unknown_throws() {
        assertThrows(IllegalArgumentException.class, () -> JobStatus.fromJson("NOT_A_STATUS"));
    }

    @Test
    void toJson_isEnumName() {
        assertEquals("FINAL_ROUND", JobStatus.FINAL_ROUND.toJson());
        assertEquals("OFFER", JobStatus.OFFER.toJson());
    }
}
