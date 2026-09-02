package com.developer.copilot.jobextraction.util;

import com.developer.copilot.jobs.util.JobLimits;

/**
 * Caps for AI-extracted fields so {@code POST /api/v1/jobs} Bean Validation will accept
 * the preview without a surprise 400. Lengths match {@code JobRequest}; description
 * reuses {@link JobLimits#MAX_DESCRIPTION_LENGTH}.
 */
public final class JobExtractionLimits {

    public static final int MAX_URL_LENGTH = 2000;
    public static final int MAX_DESCRIPTION_LENGTH = JobLimits.MAX_DESCRIPTION_LENGTH;
    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_COMPANY_LENGTH = 255;
    public static final int MAX_LOCATION_LENGTH = 255;
    public static final int MAX_EMPLOYMENT_TYPE_LENGTH = 100;
    public static final int MAX_WORK_MODE_LENGTH = 50;
    public static final int MAX_EXPERIENCE_LENGTH = 100;
    public static final int MAX_SALARY_LENGTH = 100;
    public static final int MAX_EDUCATION_LENGTH = 255;
    public static final int MAX_DEPARTMENT_LENGTH = 100;
    public static final int MAX_INDUSTRY_LENGTH = 100;
    public static final int MAX_SOURCE_PLATFORM_LENGTH = 50;
    public static final int MAX_SKILL_LENGTH = 255;
    public static final int MAX_SKILL_COUNT = 50;

    private JobExtractionLimits() {
    }
}
