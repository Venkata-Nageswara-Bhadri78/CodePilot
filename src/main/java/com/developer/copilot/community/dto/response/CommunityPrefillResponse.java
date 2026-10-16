package com.developer.copilot.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Permitted fields from the signed-in user's saved job, plus whether a community already exists for its posting URL.")
public class CommunityPrefillResponse {

    @Schema(example = "12")
    private Long jobId;

    @Schema(example = "Backend Engineer")
    private String title;

    @Schema(example = "Northwind")
    private String company;

    @Schema(example = "Bengaluru")
    private String location;

    @Schema(description = "Job description, or the original pasted description when the cleaned one is empty.")
    private String description;

    @Schema(example = "https://jobs.example.com/openings/backend-engineer")
    private String sourceUrl;

    private CommunityMatchResponse match;
}
