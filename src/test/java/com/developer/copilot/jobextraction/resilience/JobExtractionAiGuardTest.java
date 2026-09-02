package com.developer.copilot.jobextraction.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.jobextraction.exception.JobExtractionAiUnavailableException;

class JobExtractionAiGuardTest {

    @Test
    void threeAiFailuresOpenCircuit() {
        JobExtractionAiGuard guard = new JobExtractionAiGuard();
        AiServiceException failure = new AiServiceException("down");

        for (int i = 0; i < 3; i++) {
            assertThrows(AiServiceException.class, () -> guard.call(() -> {
                throw failure;
            }));
        }

        JobExtractionAiUnavailableException open = assertThrows(
                JobExtractionAiUnavailableException.class,
                () -> guard.call(() -> "should-not-run"));
        assertEquals("The AI service is temporarily unavailable. Please try again shortly.", open.getMessage());
    }

    @Test
    void successResetsFailureCount() {
        JobExtractionAiGuard guard = new JobExtractionAiGuard();
        AiServiceException failure = new AiServiceException("down");

        assertThrows(AiServiceException.class, () -> guard.call(() -> {
            throw failure;
        }));
        assertEquals("ok", guard.call(() -> "ok"));
        assertThrows(AiServiceException.class, () -> guard.call(() -> {
            throw failure;
        }));
        assertThrows(AiServiceException.class, () -> guard.call(() -> {
            throw failure;
        }));
        assertEquals("still-open-not-yet", guard.call(() -> "still-open-not-yet"));
    }
}
