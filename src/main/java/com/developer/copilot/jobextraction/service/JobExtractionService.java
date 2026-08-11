package com.developer.copilot.jobextraction.service;

import com.developer.copilot.jobextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;

/**
 * Orchestrates the "Extract Job Info" flow: URL normalization/validation, duplicate
 * pre-check against the current user's existing job records, and AI-assisted parsing of the
 * pasted posting into strict structured fields. Purely stateless - no data is persisted here.
 */
public interface JobExtractionService {

    /**
     * Parses a pasted job posting for the current user, without saving anything.
     *
     * @param request the raw source URL and pasted job posting text
     * @return the extracted fields, ready for user review before the client calls
     *         {@code POST /api/v1/jobs} to actually save the record
     */
    JobExtractionResultResponse extractJobInfo(JobExtractionRequest request);
}
