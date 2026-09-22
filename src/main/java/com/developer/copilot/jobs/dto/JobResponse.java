package com.developer.copilot.jobs.dto;

import com.developer.copilot.jobs.entity.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Full job posting details returned by the API. sourceUrl is the stored canonical URL, "
        + "not the raw paste. sourceUrlHash is not exposed.")
public class JobResponse {

    @Schema(description = "Unique job identifier", example = "42")
    private Long id;

    @Schema(description = "Canonicalized source URL after tracking params, www, and host casing are normalized",
            example = "https://linkedin.com/jobs/view/1234")
    private String sourceUrl;

    @Schema(description = "Raw job posting text as submitted")
    private String originalDescription;

    @Schema(description = "Cleaned or extracted job description")
    private String description;

    @Schema(description = "Job title", example = "Software Engineer")
    private String title;

    @Schema(description = "Company name", example = "Acme Corp")
    private String company;

    @Schema(example = "Bengaluru, India")
    private String location;

    @Schema(example = "Full Time")
    private String employmentType;

    @Schema(example = "Hybrid")
    private String workMode;

    @Schema(example = "2-4 years")
    private String experience;

    @Schema(example = "15-20 LPA")
    private String salary;

    private String education;

    private String department;

    private String industry;

    @Schema(example = "LinkedIn")
    private String sourcePlatform;

    @Schema(description = "Required skills as a comma-separated string. Empty string when none are set.",
            example = "Java, Spring Boot, MySQL, AWS")
    private String skills;

    @Schema(description = "Id of the resume bound to this job. Null when none was available.", example = "12")
    private Long resume;

    @Schema(description = "AI match score of the bound resume against this job, 0–100.", example = "78")
    private Integer resumeToJobScore;

    @Schema(description = "User notes. Empty string when none have been added.")
    private String notes;

    @Schema(description = "Manual application status. Defaults to APPLIED on create.", example = "APPLIED")
    private JobStatus jobStatus;

    @Schema(description = "User-entered status label. Present only when jobStatus is CUSTOM.")
    private String customStatus;

    @Schema(example = "2026-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(example = "2026-01-16T09:00:00")
    private LocalDateTime updatedAt;
}
