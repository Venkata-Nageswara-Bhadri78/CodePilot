package com.developer.copilot.jobs.util;

import java.util.Set;

import org.springframework.data.domain.Sort;

import com.developer.copilot.jobs.exception.JobValidationException;

public final class JobDraftSortSupport {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "title",
            "company",
            "sourceUrl",
            "resumeToJobScore"
    );

    private JobDraftSortSupport() {
    }

    public static Sort resolveSort(String sortBy, String sortDir) {
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new JobValidationException(
                    "Invalid sort field '" + sortBy + "'. Allowed values: " + ALLOWED_SORT_FIELDS);
        }
        return sortDir != null && sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
    }
}
