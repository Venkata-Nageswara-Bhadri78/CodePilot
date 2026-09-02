package com.developer.copilot.jobs.util;

import com.developer.copilot.jobs.exception.JobValidationException;

/**
 * Validates list-query parameters and prepares search text for JPQL {@code LIKE}.
 * Bind parameters already stop SQL injection; this class stops {@code %} / {@code _}
 * wildcard abuse and oversized pages.
 */
public final class JobQuerySupport {

    private JobQuerySupport() {
    }

    public static void validatePaging(int page, int size) {
        if (page < 0 || page > JobLimits.MAX_PAGE_INDEX) {
            throw new JobValidationException(
                    "page must be between 0 and " + JobLimits.MAX_PAGE_INDEX + ".");
        }
        if (size < 1 || size > JobLimits.MAX_PAGE_SIZE) {
            throw new JobValidationException(
                    "size must be between 1 and " + JobLimits.MAX_PAGE_SIZE + ".");
        }
    }

    public static void validateSearchLength(String search) {
        if (search != null && search.trim().length() > JobLimits.MAX_SEARCH_LENGTH) {
            throw new JobValidationException(
                    "search cannot exceed " + JobLimits.MAX_SEARCH_LENGTH + " characters.");
        }
    }

    /**
     * @return {@code null} when search should be skipped; otherwise the trimmed,
     *         LIKE-escaped value to bind into {@code searchJobsByUserId}
     */
    public static String prepareSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String trimmed = search.trim();
        validateSearchLength(trimmed);
        return escapeLike(trimmed);
    }

    public static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
