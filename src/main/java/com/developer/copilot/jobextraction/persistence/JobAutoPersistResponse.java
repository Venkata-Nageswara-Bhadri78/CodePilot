package com.developer.copilot.jobextraction.persistence;

import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Result of an automatic job save. Exactly one of job or draft is present.")
public class JobAutoPersistResponse {

    @Schema(description = "JOB when the jobs table accepted the record. DRAFT when it was stored for later review.")
    private final PersistedAs persistedAs;

    private final JobResponse job;

    private final JobDraftResponse draft;

    @Schema(description = "Present only for DRAFT. Safe to show to the user.")
    private final String failureReason;

    public static JobAutoPersistResponse saved(JobResponse job) {
        return JobAutoPersistResponse.builder()
                .persistedAs(PersistedAs.JOB)
                .job(job)
                .build();
    }

    public static JobAutoPersistResponse drafted(JobDraftResponse draft) {
        return JobAutoPersistResponse.builder()
                .persistedAs(PersistedAs.DRAFT)
                .draft(draft)
                .failureReason(draft == null ? null : draft.getFailureReason())
                .build();
    }
}
