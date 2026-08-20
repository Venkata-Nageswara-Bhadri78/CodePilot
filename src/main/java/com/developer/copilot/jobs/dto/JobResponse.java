package com.developer.copilot.jobs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Full job posting details returned by the API")
public class JobResponse {

    @Schema(description = "Unique job identifier", example = "42")
    private Long id;

    @Schema(description = "Canonicalized source URL", example = "https://linkedin.com/jobs/view/1234")
    private String sourceUrl;

    private String originalDescription;

    private String description;

    @Schema(description = "Job title", example = "Software Engineer")
    private String title;

    @Schema(description = "Company name", example = "Acme Corp")
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

    @Schema(description = "Required skills", example = "[\"Java\", \"Spring Boot\"]")
    private List<String> skills;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
