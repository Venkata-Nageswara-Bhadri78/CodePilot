package com.developer.copilot.jobs.util;

import com.developer.copilot.jobs.exception.JobValidationException;

/**
 * resumeToJobScore is an integer in {@code [0, 100]}. Missing or out-of-range AI
 * values fall back to {@link #DEFAULT_SCORE} so a job can still be saved.
 */
public final class ResumeToJobScoreSupport {

    public static final int DEFAULT_SCORE = 0;
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;

    private ResumeToJobScoreSupport() {
    }

    public static boolean isValid(Integer score) {
        return score != null && score >= MIN_SCORE && score <= MAX_SCORE;
    }

    public static int sanitize(Integer score) {
        return isValid(score) ? score : DEFAULT_SCORE;
    }

    public static void requireValid(Integer score) {
        if (!isValid(score)) {
            throw new JobValidationException(
                    "resumeToJobScore must be an integer between "
                            + MIN_SCORE + " and " + MAX_SCORE + ".");
        }
    }
}
