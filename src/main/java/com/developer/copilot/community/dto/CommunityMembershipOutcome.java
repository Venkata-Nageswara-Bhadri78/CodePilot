package com.developer.copilot.community.dto;

import com.developer.copilot.community.service.CommunityMessages;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Membership result. JOINED, ALREADY_MEMBER, and REJOINED come from join. "
        + "LEFT, ALREADY_LEFT, and NOT_A_MEMBER come from leave. "
        + "A status read uses MEMBER, ALREADY_LEFT, or NOT_A_MEMBER.")
public enum CommunityMembershipOutcome {
    MEMBER(CommunityMessages.MEMBER),
    NOT_A_MEMBER(CommunityMessages.NOT_A_MEMBER),
    LEFT(CommunityMessages.LEFT),
    JOINED(CommunityMessages.JOINED),
    ALREADY_MEMBER(CommunityMessages.ALREADY_MEMBER),
    REJOINED(CommunityMessages.REJOINED),
    ALREADY_LEFT(CommunityMessages.ALREADY_LEFT);

    private final String message;

    CommunityMembershipOutcome(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
