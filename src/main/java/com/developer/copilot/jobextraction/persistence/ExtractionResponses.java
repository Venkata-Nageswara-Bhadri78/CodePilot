package com.developer.copilot.jobextraction.persistence;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobextraction.manualextraction.dto.response.JobExtractionResultResponse;

public final class ExtractionResponses {

    public static final String PREVIEW_MESSAGE =
            "Job information extracted successfully. Review and edit before saving.";
    public static final String SAVED_MESSAGE = "Job created successfully.";
    public static final String DRAFT_MESSAGE = "Job could not be saved. Stored as a draft.";

    private ExtractionResponses() {
    }

    public static ResponseEntity<?> preview(
            JobExtractionResultResponse result) {
        return ResponseEntity.ok(ApiResponse.<JobExtractionResultResponse>builder()
                .success(true)
                .message(PREVIEW_MESSAGE)
                .data(result)
                .timestamp(LocalDateTime.now())
                .build());
    }

    public static ResponseEntity<?> persisted(JobAutoPersistResponse outcome) {
        String message = outcome.getPersistedAs() == PersistedAs.JOB ? SAVED_MESSAGE : DRAFT_MESSAGE;
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<JobAutoPersistResponse>builder()
                .success(true)
                .message(message)
                .data(outcome)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
