package com.developer.copilot.jobs.dto;

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

    @ArraySchema(arraySchema = @Schema(description = "Required skills"), schema = @Schema(example = "Java"))
    private List<String> skills;

    @Schema(example = "2026-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(example = "2026-01-16T09:00:00")
    private LocalDateTime updatedAt;
}
