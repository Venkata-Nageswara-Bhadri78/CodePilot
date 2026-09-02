package com.developer.copilot.jobs.util;

import com.developer.copilot.jobs.exception.JobValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSortSupportTest {

    @Test
    void resolveSort_validField_returnsAscendingSort() {
        Sort sort = JobSortSupport.resolveSort("title", "asc");

        assertEquals("title", sort.iterator().next().getProperty());
        assertEquals(Sort.Direction.ASC, sort.iterator().next().getDirection());
    }

    @Test
    void resolveSort_nonAsc_isDescending() {
        Sort sort = JobSortSupport.resolveSort("company", "DESC");

        assertEquals(Sort.Direction.DESC, sort.iterator().next().getDirection());
    }

    @Test
    void resolveSort_invalidField_throwsValidationException() {
        assertThrows(JobValidationException.class,
                () -> JobSortSupport.resolveSort("hackedField", "desc"));
    }

    @Test
    void resolveSort_salary_isNotAllowed() {
        assertThrows(JobValidationException.class,
                () -> JobSortSupport.resolveSort("salary", "asc"));
        assertFalse(JobSortSupport.allowedSortFields().contains("salary"));
    }

    @Test
    void resolveSort_nestedUserAndHash_areNotAllowed() {
        assertThrows(JobValidationException.class, () -> JobSortSupport.resolveSort("user.password", "asc"));
        assertThrows(JobValidationException.class, () -> JobSortSupport.resolveSort("user.email", "desc"));
        assertThrows(JobValidationException.class, () -> JobSortSupport.resolveSort("sourceUrlHash", "asc"));
        assertThrows(JobValidationException.class, () -> JobSortSupport.resolveSort("id; drop", "asc"));
        assertFalse(JobSortSupport.allowedSortFields().contains("sourceUrlHash"));
        assertFalse(JobSortSupport.allowedSortFields().contains("user"));
    }

    @Test
    void allowedSortFields_includeCoreColumns() {
        assertTrue(JobSortSupport.allowedSortFields().contains("createdAt"));
        assertTrue(JobSortSupport.allowedSortFields().contains("title"));
        assertTrue(JobSortSupport.allowedSortFields().contains("sourceUrl"));
    }
}
