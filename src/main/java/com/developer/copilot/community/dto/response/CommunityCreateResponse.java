package com.developer.copilot.community.dto.response;

import com.developer.copilot.community.dto.CommunityMatchOutcome;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Result of creating a community, or of finding that the posting already has one.")
public class CommunityCreateResponse {

    @Schema(example = "CREATED")
    private CommunityMatchOutcome outcome;

    @Schema(example = "Community created.")
    private String message;

    @Schema(description = "False when refinement was not requested or the model could not be used. The stored description is still the text that was saved.")
    private boolean descriptionRefined;

    private CommunitySummaryResponse community;
}
