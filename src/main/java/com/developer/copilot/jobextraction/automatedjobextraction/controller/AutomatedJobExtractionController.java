package com.developer.copilot.jobextraction.automatedjobextraction.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobextraction.automatedjobextraction.dto.request.AutomatedJobExtractionRequest;
import com.developer.copilot.jobextraction.automatedjobextraction.service.AutomatedJobExtractionService;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;

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

@Slf4j
@RestController
@RequestMapping("/api/v1/automated-job-extraction")
@RequiredArgsConstructor
@Tag(
        name = "Automated Job Extraction",
        description = "Preview step that fetches a job posting URL, extracts only that job's content, "
                + "and reuses POST /api/v1/job-extraction/parse behavior to return the application's "
                + "fixed JSON. Authorize with a JWT from POST /api/v1/auth/login (enabled, email-verified). "
                + "Edit the returned data, then save with POST /api/v1/jobs. Parse does not write rows. "
                + "409 means this user already saved that URL. Try-it-out can take up to ~60s.")
@SecurityRequirement(name = "Bearer Authentication")
public class AutomatedJobExtractionController {

    private final AutomatedJobExtractionService automatedJobExtractionService;

    @PostMapping("/parse")
    @Operation(
            summary = "Extract structured job info from a job posting URL",
            description = "Normalizes the job URL (absolute http/https only; tracking query params stripped), "
                    + "blocks private/internal targets, fetches the page, identifies the specific job, "
                    + "strips unrelated chrome, and sends the extracted text through the existing manual "
                    + "job-extraction feature. Returns clipped fields that fit POST /api/v1/jobs. "
                    + "Nothing is persisted. Unusable job URLs return 400 with message INVALID JOB URL.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Preview built. requiresManualReview may be true — still success, not an error."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation, malformed JSON, URL is not absolute http/https, or the URL is not "
                            + "a usable job posting (INVALID JOB URL).",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "invalidFormat",
                                            value = "{\"success\":false,\"message\":\"Job URL must be a valid absolute http or https link.\",\"data\":null,\"timestamp\":\"2026-01-15T10:30:00\"}"),
                                    @ExampleObject(name = "invalidJob",
                                            value = "{\"success\":false,\"message\":\"INVALID JOB URL\",\"data\":null,\"timestamp\":\"2026-01-15T10:30:00\"}")
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing/invalid JWT, or disabled account (filter returns Unauthorized.)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Email not verified (service path).",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Please verify your email before using this feature.\",\"data\":null,\"timestamp\":\"2026-01-15T10:30:00\"}"))),
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
                    description = "The job page could not be retrieved, or the AI provider failed.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Fetch or AI circuit open / bulkhead full — retry shortly",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<JobExtractionResultResponse>> parseJobUrl(
            @Valid @RequestBody AutomatedJobExtractionRequest request) {
        log.info("Incoming automated job extraction request, sourceUrl length: {}",
                request.getSourceUrl() != null ? request.getSourceUrl().length() : 0);

        JobExtractionResultResponse result = automatedJobExtractionService.extractFromUrl(request);

        ApiResponse<JobExtractionResultResponse> response = ApiResponse.<JobExtractionResultResponse>builder()
                .success(true)
                .message("Job information extracted successfully. Review and edit before saving.")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
