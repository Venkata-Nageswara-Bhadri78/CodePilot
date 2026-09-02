package com.developer.copilot.jobextraction.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.service.JobExtractionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(
        name = "Job Content Extraction",
        description = "Preview step before saving a job. Authorize with a JWT from POST /api/v1/auth/login "
                + "(enabled, email-verified account). POST /parse with the URL + pasted page text, edit the "
                + "returned data, then save with POST /api/v1/jobs. Drop requiresManualReview on save; use "
                + "data.sourceUrl (canonical) and data.originalDescription (the paste). Parse does not write "
                + "rows. 200 is a preview — requiresManualReview true means fill title/company before save. "
                + "409 means this user already saved that URL. Try-it-out can take up to ~60s while the model runs.")
@SecurityRequirement(name = "Bearer Authentication")
public class JobExtractionController {

    private final JobExtractionService jobExtractionService;

    @PostMapping("/parse")
    @Operation(
            summary = "Extract structured job info from a pasted job posting",
            description = "Normalizes the job URL (absolute http/https only; tracking query params stripped), "
                    + "rejects it if already added by this user, sends the pasted text to the AI model, and "
                    + "returns clipped fields that fit POST /api/v1/jobs @Size limits. Nothing is persisted. "
                    + "URL format is enforced in code, not by @Pattern — javascript: passes Bean Validation "
                    + "and fails with 400 from UrlNormalizationUtil. "
                    + "Field map: data.sourceUrl → JobRequest.sourceUrl (canonical); "
                    + "data.originalDescription → originalDescription; description/title/company/location/"
                    + "employmentType/workMode/experience/salary/education/department/industry/sourcePlatform/"
                    + "skills share names. Omit requiresManualReview on save. "
                    + "requiresManualReview is computed here (blank or truncated title/company), not by the model.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Preview built. requiresManualReview may be true — still success, not an error."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation, malformed JSON, or URL is not an absolute http/https link",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Job URL must be a valid absolute http or https link.\",\"data\":null,\"timestamp\":\"2026-01-15T10:30:00\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing/invalid JWT, or unverified/disabled account (filter returns Unauthorized.)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "This user already saved this canonical URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"This post was already added to your records.\",\"data\":null,\"timestamp\":\"2026-01-15T10:30:00\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many parse requests (per IP and per user). Retry-After is seconds.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Too many requests. Please try again later.\",\"data\":null,\"timestamp\":\"2026-01-15T10:30:00\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "AI provider error, timeout, or unparseable structured output",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<JobExtractionResultResponse>> parseJobInfo(
            @Valid @RequestBody JobExtractionRequest request) {
        log.info("Incoming job extraction request, sourceUrl length: {}",
                request.getSourceUrl() != null ? request.getSourceUrl().length() : 0);

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
