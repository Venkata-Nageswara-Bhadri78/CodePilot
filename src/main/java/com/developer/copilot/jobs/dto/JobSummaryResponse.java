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

    @Schema(example = "2026-01-15T10:30:00")
    private LocalDateTime createdAt;
}
