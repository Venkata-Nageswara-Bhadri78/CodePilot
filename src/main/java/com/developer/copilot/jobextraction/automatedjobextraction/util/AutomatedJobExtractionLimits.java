package com.developer.copilot.jobextraction.automatedjobextraction.util;

import com.developer.copilot.jobextraction.manualextraction.util.JobExtractionLimits;

/**
 * Caps for automated URL extraction so fetched pages cannot exhaust memory and the
 * downstream manual parser still receives text within {@link JobExtractionLimits}.
 */
public final class AutomatedJobExtractionLimits {

    public static final int MAX_URL_LENGTH = JobExtractionLimits.MAX_URL_LENGTH;
    public static final int MAX_EXTRACTED_TEXT_LENGTH = JobExtractionLimits.MAX_DESCRIPTION_LENGTH;
    public static final int MIN_JOB_TEXT_LENGTH = 120;
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 1_500_000;
    public static final int DEFAULT_MAX_REDIRECTS = 3;
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5_000;
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 15_000;
    public static final int FETCH_BULKHEAD = 8;
    public static final int FETCH_FAILURES_TO_OPEN = 3;
    public static final long FETCH_CIRCUIT_OPEN_MS = 30_000L;

    private AutomatedJobExtractionLimits() {
    }
}
