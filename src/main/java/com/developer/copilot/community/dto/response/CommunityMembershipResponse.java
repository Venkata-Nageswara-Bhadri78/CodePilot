package com.developer.copilot.community.dto.response;

import java.time.LocalDateTime;

import com.developer.copilot.community.dto.CommunityMembershipOutcome;
import com.developer.copilot.community.dto.CommunityMembershipView;
import com.developer.copilot.community.entity.MembershipStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "The signed-in user's membership in one community. Other people are not included.")
public class CommunityMembershipResponse {

    private CommunityMembershipOutcome outcome;

    @Schema(example = "You joined the community.")
    private String message;

    @Schema(example = "10")
    private Long communityId;

    @Schema(description = "MEMBER only while the membership is active. LEFT and never-joined are both NOT_A_MEMBER.")
    private CommunityMembershipView membership;

    @Schema(description = "ACTIVE, LEFT, or null when this user has never joined.", example = "ACTIVE")
    private MembershipStatus membershipStatus;

    @Schema(description = "Start of the current active period. Unchanged by leave. Set again on rejoin.",
            example = "2026-10-16T13:00:00")
    private LocalDateTime joinedAt;

    @Schema(description = "Set when the user leaves. Cleared on rejoin. A repeated leave keeps the first value.",
            example = "2026-10-16T18:00:00")
    private LocalDateTime leftAt;

    @Schema(description = "True when this user is the recorded creator. Leaving does not change that, and it does not close the community.")
    private boolean creator;

    @Schema(description = "Active members after this operation.", example = "2")
    private long activeMemberCount;
}
