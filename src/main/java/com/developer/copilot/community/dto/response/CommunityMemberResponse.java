package com.developer.copilot.community.dto.response;

import java.time.LocalDateTime;

import com.developer.copilot.community.entity.MembershipStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "A community member. Email and other private profile fields are not included.")
public class CommunityMemberResponse {

    @Schema(description = "users.id. Used to match a message sender. It is not an email.", example = "7")
    private Long userId;

    @Schema(example = "ada")
    private String username;

    @Schema(example = "Ada Lovelace")
    private String fullName;

    @Schema(example = "ACTIVE")
    private MembershipStatus membershipStatus;

    @Schema(description = "Start of the current active membership.", example = "2026-10-16T13:00:00")
    private LocalDateTime joinedAt;
}
