package com.developer.copilot.jobs.util;

import com.developer.copilot.jobs.exception.JobValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobQuerySupportTest {

    @Test
    void validatePaging_rejectsOversizedPage() {
        JobValidationException ex = assertThrows(JobValidationException.class,
                () -> JobQuerySupport.validatePaging(0, 50000));
        assertEquals("size must be between 1 and " + JobLimits.MAX_PAGE_SIZE + ".", ex.getMessage());
    }

    @Test
    void validatePaging_rejectsZeroSize() {
        assertThrows(JobValidationException.class, () -> JobQuerySupport.validatePaging(0, 0));
    }

    @Test
    void validatePaging_rejectsNegativePage() {
        assertThrows(JobValidationException.class, () -> JobQuerySupport.validatePaging(-1, 10));
    }

    @Test
    void validatePaging_acceptsDefaults() {
        JobQuerySupport.validatePaging(0, 10);
    }

    @Test
    void prepareSearch_blank_returnsNull() {
        assertNull(JobQuerySupport.prepareSearch(null));
        assertNull(JobQuerySupport.prepareSearch("   "));
    }

    @Test
    void prepareSearch_escapesLikeWildcards() {
        assertEquals("100\\%", JobQuerySupport.prepareSearch(" 100% "));
        assertEquals("\\_x", JobQuerySupport.prepareSearch("_x"));
        assertEquals("a\\\\b", JobQuerySupport.prepareSearch("a\\b"));
    }

    @Test
    void prepareSearch_tooLong_rejected() {
        assertThrows(JobValidationException.class,
                () -> JobQuerySupport.prepareSearch("x".repeat(JobLimits.MAX_SEARCH_LENGTH + 1)));
    }
}
