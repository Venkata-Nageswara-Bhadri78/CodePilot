package com.developer.copilot.jobextraction.automatedjobextraction.integration;

import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.manualextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.service.JobExtractionService;

import lombok.RequiredArgsConstructor;

/**
 * Reuses the existing manual job-extraction feature to turn extracted page text into
 * the application's fixed JSON. Does not reimplement AI parsing.
 */
@Component
@RequiredArgsConstructor
public class ManualJobExtractionGateway {

    private final JobExtractionService jobExtractionService;

    public JobExtractionResultResponse parseExtractedContent(String canonicalUrl, String extractedText) {
        JobExtractionRequest request = JobExtractionRequest.builder()
                .sourceUrl(canonicalUrl)
                .rawJobText(extractedText)
                .build();
        return jobExtractionService.extractJobInfo(request);
    }
}
