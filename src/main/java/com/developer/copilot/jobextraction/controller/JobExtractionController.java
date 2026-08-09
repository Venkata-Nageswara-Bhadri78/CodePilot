package com.developer.copilot.jobextraction.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.service.JobExtractionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for the "Extract Job Info" feature: normalizes/validates the pasted job
 * URL, rejects duplicates early, and returns AI-parsed structured job fields for the user to
 * review before saving. The actual save happens via the existing {@code jobs} module's
 * {@code POST /api/v1/jobs} endpoint - this controller never writes to the database.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/job-extraction")
@RequiredArgsConstructor
@Validated
@Tag(name = "Job Content Extraction", description = "AI-assisted extraction of structured job fields from a pasted job posting")
@SecurityRequirement(name = "Bearer Authentication")
public class JobExtractionController {

    private final JobExtractionService jobExtractionService;

    @PostMapping("/parse")
    @Operation(summary = "Extract structured job info from a pasted job posting",
            description = "Normalizes the job URL, rejects it if already added by this user, sends the pasted "
                    + "text to the AI model for zero-hallucination structured extraction, and returns the "
                    + "result for review. Nothing is persisted by this endpoint - call POST /api/v1/jobs with "
                    + "the (optionally edited) result to save the record.")
    public ResponseEntity<ApiResponse<JobExtractionResultResponse>> parseJobInfo(
            @Valid @RequestBody JobExtractionRequest request) {
        log.info("Incoming job extraction request for URL: {}", request.getSourceUrl());

        JobExtractionResultResponse result = jobExtractionService.extractJobInfo(request);

        ApiResponse<JobExtractionResultResponse> response = ApiResponse.<JobExtractionResultResponse>builder()
                .success(true)
                .message("Job information extracted successfully. Review and edit before saving.")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
