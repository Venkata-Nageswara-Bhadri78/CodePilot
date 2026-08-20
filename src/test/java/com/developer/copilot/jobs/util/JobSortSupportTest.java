package com.developer.copilot.jobs.util;

import com.developer.copilot.jobs.exception.JobValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobSortSupportTest {

    @Test
    void resolveSort_validField_returnsAscendingSort() {
        Sort sort = JobSortSupport.resolveSort("title", "asc");

        assertEquals("title", sort.iterator().next().getProperty());
        assertEquals(Sort.Direction.ASC, sort.iterator().next().getDirection());
    }

    @Test
    void resolveSort_invalidField_throwsValidationException() {
        assertThrows(JobValidationException.class,
                () -> JobSortSupport.resolveSort("hackedField", "desc"));
    }
}
