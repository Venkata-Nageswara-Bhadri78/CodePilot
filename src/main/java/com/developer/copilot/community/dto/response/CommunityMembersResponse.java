package com.developer.copilot.community.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Active members of one community. People who have left are not listed.")
public class CommunityMembersResponse {

    @Schema(example = "10")
    private Long communityId;

    @Schema(description = "Same number of people as members. Counted from active memberships.", example = "2")
    private long activeMemberCount;

    private List<CommunityMemberResponse> members;
}
