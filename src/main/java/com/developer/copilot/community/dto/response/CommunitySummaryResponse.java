package com.developer.copilot.community.dto.response;

import java.time.LocalDateTime;

import com.developer.copilot.community.dto.CommunityMembershipView;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Shared community header. Personal job notes, resumes, and user credentials are not included.")
public class CommunitySummaryResponse {

    @Schema(example = "10")
    private Long id;

    @Schema(description = "Generated name. It cannot be changed.", example = "Backend Engineer at Northwind")
    private String communityName;

    @Schema(example = "Backend Engineer")
    private String title;

    @Schema(example = "Northwind")
    private String company;

    @Schema(example = "Bengaluru")
    private String location;

    @Schema(description = "Shared description stored on the community.")
    private String description;

    @Schema(example = "2026-10-16T13:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Whether the signed-in user is an active member. Matching a URL does not make them a member.")
    private CommunityMembershipView membership;

    @Schema(description = "Active members.", example = "1")
    private long activeMemberCount;
}
