package com.developer.copilot.community.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Communities the signed-in user currently belongs to. This is not a public directory.")
public class CommunityListResponse {

    private List<CommunitySummaryResponse> communities;
}
