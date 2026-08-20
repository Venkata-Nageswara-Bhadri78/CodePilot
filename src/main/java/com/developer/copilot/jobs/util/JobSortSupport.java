package com.developer.copilot.jobs.util;

import com.developer.copilot.jobs.exception.JobValidationException;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class JobSortSupport {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "title",
            "company",
            "location",
            "employmentType",
            "workMode",
            "experience",
            "salary",
            "department",
            "education",
            "industry",
            "sourcePlatform",
            "sourceUrl"
    );

    private JobSortSupport() {
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
