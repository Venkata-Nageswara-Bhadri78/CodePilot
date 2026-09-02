package com.developer.copilot.jobs.controller;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.JobSummaryResponse;
import com.developer.copilot.jobs.dto.request.*;
import com.developer.copilot.jobs.service.JobService;
import com.developer.copilot.jobs.util.JobLimits;
import com.developer.copilot.jobs.util.JobQuerySupport;
import com.developer.copilot.jobs.util.JobSortSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(
        name = "Jobs",
        description = "Personal job-posting notebook for the signed-in user. "
                + "Obtain a JWT from POST /api/v1/auth/login, then use the Authorize button. "
                + "PUT = full form; omitting skills clears them. PATCH = dirty fields only. "
                + "PATCH /{id}/skills with [] clears skills. Optional field routes accept empty string to clear. "
                + "Someone else's job id looks like 404.")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class JobController {

    private static final String JOB_ID_DESCRIPTION =
            "Job id owned by the current user; foreign ids look like 404";

    private final JobService jobService;

    @Operation(summary = "Create a new job",
            description = "Normalizes the source URL (http/https only, tracking params stripped) "
                    + "and rejects duplicates for the current user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Job created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or invalid URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate source URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"This post was already added to your records.\",\"data\":null,\"timestamp\":\"2026-01-15T10:30:00\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(@Valid @RequestBody JobRequest request) {
        JobResponse createdJob = jobService.createJob(request);

        ApiResponse<JobResponse> response = ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job created successfully.")
                .data(createdJob)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List jobs",
            description = "Paginated list for the current user. Search is a case-insensitive contains-match "
                    + "on title, company, location, industry, and sourcePlatform. LIKE wildcards in search are escaped.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Jobs retrieved successfully. "
                    + "data is a Spring Page: content, totalElements, totalPages, number, size, sort."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid sort, page, size, or search length",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobSummaryResponse>>> getAllJobs(
            @Parameter(description = "Contains-match on title, company, location, industry, sourcePlatform. Max 100 chars. % and _ are treated as literals.")
            @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, default 10, max " + JobLimits.MAX_PAGE_SIZE, example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field. Allowed: createdAt, updatedAt, title, company, location, "
                    + "employmentType, workMode, experience, department, education, industry, sourcePlatform, sourceUrl. "
                    + "salary is not sortable (it is free text).")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc (any non-asc value is desc)", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDir) {

        JobQuerySupport.validatePaging(page, size);
        JobQuerySupport.validateSearchLength(search);

        Sort sort = JobSortSupport.resolveSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<JobSummaryResponse> jobs = jobService.getAllJobs(search, pageable);

        ApiResponse<Page<JobSummaryResponse>> response = ApiResponse.<Page<JobSummaryResponse>>builder()
                .success(true)
                .message("Jobs retrieved successfully.")
                .data(jobs)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get job by ID", description = "Returns full job details for a job owned by the current user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Job details retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Job not found with id: 42\",\"data\":null,\"timestamp\":\"2026-01-15T10:30:00\"}")))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> getJobById(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id) {
        JobResponse job = jobService.getJobById(id);

        ApiResponse<JobResponse> response = ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job details retrieved successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Replace a job",
            description = "Fully replaces an existing job. Recalculates the URL hash when the source URL changes. "
                    + "Omitting skills clears the list.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Job updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or invalid URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate source URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody JobRequest request) {

        JobResponse updatedJob = jobService.updateJob(id, request);

        ApiResponse<JobResponse> response = ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job updated successfully.")
                .data(updatedJob)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Partially update a job",
            description = "Updates only the fields provided in the request body. Blank title, company, or originalDescription is rejected.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Job updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error, blank mandatory field, or invalid URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate source URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> patchJob(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody JobPatchRequest request) {

        JobResponse patchedJob = jobService.patchJob(id, request);

        ApiResponse<JobResponse> response = ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job updated successfully.")
                .data(patchedJob)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a job", description = "Permanently deletes a job and its associated skills.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Job deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id) {
        jobService.deleteJob(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Job deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update job location")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/location")
    public ResponseEntity<ApiResponse<JobResponse>> updateLocation(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateLocationRequest request) {

        JobResponse job = jobService.updateLocation(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job location updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update job title")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/title")
    public ResponseEntity<ApiResponse<JobResponse>> updateTitle(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateTitleRequest request) {

        JobResponse job = jobService.updateTitle(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job title updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update job company")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/company")
    public ResponseEntity<ApiResponse<JobResponse>> updateCompany(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateCompanyRequest request) {

        JobResponse job = jobService.updateCompany(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job company updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update employment type")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/employment-type")
    public ResponseEntity<ApiResponse<JobResponse>> updateEmploymentType(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateEmploymentTypeRequest request) {

        JobResponse job = jobService.updateEmploymentType(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job employment type updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update work mode")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/work-mode")
    public ResponseEntity<ApiResponse<JobResponse>> updateWorkMode(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateWorkModeRequest request) {

        JobResponse job = jobService.updateWorkMode(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job work mode updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update experience requirement")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/experience")
    public ResponseEntity<ApiResponse<JobResponse>> updateExperience(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateExperienceRequest request) {

        JobResponse job = jobService.updateExperience(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job experience updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update salary")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/salary")
    public ResponseEntity<ApiResponse<JobResponse>> updateSalary(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateSalaryRequest request) {

        JobResponse job = jobService.updateSalary(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job salary updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update education requirement")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/education")
    public ResponseEntity<ApiResponse<JobResponse>> updateEducation(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateEducationRequest request) {

        JobResponse job = jobService.updateEducation(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job education updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update department")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/department")
    public ResponseEntity<ApiResponse<JobResponse>> updateDepartment(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentRequest request) {

        JobResponse job = jobService.updateDepartment(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job department updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update industry")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/industry")
    public ResponseEntity<ApiResponse<JobResponse>> updateIndustry(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateIndustryRequest request) {

        JobResponse job = jobService.updateIndustry(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job industry updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update source platform")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/source-platform")
    public ResponseEntity<ApiResponse<JobResponse>> updateSourcePlatform(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateSourcePlatformRequest request) {

        JobResponse job = jobService.updateSourcePlatform(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job source platform updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update source URL", description = "Normalizes the URL (http/https only) and enforces duplicate detection.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or invalid URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate source URL",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/source-url")
    public ResponseEntity<ApiResponse<JobResponse>> updateSourceUrl(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateSourceUrlRequest request) {

        JobResponse job = jobService.updateSourceUrl(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job source URL updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Replace job skills", description = "Replaces the full skills list. Send an empty array to clear.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/skills")
    public ResponseEntity<ApiResponse<JobResponse>> updateSkills(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateSkillsRequest request) {

        JobResponse job = jobService.updateSkills(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job skills updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update cleaned description")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/description")
    public ResponseEntity<ApiResponse<JobResponse>> updateDescription(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateDescriptionRequest request) {

        JobResponse job = jobService.updateDescription(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job description updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update original description")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the caller",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PatchMapping("/{id}/original-description")
    public ResponseEntity<ApiResponse<JobResponse>> updateOriginalDescription(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42") @PathVariable Long id,
            @Valid @RequestBody UpdateOriginalDescriptionRequest request) {

        JobResponse job = jobService.updateOriginalDescription(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job original description updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
