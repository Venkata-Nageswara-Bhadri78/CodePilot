package com.developer.copilot.jobs.dto;

import java.time.LocalDateTime;

import com.developer.copilot.jobs.entity.JobDraftFailureCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A partial job that could not be saved on the jobs table. "
        + "Notes and job status are not part of a draft. sourceUrlHash is not exposed.")
public class JobDraftResponse {

    @Schema(example = "15")
    private Long id;

    @Schema(description = "Canonical source URL when one was available.")
    private String sourceUrl;

    private String originalDescription;
    private String description;
    private String title;
    private String company;
    private String location;
    private String employmentType;
    private String workMode;
    private String experience;
    private String salary;
    private String education;
    private String department;
    private String industry;
    private String sourcePlatform;
    private String skills;

    @Schema(description = "Resume id stored with the draft. Ownership is checked again when the draft is saved as a job.")
    private Long resume;

    @Schema(description = "Resume-to-job score from extraction, 0–100, or null when it could not be scored.")
    private Integer resumeToJobScore;

    private JobDraftFailureCategory failureCategory;

    @Schema(description = "Safe explanation of why the job was not saved. No stack traces or resume text.")
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
