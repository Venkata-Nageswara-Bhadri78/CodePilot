package com.developer.copilot.jobs.util;

/**
 * Shared numeric limits for jobs HTTP input. Bean-validation annotations and
 * {@link JobQuerySupport} both read these compile-time constants.
 */
public final class JobLimits {

    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_PAGE_INDEX = 10_000;
    public static final int MAX_SEARCH_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 50_000;

    private JobLimits() {
    }
}
