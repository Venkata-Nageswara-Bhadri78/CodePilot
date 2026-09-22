package com.developer.copilot.jobs.dto;

import com.developer.copilot.jobs.entity.JobStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "List-row view of a saved job. Descriptions and sourceUrl are omitted; skills are included.")
public class JobSummaryResponse {

    @Schema(description = "Unique job identifier", example = "42")
    private Long id;

    @Schema(example = "Software Engineer")
    private String title;

    @Schema(example = "Acme Corp")
    private String company;

    @Schema(example = "Bengaluru, India")
    private String location;

    @Schema(example = "Full Time")
    private String employmentType;

    @Schema(example = "Hybrid")
    private String workMode;

    @Schema(example = "2-4 years")
    private String experience;

    @Schema(description = "Salary as free text (not numeric; do not sort by this field)", example = "15-20 LPA")
    private String salary;

    @Schema(example = "LinkedIn")
    private String sourcePlatform;

    @ArraySchema(arraySchema = @Schema(description = "Required skills"), schema = @Schema(example = "Java"))
    private List<String> skills;

    @Schema(description = "Id of the resume bound to this job", example = "12")
    private Long resume;

    @Schema(description = "AI match score of the bound resume against this job, 0–100", example = "78")
    private Integer resumeToJobScore;

    @Schema(description = "User notes. Empty string when none have been added.")
    private String notes;

    @Schema(description = "Manual application status", example = "APPLIED")
    private JobStatus jobStatus;

    @Schema(description = "User-entered status label when jobStatus is CUSTOM")
    private String customStatus;

    @Schema(example = "2026-01-15T10:30:00")
    private LocalDateTime createdAt;
}
