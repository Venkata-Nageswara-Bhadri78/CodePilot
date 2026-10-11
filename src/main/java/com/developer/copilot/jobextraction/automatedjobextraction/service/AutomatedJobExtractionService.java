package com.developer.copilot.jobextraction.automatedjobextraction.service;

import com.developer.copilot.jobextraction.automatedjobextraction.dto.request.AutomatedJobExtractionRequest;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;

public interface AutomatedJobExtractionService {

    JobExtractionResultResponse extractFromUrl(AutomatedJobExtractionRequest request);

    /**
     * Same fetch and AI parse as {@link #extractFromUrl}, but an AI failure is wrapped in
     * {@code RecoverableAutomatedExtractionException} so the caller still has the page text.
     */
    JobExtractionResultResponse extractForAutoSave(AutomatedJobExtractionRequest request);
}
