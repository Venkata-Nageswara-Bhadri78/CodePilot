package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobExtractionUnavailableException;
import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobPageFetchException;

class JobPageFetchGuardTest {

    @Test
    void threeFetchFailures_fourthIsUnavailable() {
        JobPageFetchGuard guard = new JobPageFetchGuard();
        for (int i = 0; i < 3; i++) {
            assertThrows(AutomatedJobPageFetchException.class,
                    () -> guard.call(() -> {
                        throw new AutomatedJobPageFetchException();
                    }));
        }
        assertThrows(AutomatedJobExtractionUnavailableException.class,
                () -> guard.call(() -> "ok"));
    }

    @Test
    void successResetsCircuit() throws Exception {
        JobPageFetchGuard guard = new JobPageFetchGuard();
        assertThrows(AutomatedJobPageFetchException.class,
                () -> guard.call(() -> {
                    throw new AutomatedJobPageFetchException();
                }));
        assertEqualsSafe(guard.call(() -> "ok"));
        assertThrows(AutomatedJobPageFetchException.class,
                () -> guard.call(() -> {
                    throw new AutomatedJobPageFetchException();
                }));
    }

    private static void assertEqualsSafe(String value) {
        org.junit.jupiter.api.Assertions.assertEquals("ok", value);
    }
}
