package com.developer.copilot.jobs.controller;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobs.dto.JobPatchRequest;
import com.developer.copilot.jobs.dto.JobRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.dto.JobSummaryResponse;
import com.developer.copilot.jobs.dto.request.*;
import com.developer.copilot.jobs.service.JobService;
import com.developer.copilot.jobs.util.JobSortSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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

@Tag(name = "Jobs", description = "Operations for managing saved job postings")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class JobController {

    private final JobService jobService;

    @Operation(summary = "Create a new job", description = "Normalizes the source URL and prevents duplicate job postings for the current user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Job created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or duplicate job URL", content = @Content)
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

    @Operation(summary = "List jobs", description = "Returns a paginated list of the current user's jobs. Supports optional text search and sorting.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Jobs retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid sort parameter", content = @Content)
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobSummaryResponse>>> getAllJobs(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

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
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> getJobById(@PathVariable Long id) {
        JobResponse job = jobService.getJobById(id);

        ApiResponse<JobResponse> response = ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job details retrieved successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Replace a job", description = "Fully replaces an existing job. Recalculates the URL hash when the source URL changes.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable Long id,
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

    @Operation(summary = "Partially update a job", description = "Updates only the fields provided in the request body.")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> patchJob(
            @PathVariable Long id,
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
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Job deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update job location")
    @PatchMapping("/{id}/location")
    public ResponseEntity<ApiResponse<JobResponse>> updateLocation(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/title")
    public ResponseEntity<ApiResponse<JobResponse>> updateTitle(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/company")
    public ResponseEntity<ApiResponse<JobResponse>> updateCompany(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/employment-type")
    public ResponseEntity<ApiResponse<JobResponse>> updateEmploymentType(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/work-mode")
    public ResponseEntity<ApiResponse<JobResponse>> updateWorkMode(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/experience")
    public ResponseEntity<ApiResponse<JobResponse>> updateExperience(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/salary")
    public ResponseEntity<ApiResponse<JobResponse>> updateSalary(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/education")
    public ResponseEntity<ApiResponse<JobResponse>> updateEducation(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/department")
    public ResponseEntity<ApiResponse<JobResponse>> updateDepartment(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/industry")
    public ResponseEntity<ApiResponse<JobResponse>> updateIndustry(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/source-platform")
    public ResponseEntity<ApiResponse<JobResponse>> updateSourcePlatform(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSourcePlatformRequest request) {

        JobResponse job = jobService.updateSourcePlatform(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job source platform updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Update source URL", description = "Normalizes the URL and enforces duplicate detection.")
    @PatchMapping("/{id}/source-url")
    public ResponseEntity<ApiResponse<JobResponse>> updateSourceUrl(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSourceUrlRequest request) {

        JobResponse job = jobService.updateSourceUrl(id, request);

        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job source URL updated successfully.")
                .data(job)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Operation(summary = "Replace job skills")
    @PatchMapping("/{id}/skills")
    public ResponseEntity<ApiResponse<JobResponse>> updateSkills(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/description")
    public ResponseEntity<ApiResponse<JobResponse>> updateDescription(
            @PathVariable Long id,
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
    @PatchMapping("/{id}/original-description")
    public ResponseEntity<ApiResponse<JobResponse>> updateOriginalDescription(
            @PathVariable Long id,
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
