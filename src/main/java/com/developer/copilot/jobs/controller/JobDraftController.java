package com.developer.copilot.jobs.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobDraftUpdateRequest;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.service.JobDraftService;
import com.developer.copilot.jobs.util.JobDraftSortSupport;
import com.developer.copilot.jobs.util.JobLimits;
import com.developer.copilot.jobs.util.JobQuerySupport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/job-drafts")
@RequiredArgsConstructor
@Tag(name = "Job Drafts", description = "Incomplete jobs kept when an automatic save cannot be stored on jobs. "
        + "Authorize with a web JWT. A missing or foreign id is 404. "
        + "POST /{id}/save creates the job first and deletes the draft only after that save succeeds.")
@SecurityRequirement(name = "Bearer Authentication")
public class JobDraftController {

    private final JobDraftService jobDraftService;

    @Operation(summary = "List job drafts",
            description = "Page of drafts owned by the current user. Sort fields: createdAt, updatedAt, title, "
                    + "company, sourceUrl, resumeToJobScore.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobDraftResponse>>> listDrafts(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, default 10, max " + JobLimits.MAX_PAGE_SIZE, example = "10")
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        JobQuerySupport.validatePaging(page, size);
        Sort sort = JobDraftSortSupport.resolveSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, size, sort);
        ApiResponse<Page<JobDraftResponse>> response = ApiResponse.<Page<JobDraftResponse>>builder()
                .success(true)
                .message("Job drafts retrieved successfully.")
                .data(jobDraftService.list(pageable))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get a job draft")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobDraftResponse>> getDraft(@PathVariable Long id) {
        ApiResponse<JobDraftResponse> response = ApiResponse.<JobDraftResponse>builder()
                .success(true)
                .message("Job draft retrieved successfully.")
                .data(jobDraftService.get(id))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Replace draft content",
            description = "Every content field is optional. Null or blank clears it. "
                    + "Omitted JSON properties are null and clear the stored value. "
                    + "Longer values are clipped to the jobs field limits. "
                    + "failureCategory and failureReason are left unchanged.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<JobDraftResponse>> updateDraft(
            @PathVariable Long id,
            @Valid @RequestBody JobDraftUpdateRequest request) {
        ApiResponse<JobDraftResponse> response = ApiResponse.<JobDraftResponse>builder()
                .success(true)
                .message("Job draft updated successfully.")
                .data(jobDraftService.update(id, request))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Save a draft as a job",
            description = "Validates the draft with the same rules as POST /api/v1/jobs. "
                    + "The draft is deleted only after the job is saved. "
                    + "A failed save leaves the draft in place. "
                    + "409 means this user already saved that URL.")
    @PostMapping("/{id}/save")
    public ResponseEntity<ApiResponse<JobResponse>> saveDraft(@PathVariable Long id) {
        ApiResponse<JobResponse> response = ApiResponse.<JobResponse>builder()
                .success(true)
                .message("Job created successfully.")
                .data(jobDraftService.saveAsJob(id))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Delete a job draft")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDraft(@PathVariable Long id) {
        jobDraftService.delete(id);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Job draft deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(response);
    }
}
