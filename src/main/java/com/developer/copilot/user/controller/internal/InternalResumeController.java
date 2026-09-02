package com.developer.copilot.user.controller.internal;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.user.dto.parsing.ResumeParsedDataResponse;
import com.developer.copilot.user.service.ResumeParsingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Internal service-to-service endpoints for parsed resume information.
 * <p>
 * Not part of the public user-facing API. Callers must present both the user's
 * bearer token and the internal service key header; the resume is always resolved
 * from the authenticated user's own resumes, so ownership cannot be spoofed.
 */
@Tag(
        name = "Internal - Resume Parsing",
        description = "Service-to-service access to parsed resume information. Requires the internal service key header."
)
@RestController
@RequestMapping("/api/v1/internal/resumes")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "Bearer Authentication")
@SecurityRequirement(name = "InternalApiKey")
public class InternalResumeController {

    private final ResumeParsingService resumeParsingService;

    @Operation(
            summary = "Get parsed data for the high-priority resume",
            description = "Returns COMPLETED parsed data. PENDING (background parse still running) "
                    + "and FAILED return 422. Cache misses with no PENDING row parse on demand "
                    + "(timeout applies). Payload is PII; not for the SPA."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Parsed resume information"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing/invalid JWT or internal service key", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No profile or no high-priority resume", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "PENDING or FAILED parse", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @GetMapping("/parsed")
    public ResponseEntity<ApiResponse<ResumeParsedDataResponse>> getParsedHighPriorityResume() {
        return ok(resumeParsingService.getParsedResume(null));
    }

    @Operation(
            summary = "Get parsed data for a specific resume",
            description = "The resume must belong to the authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Parsed resume information"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing/invalid JWT or internal service key", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Resume not found for this user", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "PENDING or FAILED parse", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @GetMapping("/{resumeId}/parsed")
    public ResponseEntity<ApiResponse<ResumeParsedDataResponse>> getParsedResume(
            @PathVariable Long resumeId) {

        return ok(resumeParsingService.getParsedResume(resumeId));
    }

    private ResponseEntity<ApiResponse<ResumeParsedDataResponse>> ok(ResumeParsedDataResponse data) {
        return ResponseEntity.ok(
                ApiResponse.<ResumeParsedDataResponse>builder()
                        .success(true)
                        .message("Parsed resume information fetched successfully.")
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }
}
