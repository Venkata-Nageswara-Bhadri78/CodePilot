package com.developer.copilot.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityDescriptionResponse {

    @Schema(description = "Refined text, or the submitted text when refinement could not be used.")
    private String description;

    @Schema(example = "true")
    private boolean refined;
}
