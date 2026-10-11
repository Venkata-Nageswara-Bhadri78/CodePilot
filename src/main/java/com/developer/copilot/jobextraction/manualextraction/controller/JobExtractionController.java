package com.developer.copilot.jobextraction.manualextraction.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobextraction.manualextraction.dto.request.JobExtractionRequest;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.manualextraction.service.JobExtractionService;
import com.developer.copilot.jobextraction.manualextraction.util.ExtractionAuto;
import com.developer.copilot.jobextraction.persistence.ExtractionResponses;
import com.developer.copilot.jobextraction.persistence.JobAutoPersistResponse;
import com.developer.copilot.jobextraction.persistence.JobExtractionAutoPersistenceService;

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
 * review before saving when {@code isAuto} is false. When {@code isAuto} is true or omitted,
 * the structured job is saved immediately, or stored as a draft if that save fails.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/job-extraction")
@RequiredArgsConstructor
@Tag(
        name = "Job Content Extraction",
        description = "Preview step before saving a job. Authorize with a JWT from POST /api/v1/auth/login "
                + "(enabled, email-verified account). POST /parse with the URL + pasted page text, edit the "
                + "returned data, then save with POST /api/v1/jobs when isAuto is false. "
                + "Omit isAuto or send true to save immediately; a failed save is stored as a draft "
                + "and data.persistedAs is DRAFT. Drop requiresManualReview on a manual save; use "
                + "data.sourceUrl (canonical) and data.originalDescription (the paste). "
                + "409 means this user already saved that URL. Try-it-out can take up to ~60s while the model runs.")
@SecurityRequirement(name = "Bearer Authentication")
public class JobExtractionController {

    private final JobExtractionService jobExtractionService;
    private final JobExtractionAutoPersistenceService jobExtractionAutoPersistenceService;

    @PostMapping("/parse")
    @Operation(
            summary = "Extract structured job info from a pasted job posting",
            description = "Normalizes the job URL (absolute http/https only; tracking query params stripped), "
                    + "rejects it if already added by this user, sends the pasted text to the AI model, and "
                    + "returns clipped fields that fit POST /api/v1/jobs @Size limits when isAuto is false. "
                    + "isAuto true or omitted saves the job, or a draft if required fields are missing. "
                    + "URL format is enforced in code, not by @Pattern — javascript: passes Bean Validation "
                    + "and fails with 400 from UrlNormalizationUtil. "
                    + "Field map: data.sourceUrl → JobRequest.sourceUrl (canonical); "
                    + "data.originalDescription → originalDescription; description/title/company/location/"
                    + "employmentType/workMode/experience/salary/education/department/industry/sourcePlatform/"
                    + "skills share names. data.resume and data.resumeToJobScore go on JobRequest as well. "
                    + "Omit requiresManualReview, notes, and jobStatus on save (jobs defaults those). "
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
                    description = "Missing/invalid JWT, or disabled account (filter returns Unauthorized.)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Email not verified (service path). JWT filter still 401s unverified accounts.",
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
                    description = "AI provider error, timeout, or unparseable structured output",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "AI circuit open or bulkhead full — retry shortly",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<?> parseJobInfo(
            @Valid @RequestBody JobExtractionRequest request) {
        log.info("Incoming job extraction request, sourceUrl length: {}, isAuto: {}",
                request.getSourceUrl() != null ? request.getSourceUrl().length() : 0,
                request.getIsAuto());

        if (!ExtractionAuto.enabled(request.getIsAuto())) {
            JobExtractionResultResponse result = jobExtractionService.extractJobInfo(request);
            return ExtractionResponses.preview(result);
        }

        JobAutoPersistResponse outcome = jobExtractionAutoPersistenceService.persistManual(request);
        return ExtractionResponses.persisted(outcome);
    }
}
