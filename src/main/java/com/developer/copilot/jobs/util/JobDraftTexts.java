package com.developer.copilot.jobs.util;

import java.util.Locale;

/**
 * Clips untrusted job text before it is stored on a draft or submitted as a job.
 * Whole-field {@code javascript:} and {@code data:} values are dropped. Markup is left
 * as text and must be rendered escaped by clients.
 */
public final class JobDraftTexts {

    public static final int MAX_FAILURE_REASON = 1000;

    private JobDraftTexts() {
    }

    public static String clip(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = blankUnsafeUri(stripControls(value));
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength);
    }

    public static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public static Integer validScoreOrNull(Integer score) {
        return ResumeToJobScoreSupport.isValid(score) ? score : null;
    }

    private static String blankUnsafeUri(String value) {
        String trimmed = value.stripLeading();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:")) {
            return "";
        }
        return value;
    }

    private static String stripControls(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c >= 32) {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }
}
