package com.developer.copilot.community.dto.response;

import com.developer.copilot.community.dto.CommunityMatchOutcome;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Whether the posting URLs already belong to a community. A match does not join the user.")
public class CommunityMatchResponse {

    @Schema(example = "EXISTING_COMMUNITY")
    private CommunityMatchOutcome outcome;

    @Schema(example = "A community already exists for this posting.")
    private String message;

    @Schema(description = "Present when outcome is EXISTING_COMMUNITY.")
    private CommunitySummaryResponse community;
}
