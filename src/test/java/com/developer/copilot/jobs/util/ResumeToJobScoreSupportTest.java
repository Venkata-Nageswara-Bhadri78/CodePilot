package com.developer.copilot.jobs.util;

import com.developer.copilot.jobs.exception.JobValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeToJobScoreSupportTest {

    @Test
    void isValid_acceptsBounds() {
        assertTrue(ResumeToJobScoreSupport.isValid(0));
        assertTrue(ResumeToJobScoreSupport.isValid(100));
        assertTrue(ResumeToJobScoreSupport.isValid(57));
        assertFalse(ResumeToJobScoreSupport.isValid(null));
        assertFalse(ResumeToJobScoreSupport.isValid(-1));
        assertFalse(ResumeToJobScoreSupport.isValid(101));
    }

    @Test
    void sanitize_fallsBackToZero() {
        assertEquals(0, ResumeToJobScoreSupport.sanitize(null));
        assertEquals(0, ResumeToJobScoreSupport.sanitize(-4));
        assertEquals(0, ResumeToJobScoreSupport.sanitize(150));
        assertEquals(42, ResumeToJobScoreSupport.sanitize(42));
    }

    @Test
    void requireValid_rejectsOutOfRange() {
        assertThrows(JobValidationException.class, () -> ResumeToJobScoreSupport.requireValid(null));
        assertThrows(JobValidationException.class, () -> ResumeToJobScoreSupport.requireValid(101));
        ResumeToJobScoreSupport.requireValid(0);
        ResumeToJobScoreSupport.requireValid(100);
    }
}
