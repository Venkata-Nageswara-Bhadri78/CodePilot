package com.developer.copilot.jobextraction.automatedjobextraction.service;

import com.developer.copilot.jobextraction.automatedjobextraction.dto.request.AutomatedJobExtractionRequest;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;

public interface AutomatedJobExtractionService {

    JobExtractionResultResponse extractFromUrl(AutomatedJobExtractionRequest request);
}
